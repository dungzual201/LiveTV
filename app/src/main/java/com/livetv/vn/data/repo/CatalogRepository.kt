package com.livetv.vn.data.repo

import android.content.Context
import com.livetv.vn.data.model.ChannelCatalog
import com.livetv.vn.data.model.PlaylistSource
import com.livetv.vn.data.source.CatalogMerger
import com.livetv.vn.data.source.M3uParser
import com.livetv.vn.data.source.PlaylistSnapshot
import com.livetv.vn.data.store.DiskJsonStore
import com.livetv.vn.search.SearchIndexStore
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class CatalogRepository(
    context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val disk: DiskJsonStore,
    private val sources: SourcesRepository,
    private val searchIndex: SearchIndexStore,
) {
    private val appContext = context.applicationContext
    @Serializable
    data class CachedPlaylist(
        val sourceId: String,
        val fetchedAt: Long,
        val sourceUrl: String = "",
        val entries: List<com.livetv.vn.data.model.M3uEntry> = emptyList(),
    )

    private data class FetchResult(
        val source: PlaylistSource,
        val cache: CachedPlaylist?,
        val error: String?,
    )

    private val parser = M3uParser(json)
    private val serializer = CachedPlaylist.serializer()
    private val catalogSerializer = ChannelCatalog.serializer()
    private val mutex = Mutex()
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _catalog = MutableStateFlow(ChannelCatalog())
    val catalog: StateFlow<ChannelCatalog> = _catalog.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    suspend fun loadFast(): Boolean = mutex.withLock {
        val cached = disk.read(MERGED_CACHE_NAME, catalogSerializer) ?: return@withLock false
        if (cached.channels.isEmpty()) return@withLock false
        _catalog.value = cached
        true
    }

    private fun catalogSignature(catalog: ChannelCatalog): Long {
        var hash = 1125899906842597L
        catalog.channels.forEach { channel ->
            hash = hash * 31 + channel.id.hashCode()
            hash = hash * 31 + channel.name.hashCode()
            channel.streams.forEach { stream ->
                hash = hash * 31 + stream.url.hashCode()
                hash = hash * 31 + stream.sourceId.hashCode()
                hash = hash * 31 + stream.headers.hashCode()
                hash = hash * 31 + stream.qualityScore
                hash = hash * 31 + (stream.fps ?: 0)
            }
        }
        return hash
    }

    private fun publishMerged(merged: ChannelCatalog) {
        val current = _catalog.value
        if (current.channels.isNotEmpty() &&
            current.updatedAt == merged.updatedAt &&
            catalogSignature(current) == catalogSignature(merged)
        ) {
            return
        }
        _catalog.value = merged
        searchIndex.scheduleWrite(merged.channels)
        cacheScope.launch {
            runCatching { disk.write(MERGED_CACHE_NAME, catalogSerializer, merged) }
        }
    }

    suspend fun loadCached() {
        mutex.withLock {
            if (_syncing.value) return
            val enabled = sources.enabledPlaylists()
            val loaded = enabled.filterNot(::isAssetSource).mapNotNull { source ->
                val cache = disk.read(cacheName(source.id), serializer) ?: return@mapNotNull null
                PlaylistSnapshot(source = source, entries = cache.entries, fromCache = true)
                    .toPairWith(cache.fetchedAt)
            }
            val local = enabled.filter(::isAssetSource).mapNotNull { source ->
                val entries = runCatching { readAsset(source) }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                PlaylistSnapshot(source = source, entries = entries)
            }
            val snapshots = loaded.map { it.first } + local
            if (snapshots.isEmpty()) {
                if (_catalog.value.channels.isEmpty()) {
                    _catalog.value = ChannelCatalog()
                }
                return
            }
            val fetchedAt = loaded.maxOfOrNull { it.second } ?: 0L
            val merged = withContext(Dispatchers.Default) {
                CatalogMerger.merge(snapshots, fetchedAt)
            }
            publishMerged(merged)
        }
    }

    private fun PlaylistSnapshot.toPairWith(fetchedAt: Long): Pair<PlaylistSnapshot, Long> =
        this to fetchedAt

    private fun isAssetSource(source: PlaylistSource): Boolean =
        source.url.startsWith(ASSET_PREFIX)

    private suspend fun readAsset(source: PlaylistSource): List<com.livetv.vn.data.model.M3uEntry> =
        withContext(Dispatchers.IO) {
            val name = source.url.removePrefix(ASSET_PREFIX)
            appContext.assets.open(name).bufferedReader().use { it.readText() }
                .let { parser.parse(it, source.url) }
        }

    suspend fun refreshIfStale(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        if (_catalog.value.channels.isEmpty() || now - _catalog.value.updatedAt > maxAgeMs) {
            refresh()
        }
    }

    suspend fun refresh() {
        if (_syncing.value) return
        mutex.withLock {
            if (_syncing.value) return
            _syncing.value = true
            try {
                val enabled = sources.enabledPlaylists()
                if (enabled.isEmpty()) {
                    _catalog.value = ChannelCatalog(updatedAt = System.currentTimeMillis())
                    return
                }
                val semaphore = Semaphore(3)
                val results = coroutineScope {
                    enabled.map { source ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit { fetchSource(source) }
                        }
                    }.awaitAll()
                }
                val hasData = results.any { it.cache?.entries?.isNotEmpty() == true }
                if (!hasData) {
                    val errors = results.map {
                        com.livetv.vn.data.model.SourceError(
                            it.source.id,
                            it.source.name,
                            it.error ?: "Không tải được nguồn",
                        )
                    }
                    if (_catalog.value.channels.isEmpty()) {
                        _catalog.value = ChannelCatalog(errors = errors)
                    } else {
                        _catalog.value = _catalog.value.copy(errors = errors)
                    }
                    return
                }
                val snapshots = results.map { result ->
                    PlaylistSnapshot(
                        source = result.source,
                        entries = result.cache?.entries.orEmpty(),
                        fromCache = result.error != null,
                        error = result.error,
                    )
                }
                val networkResults = results.filterNot { isAssetSource(it.source) }
                val anyNetworkFresh = networkResults.any { it.error == null && it.cache != null }
                val newestNetworkCache = networkResults.mapNotNull { it.cache?.fetchedAt }.maxOrNull() ?: 0L
                val updatedAt = when {
                    anyNetworkFresh -> System.currentTimeMillis()
                    networkResults.isEmpty() -> System.currentTimeMillis()
                    else -> newestNetworkCache
                }
                val merged = withContext(Dispatchers.Default) {
                    CatalogMerger.merge(snapshots, updatedAt)
                }
                publishMerged(merged)
            } finally {
                _syncing.value = false
            }
        }
    }

    private suspend fun fetchSource(source: PlaylistSource): FetchResult = withContext(Dispatchers.IO) {
        if (isAssetSource(source)) {
            return@withContext try {
                val entries = readAsset(source)
                if (entries.isEmpty()) throw IOException("Danh sách tích hợp rỗng")
                val cache = CachedPlaylist(
                    sourceId = source.id,
                    fetchedAt = System.currentTimeMillis(),
                    sourceUrl = source.url,
                    entries = entries,
                )
                FetchResult(source, cache, null)
            } catch (error: Exception) {
                FetchResult(source, null, describeError(error))
            }
        }
        var lastError: String? = null
        val urls = (listOf(source.url) + source.fallbackUrls).distinct()
        for (url in urls) {
            try {
                val cache = fetchFromUrl(source, url)
                if (cache.entries.isEmpty()) throw IOException("Danh sách rỗng")
                runCatching { disk.write(cacheName(source.id), serializer, cache) }
                return@withContext FetchResult(source, cache, null)
            } catch (error: Exception) {
                lastError = describeError(error)
            }
        }
        val cached = disk.read(cacheName(source.id), serializer)
        val message = buildString {
            append(lastError ?: "Không tải được nguồn")
            if (cached != null) append(" · đang dùng bản lưu")
        }
        FetchResult(source, cached, message)
    }

    private fun fetchFromUrl(source: PlaylistSource, url: String): CachedPlaylist {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*")
            .header("Cache-Control", "no-cache")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Phản hồi rỗng")
            if (body.contentLength() > MAX_PLAYLIST_BYTES) throw IOException("Playlist quá lớn")
            val content = body.string()
            if (content.length > MAX_PLAYLIST_BYTES) throw IOException("Playlist quá lớn")
            val entries = parser.parse(content, url)
            return CachedPlaylist(
                sourceId = source.id,
                fetchedAt = System.currentTimeMillis(),
                sourceUrl = url,
                entries = entries,
            )
        }
    }

    private fun cacheName(sourceId: String): String = disk.fileName("playlist", sourceId)

    private fun describeError(error: Throwable): String = when (error) {
        is UnknownHostException -> "Không có mạng hoặc không phân giải được tên miền"
        is SocketTimeoutException -> "Hết thời gian kết nối"
        is IOException -> error.message ?: "Lỗi mạng"
        else -> error.message ?: "Lỗi không xác định"
    }

    private companion object {
        const val USER_AGENT = "LiveTV/1.0 (Android TV)"
        const val MAX_PLAYLIST_BYTES = 40 * 1024 * 1024
        const val ASSET_PREFIX = "asset://"
        const val MERGED_CACHE_NAME = "catalog_merged.json"
    }
}
