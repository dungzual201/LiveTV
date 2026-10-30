package com.livetv.vn.data.store

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class StreamHealth(
    val successCount: Int = 0,
    val failCount: Int = 0,
    val lastSuccessAt: Long = 0L,
    val lastFailureAt: Long = 0L,
    val lastLatencyMs: Long = 0L,
)

@Serializable
private data class HealthCache(
    val entries: Map<String, StreamHealth> = emptyMap(),
)

class HealthStore(
    context: Context,
    private val disk: DiskJsonStore,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val saveMutex = Mutex()
    private var loaded = false
    private val entries = HashMap<String, StreamHealth>()

    suspend fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) { if (loaded) return }
        val cache = disk.read(FILE_NAME, HealthCache.serializer()) ?: HealthCache()
        synchronized(lock) {
            entries.clear()
            entries.putAll(cache.entries)
            loaded = true
        }
    }

    fun score(url: String): Double {
        val item = synchronized(lock) { entries[url] } ?: return 0.0
        val now = System.currentTimeMillis()
        val failureRecent = item.lastFailureAt > 0 && now - item.lastFailureAt < SEVEN_DAYS
        val success = minOf(item.successCount, 5) * 2.0
        val failure = if (failureRecent) minOf(item.failCount, 5) * 2.8 else 0.0
        val recentSuccess = if (item.lastSuccessAt > 0 && now - item.lastSuccessAt < THREE_DAYS) 3.0 else 0.0
        val recentFailure = if (failureRecent) -2.5 else 0.0
        val latencyBonus = when {
            item.lastLatencyMs in 1..1500 -> 2.0
            item.lastLatencyMs in 1501..4000 -> 1.0
            item.lastLatencyMs > 10000 -> -1.0
            else -> 0.0
        }
        return success - failure + recentSuccess + recentFailure + latencyBonus
    }

    fun recordSuccess(url: String, latencyMs: Long) {
        synchronized(lock) {
            val old = entries[url] ?: StreamHealth()
            entries[url] = old.copy(
                successCount = minOf(old.successCount + 1, 20),
                failCount = 0,
                lastSuccessAt = System.currentTimeMillis(),
                lastLatencyMs = latencyMs,
            )
        }
        scheduleSave()
    }

    fun recordFailure(url: String) {
        synchronized(lock) {
            val old = entries[url] ?: StreamHealth()
            entries[url] = old.copy(
                failCount = minOf(old.failCount + 1, 20),
                lastFailureAt = System.currentTimeMillis(),
                lastLatencyMs = 0,
            )
        }
        scheduleSave()
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            loaded = true
        }
        scheduleSave()
    }

    private fun scheduleSave() {
        scope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                val snapshot = synchronized(lock) { entries.toMap() }
                runCatching { disk.write(FILE_NAME, HealthCache.serializer(), HealthCache(snapshot)) }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "stream_health.json"
        const val THREE_DAYS = 3L * 24 * 60 * 60 * 1000
        const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
    }
}
