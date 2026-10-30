package com.livetv.vn

import android.content.Context
import com.livetv.vn.data.repo.CatalogRepository
import com.livetv.vn.data.repo.EpgRepository
import com.livetv.vn.data.repo.SourcesRepository
import com.livetv.vn.data.store.DiskJsonStore
import com.livetv.vn.data.store.HealthStore
import com.livetv.vn.data.store.SettingsStore
import com.livetv.vn.search.SearchIndexStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val disk = DiskJsonStore(appContext, json)
    val sources = SourcesRepository(appContext, json)
    val settings = SettingsStore(appContext)
    private val healthScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val health = HealthStore(appContext, disk, healthScope)
    val searchIndex = SearchIndexStore(appContext, json)
    val catalog = CatalogRepository(appContext, http, json, disk, sources, searchIndex)
    val epg = EpgRepository(http, disk, sources)
}
