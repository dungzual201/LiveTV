package com.livetv.vn.data.repo

import com.livetv.vn.data.model.ChannelCatalog
import com.livetv.vn.data.model.EpgSource
import com.livetv.vn.data.model.Program
import com.livetv.vn.data.source.EpgMatcher
import com.livetv.vn.data.source.XmltvParser
import com.livetv.vn.data.store.DiskJsonStore
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

class EpgRepository(
    private val http: OkHttpClient,
    private val disk: DiskJsonStore,
    private val sources: SourcesRepository,
) {
    @Serializable
    data class EpgCache(
        val updatedAt: Long = 0L,
        val providers: List<String> = emptyList(),
        val programs: Map<String, List<Program>> = emptyMap(),
    )

    private data class ProviderResult(
        val source: EpgSource,
        val programs: List<Program>,
        val error: String?,
        val channelsSeen: Int = 0,
        val programsSeen: Int = 0,
    )

    private val serializer = EpgCache.serializer()
    private val mutex = Mutex()
    private val epgHttp = http.newBuilder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _programs = MutableStateFlow<Map<String, List<Program>>>(emptyMap())
    val programs: StateFlow<Map<String, List<Program>>> = _programs.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun loadCached() {
        mutex.withLock {
            if (_syncing.value) return
            val cache = disk.read(FILE_NAME, serializer) ?: return
            val cutoff = System.currentTimeMillis() - HISTORY_MS
            val fresh = withContext(Dispatchers.Default) {
                cache.programs.mapValues { (_, list) ->
                    list.filter { it.endMs >= cutoff }.sortedBy { it.startMs }
                }.filterValues { it.isNotEmpty() }
            }
            _programs.value = fresh
            _lastUpdated.value = cache.updatedAt
        }
    }

    suspend fun refreshIfStale(catalog: ChannelCatalog, maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        if (_programs.value.isEmpty() || now - _lastUpdated.value > maxAgeMs) {
            refresh(catalog)
        }
    }

    suspend fun refresh(catalog: ChannelCatalog) {
        if (_syncing.value) return
        mutex.withLock {
            if (_syncing.value) return
            if (catalog.channels.isEmpty()) return
            _syncing.value = true
            try {
                val enabled = sources.enabledEpgs()
                if (enabled.isEmpty()) {
                    _lastError.value = "Chưa bật nguồn EPG nào"
                    return
                }
                val matcher = EpgMatcher(catalog.channels)
                val now = System.currentTimeMillis()
                val windowStart = now - HISTORY_MS
                val windowEnd = now + FUTURE_MS
                val combined = HashMap<String, MutableList<Program>>()
                val errors = ArrayList<String>()
                val previous = _programs.value
                val providerIds = enabled.map { it.id }
                var successCount = 0
                var usedCount = 0
                var partialSaved = false

                enabled.sortedByDescending { it.priority }.forEach { source ->
                    val result = parseProvider(source, matcher, windowStart, windowEnd, now)
                    if (result.error != null) {
                        errors += "${source.name}: ${result.error}"
                    } else {
                        successCount++
                        usedCount += result.programs.size
                        val partial = withContext(Dispatchers.Default) {
                            result.programs.forEach { addWithoutOverlap(combined, it) }
                            combined.mapValues { (_, list) -> list.toList() }
                        }
                        _programs.value = partial
                        if (!partialSaved) {
                            partialSaved = true
                            val stamp = System.currentTimeMillis()
                            _lastUpdated.value = stamp
                            persistCache(partial, stamp, providerIds)
                        }
                    }
                }

                if (successCount == 0) {
                    _lastError.value = errors.joinToString(" · ").ifBlank { "Không tải được EPG" }
                    return@withLock
                }
                if (usedCount == 0) {
                    _lastError.value = "Đã tải EPG nhưng không khớp được kênh nào"
                    return@withLock
                }

                val cutoff = System.currentTimeMillis() - HISTORY_MS
                val finalPrograms = withContext(Dispatchers.Default) {
                    previous.forEach { (channelId, oldPrograms) ->
                        if (combined[channelId] == null) {
                            val kept = oldPrograms.filter { it.endMs >= cutoff }.sortedBy { it.startMs }
                            if (kept.isNotEmpty()) combined[channelId] = kept.toMutableList()
                        }
                    }
                    combined.mapValues { (_, list) -> list.toList() }
                }
                val finalUpdatedAt = System.currentTimeMillis()
                _programs.value = finalPrograms
                _lastUpdated.value = finalUpdatedAt
                _lastError.value = errors.joinToString(" · ").takeIf { it.isNotBlank() }
                persistCache(finalPrograms, finalUpdatedAt, providerIds)
            } finally {
                _syncing.value = false
            }
        }
    }

    private suspend fun parseProvider(
        source: EpgSource,
        matcher: EpgMatcher,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
    ): ProviderResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        val urls = (listOf(source.url) + source.fallbackUrls).distinct()
        for (url in urls) {
            try {
                val buffer = ArrayList<Program>(2048)
                val stats = epgHttp.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/xml, application/gzip, text/xml, */*")
                        .header("Cache-Control", "no-cache")
                        .build(),
                ).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Phản hồi rỗng")
                    if (body.contentLength() > MAX_EPG_BYTES) throw IOException("EPG quá lớn")
                    body.byteStream().use { stream ->
                        val parser = XmltvParser(
                            windowStartMs = windowStart,
                            windowEndMs = windowEnd,
                            resolver = { providerId, names ->
                                matcher.resolve(providerId, names, source.defaultCountry)
                            },
                            onProgram = { program ->
                                buffer += program
                            },
                        )
                        parser.parse(stream)
                    }
                }
                if (buffer.isEmpty()) {
                    throw IOException("Không khớp được chương trình nào (${stats.channelsSeen} kênh EPG)")
                }
                return@withContext ProviderResult(
                    source = source,
                    programs = buffer,
                    error = null,
                    channelsSeen = stats.channelsSeen,
                    programsSeen = stats.programsSeen,
                )
            } catch (error: Exception) {
                lastError = describeError(error)
            }
        }
        ProviderResult(source, emptyList(), lastError ?: "Không tải được EPG")
    }

    private suspend fun persistCache(
        programs: Map<String, List<Program>>,
        updatedAt: Long,
        providerIds: List<String>,
    ) {
        runCatching {
            disk.write(
                FILE_NAME,
                serializer,
                EpgCache(
                    updatedAt = updatedAt,
                    providers = providerIds,
                    programs = programs,
                ),
            )
        }
    }

    private fun addWithoutOverlap(target: MutableMap<String, MutableList<Program>>, program: Program) {
        val list = target.getOrPut(program.channelId) { ArrayList() }
        val overlaps = list.any { existing ->
            program.startMs < existing.endMs && program.endMs > existing.startMs
        }
        if (overlaps) return
        val index = list.indexOfFirst { it.startMs > program.startMs }
        if (index < 0) list.add(program) else list.add(index, program)
    }

    private fun describeError(error: Throwable): String = when (error) {
        is UnknownHostException -> "Không có mạng hoặc không phân giải được tên miền"
        is SocketTimeoutException -> "Hết thời gian kết nối"
        is IOException -> error.message ?: "Lỗi mạng"
        else -> error.message ?: "Lỗi không xác định"
    }

    private companion object {
        const val FILE_NAME = "epg_cache.json"
        const val USER_AGENT = "LiveTV/1.0 (Android TV)"
        const val MAX_EPG_BYTES = 250L * 1024 * 1024
        const val HISTORY_MS = 12L * 60 * 60 * 1000
        const val FUTURE_MS = 5L * 24 * 60 * 60 * 1000
    }
}
