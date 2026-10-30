package com.livetv.vn.search

import android.content.Context
import androidx.core.util.AtomicFile
import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.util.ChannelSearch
import com.livetv.vn.data.util.ChannelText
import com.livetv.vn.tif.TifChannelSync
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchEntry(
    val id: String,
    val name: String,
    val number: Int,
    val country: String? = null,
    val tvgId: String? = null,
    val logo: String? = null,
    val quality: String? = null,
    val nameKey: String = "",
    val idKey: String = "",
)

private data class IndexedEntry(
    val entry: SearchEntry,
    val nameKey: String,
    val idKey: String,
)

@Serializable
private data class SearchIndexData(
    val updatedAt: Long = 0L,
    val entries: List<SearchEntry> = emptyList(),
)

class SearchIndexStore(
    context: Context,
    private val json: Json,
) {
    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "livetv_data/search_index.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val lock = Any()

    @Volatile
    private var cachedEntries: List<SearchEntry> = emptyList()

    @Volatile
    private var cachedIndexed: List<IndexedEntry> = emptyList()

    @Volatile
    private var cachedStamp: Long = -1L

    private val queryAliases = mapOf(
        "tvone" to "vn.vtv1",
        "tv1" to "vn.vtv1",
        "vtvone" to "vn.vtv1",
    )

    fun scheduleWrite(channels: List<Channel>) {
        if (channels.isEmpty()) return
        scope.launch {
            writeMutex.withLock {
                val entries = channels.map { channel ->
                    SearchEntry(
                        id = channel.id,
                        name = channel.name,
                        number = channel.number,
                        country = channel.country,
                        tvgId = channel.tvgId,
                        logo = channel.logo,
                        quality = channel.bestQualityLabel,
                        nameKey = ChannelText.nameKey(channel.name),
                        idKey = ChannelText.idKey(channel.tvgId),
                    )
                }
                val data = SearchIndexData(System.currentTimeMillis(), entries)
                withContext(Dispatchers.IO) {
                    runCatching {
                        file.parentFile?.mkdirs()
                        val atomic = AtomicFile(file)
                        val stream = atomic.startWrite()
                        try {
                            stream.write(json.encodeToString(SearchIndexData.serializer(), data).encodeToByteArray())
                            atomic.finishWrite(stream)
                        } catch (error: Exception) {
                            atomic.failWrite(stream)
                            throw error
                        }
                    }
                }
                synchronized(lock) {
                    cachedEntries = entries
                    cachedIndexed = indexedOf(entries)
                    cachedStamp = file.lastModified()
                }
                runCatching { TifChannelSync.sync(appContext, entries) }
            }
        }
    }

    fun allEntries(): List<SearchEntry> = readAll()

    fun search(query: String, limit: Int = 12): List<SearchEntry> {
        readAll()
        val indexed = cachedIndexed
        if (indexed.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 30)
        val aliasId = queryAliases[ChannelText.nameKey(query)]
        if (aliasId != null) {
            indexed.firstOrNull { it.entry.id == aliasId }?.let { return listOf(it.entry) }
        }
        val prepared = ChannelSearch.prepareQuery(query) ?: return emptyList()
        return indexed.mapNotNull { item ->
            val score = ChannelSearch.score(prepared, item.nameKey, item.idKey, item.entry.number)
            if (score == 0) null else item to score
        }
            .sortedWith(
                compareByDescending<Pair<IndexedEntry, Int>> { it.second }
                    .thenBy { it.first.entry.number },
            )
            .take(safeLimit)
            .map { it.first.entry }
    }

    private fun indexedOf(entries: List<SearchEntry>): List<IndexedEntry> = entries.map { entry ->
        IndexedEntry(
            entry = entry,
            nameKey = entry.nameKey.ifBlank { ChannelText.nameKey(entry.name) },
            idKey = entry.idKey.ifBlank { ChannelText.idKey(entry.tvgId) },
        )
    }

    private fun readAll(): List<SearchEntry> {
        val stamp = file.lastModified()
        synchronized(lock) {
            if (stamp == cachedStamp) return cachedEntries
            if (!file.exists()) {
                cachedEntries = emptyList()
                cachedIndexed = emptyList()
                cachedStamp = stamp
                return emptyList()
            }
            val parsed = runCatching {
                val bytes = AtomicFile(file).readFully()
                json.decodeFromString(SearchIndexData.serializer(), bytes.decodeToString())
            }.getOrNull()
            val entries = parsed?.entries.orEmpty()
            cachedEntries = entries
            cachedIndexed = indexedOf(entries)
            cachedStamp = stamp
            return entries
        }
    }
}
