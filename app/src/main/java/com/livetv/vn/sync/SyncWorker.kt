package com.livetv.vn.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.livetv.vn.LiveTvApp

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as? LiveTvApp)?.graph ?: return Result.success()
        return try {
            graph.health.ensureLoaded()
            graph.catalog.loadCached()
            graph.catalog.refreshIfStale(CATALOG_STALE_MS)
            graph.epg.loadCached()
            graph.epg.refreshIfStale(graph.catalog.catalog.value, EPG_STALE_MS)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val CATALOG_STALE_MS = 6L * 60 * 60 * 1000
        const val EPG_STALE_MS = 12L * 60 * 60 * 1000
    }
}
