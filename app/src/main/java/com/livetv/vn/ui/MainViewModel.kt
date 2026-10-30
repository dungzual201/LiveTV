package com.livetv.vn.ui

import android.app.Application
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.livetv.vn.LiveTvApp
import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.model.EpgUi
import com.livetv.vn.data.model.PlaylistUi
import com.livetv.vn.data.model.QualityMode
import com.livetv.vn.data.util.ChannelSearch
import com.livetv.vn.playback.LivePlayer
import com.livetv.vn.playback.PlaybackSnapshot
import com.livetv.vn.voice.VoiceIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = (application as LiveTvApp).graph

    val catalog = graph.catalog.catalog
    val programs = graph.epg.programs
    val favorites = graph.settings.favorites
    val qualityMode = graph.settings.qualityMode
    val sources = graph.sources.state
    val catalogSyncing = graph.catalog.syncing
    val epgSyncing = graph.epg.syncing
    val epgUpdatedAt = graph.epg.lastUpdated
    val epgError = graph.epg.lastError

    private val _playback = MutableStateFlow(PlaybackSnapshot())
    val playback: StateFlow<PlaybackSnapshot> = _playback.asStateFlow()

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    private val _epgCacheLoading = MutableStateFlow(true)
    val epgCacheLoading: StateFlow<Boolean> = _epgCacheLoading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val livePlayer = LivePlayer(
        context = application,
        health = graph.health,
        qualityMode = { graph.settings.qualityMode.value },
        scope = viewModelScope,
        onState = { _playback.value = it },
    )

    val player: ExoPlayer get() = livePlayer.player

    private var started = false
    private var pendingChannelId: String? = null
    private var pendingQuery: String? = null
    private var bootstrapJob: Job? = null

    init {
        viewModelScope.launch {
            graph.catalog.catalog.collect { cat ->
                if (cat.channels.isEmpty()) {
                    if (started) {
                        started = false
                        livePlayer.stop()
                    }
                    return@collect
                }
                val currentId = livePlayer.currentChannelId
                if (!started) {
                    val target = resolvePending(cat) ?: cat.byId[graph.settings.lastChannelId] ?: preferredChannel(cat)
                    if (target != null) {
                        started = true
                        tune(target)
                    }
                } else if (pendingChannelId != null || pendingQuery != null) {
                    applyPending(cat)
                } else if (currentId != null && cat.byId[currentId] == null) {
                    preferredChannel(cat)?.let { tune(it) }
                }
            }
        }
        bootstrapJob = viewModelScope.launch { bootstrap() }
    }

    fun handleIntent(intent: Intent) {
        val channelId = VoiceIntents.channelIdFrom(intent)
        val query = VoiceIntents.queryFrom(intent)
        Log.i("LiveTvVoice", "action=${intent.action} data=${intent.data} channel=$channelId query=$query")
        if (channelId.isNullOrBlank() && query.isNullOrBlank()) return
        if (!channelId.isNullOrBlank()) pendingChannelId = channelId
        if (!query.isNullOrBlank()) pendingQuery = query
        viewModelScope.launch {
            val cat = catalog.value
            if (!started) {
                if (cat.channels.isNotEmpty()) {
                    val target = resolvePending(cat) ?: preferredChannel(cat)
                    if (target != null) {
                        started = true
                        tune(target)
                    }
                }
            } else if (cat.channels.isNotEmpty()) {
                applyPending(cat)
            }
        }
    }

    fun zap(delta: Int) {
        val channels = catalog.value.channels
        if (channels.isEmpty()) return
        val currentId = livePlayer.currentChannelId
        val currentIndex = channels.indexOfFirst { it.id == currentId }
        val nextIndex = when {
            currentIndex < 0 -> if (delta >= 0) 0 else channels.lastIndex
            else -> Math.floorMod(currentIndex + delta, channels.size)
        }
        tune(channels[nextIndex])
    }

    fun playChannel(channelId: String) {
        val channel = catalog.value.byId[channelId] ?: return
        if (channel.id != livePlayer.currentChannelId || _playback.value.isError) {
            tune(channel)
        }
    }

    fun retryPlayback() {
        if (!_playback.value.isError) return
        val active = catalog.value.byId[_playback.value.channelId]
        if (active != null) {
            tune(active)
        } else {
            preferredChannel(catalog.value)?.let(::tune)
        }
    }

    fun toggleFavorite(channelId: String) {
        graph.settings.toggleFavorite(channelId)
    }

    fun setQualityMode(mode: QualityMode) {
        graph.settings.setQualityMode(mode)
    }

    fun setPlaylistEnabled(id: String, enabled: Boolean) {
        graph.sources.setPlaylistEnabled(id, enabled)
        refreshCatalogAndEpg()
    }

    fun setEpgEnabled(id: String, enabled: Boolean) {
        graph.sources.setEpgEnabled(id, enabled)
        refreshEpgOnly()
    }

    fun addCustomPlaylist(name: String, url: String, vietnamese: Boolean): Result<Unit> {
        val result = graph.sources.addCustomPlaylist(name, url, vietnamese)
        if (result.isSuccess) refreshCatalogAndEpg()
        return result
    }

    fun addCustomEpg(name: String, url: String, vietnamese: Boolean): Result<Unit> {
        val result = graph.sources.addCustomEpg(name, url, vietnamese)
        if (result.isSuccess) refreshEpgOnly()
        return result
    }

    fun removeCustomPlaylist(item: PlaylistUi) {
        graph.sources.removeCustomPlaylist(item.source.id)
        refreshCatalogAndEpg()
    }

    fun removeCustomEpg(item: EpgUi) {
        graph.sources.removeCustomEpg(item.source.id)
        refreshEpgOnly()
    }

    fun resetSources() {
        graph.sources.resetToDefaults()
        refreshCatalogAndEpg("Đã đặt lại nguồn mặc định")
    }

    fun refreshEverything() {
        refreshCatalogAndEpg("Đã làm mới dữ liệu")
    }

    fun refreshCatalogOnly() {
        refreshCatalogAndEpg("Đã làm mới danh sách kênh")
    }

    fun refreshEpgOnly(message: String? = "Đã làm mới lịch phát sóng") {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val currentCatalog = graph.catalog.catalog.value
                if (currentCatalog.channels.isNotEmpty()) {
                    graph.epg.refresh(currentCatalog)
                }
                message?.let { _message.value = it }
            } catch (error: Exception) {
                _message.value = error.message ?: "Không cập nhật được lịch phát sóng"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun clearTemporaryData() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                graph.disk.clear()
                graph.health.clear()
                graph.catalog.refresh()
                if (graph.catalog.catalog.value.channels.isNotEmpty()) {
                    graph.epg.refresh(graph.catalog.catalog.value)
                }
                _message.value = "Đã xóa dữ liệu tạm và tải lại"
            } catch (error: Exception) {
                _message.value = error.message ?: "Không xóa được dữ liệu"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun onHostStart() = livePlayer.onHostStart()

    fun onHostStop() = livePlayer.onHostStop()

    override fun onCleared() {
        super.onCleared()
        bootstrapJob?.cancel()
        livePlayer.release()
    }

    private suspend fun bootstrap() {
        val fast = try {
            graph.health.ensureLoaded()
            graph.catalog.loadFast()
        } catch (_: Exception) {
            false
        }
        if (fast) _initialized.value = true
        try {
            graph.catalog.loadCached()
        } catch (_: Exception) {
            // Repositories already fall back to cached data internally.
        }
        _initialized.value = true
        try {
            graph.epg.loadCached()
        } catch (_: Exception) {
            // EPG is optional; the live UI can run without it.
        } finally {
            _epgCacheLoading.value = false
        }
        try {
            graph.catalog.refreshIfStale(CATALOG_STALE_MS)
            graph.epg.refreshIfStale(graph.catalog.catalog.value, EPG_STALE_MS)
        } catch (error: Exception) {
            _message.value = error.message ?: "Không cập nhật được dữ liệu"
        }
    }

    private fun refreshCatalogAndEpg(successMessage: String? = null) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                graph.catalog.refresh()
                if (graph.catalog.catalog.value.channels.isNotEmpty()) {
                    graph.epg.refresh(graph.catalog.catalog.value)
                }
                successMessage?.let { _message.value = it }
            } catch (error: Exception) {
                _message.value = error.message ?: "Không làm mới được dữ liệu"
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun tune(channel: Channel) {
        started = true
        graph.settings.lastChannelId = channel.id
        livePlayer.play(channel)
    }

    private suspend fun resolvePending(cat: com.livetv.vn.data.model.ChannelCatalog): Channel? {
        val id = pendingChannelId
        if (!id.isNullOrBlank()) {
            pendingChannelId = null
            val resolvedChannel = cat.byId[id]
                ?: resolveTvChannelId(id)?.let { resolved ->
                    cat.byId[resolved] ?: ChannelSearch.find(cat.channels, resolved)
                }
            val channel = resolvedChannel ?: ChannelSearch.find(cat.channels, id)
            if (channel == null) {
                _message.value = "Không tìm thấy kênh đã yêu cầu"
            }
            return channel
        }
        val query = pendingQuery
        if (!query.isNullOrBlank()) {
            pendingQuery = null
            val channel = ChannelSearch.find(cat.channels, query)
            if (channel == null) {
                _message.value = "Không tìm thấy kênh cho \"$query\""
            }
            return channel
        }
        return null
    }

    private suspend fun resolveTvChannelId(raw: String): String? {
        if (raw.contains('.')) return null
        val rowId = raw.toLongOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val projection = arrayOf(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA)
                getApplication<Application>().contentResolver
                    .query(TvContract.buildChannelUri(rowId), projection, null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }
    }

    private suspend fun applyPending(cat: com.livetv.vn.data.model.ChannelCatalog) {
        resolvePending(cat)?.let(::tune)
    }

    private fun preferredChannel(cat: com.livetv.vn.data.model.ChannelCatalog): Channel? =
        cat.byId[PREFERRED_CHANNEL_ID]
            ?: cat.channels.firstOrNull { it.isVietnamese }
            ?: cat.channels.firstOrNull()

    private companion object {
        const val PREFERRED_CHANNEL_ID = "vn.vtv1"
        const val CATALOG_STALE_MS = 6L * 60 * 60 * 1000
        const val EPG_STALE_MS = 12L * 60 * 60 * 1000
    }
}
