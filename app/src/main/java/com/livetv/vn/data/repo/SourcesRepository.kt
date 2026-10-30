package com.livetv.vn.data.repo

import android.content.Context
import com.livetv.vn.data.model.EpgSource
import com.livetv.vn.data.model.EpgUi
import com.livetv.vn.data.model.PlaylistSource
import com.livetv.vn.data.model.PlaylistUi
import com.livetv.vn.data.model.SourcesConfig
import com.livetv.vn.data.model.SourcesUi
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SourcesRepository(
    private val context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("livetv_sources", Context.MODE_PRIVATE)
    private val lock = Any()

    @Serializable
    private data class StoredSources(
        val playlistOverrides: Map<String, Boolean> = emptyMap(),
        val epgOverrides: Map<String, Boolean> = emptyMap(),
        val customPlaylists: List<PlaylistSource> = emptyList(),
        val customEpgs: List<EpgSource> = emptyList(),
    )

    private val defaults: SourcesConfig = loadDefaults()
    private var stored: StoredSources = loadStored()

    private val _state = MutableStateFlow(buildUi())
    val state: StateFlow<SourcesUi> = _state.asStateFlow()

    fun enabledPlaylists(): List<PlaylistSource> =
        _state.value.playlists.filter { it.enabled }.map { it.source }
            .sortedWith(compareByDescending<PlaylistSource> { it.priority }.thenBy { it.name })

    fun enabledEpgs(): List<EpgSource> =
        _state.value.epgs.filter { it.enabled }.map { it.source }
            .sortedWith(compareByDescending<EpgSource> { it.priority }.thenBy { it.name })

    fun setPlaylistEnabled(id: String, enabled: Boolean) = synchronized(lock) {
        stored = stored.copy(playlistOverrides = stored.playlistOverrides + (id to enabled))
        persistAndPublish()
    }

    fun setEpgEnabled(id: String, enabled: Boolean) = synchronized(lock) {
        stored = stored.copy(epgOverrides = stored.epgOverrides + (id to enabled))
        persistAndPublish()
    }

    fun addCustomPlaylist(name: String, url: String, vietnamese: Boolean): Result<Unit> = runCatching {
        val cleanName = name.trim().ifBlank { "Nguồn của tôi" }
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "Địa chỉ phải bắt đầu bằng http:// hoặc https://"
        }
        val source = PlaylistSource(
            id = "custom-" + UUID.randomUUID().toString().take(8),
            name = cleanName,
            url = cleanUrl,
            priority = 500,
            enabledByDefault = true,
            defaultCountry = if (vietnamese) "VN" else null,
            homepage = cleanUrl,
        )
        synchronized(lock) {
            stored = stored.copy(customPlaylists = stored.customPlaylists + source)
            persistAndPublish()
        }
    }

    fun addCustomEpg(name: String, url: String, vietnamese: Boolean): Result<Unit> = runCatching {
        val cleanName = name.trim().ifBlank { "EPG của tôi" }
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "Địa chỉ phải bắt đầu bằng http:// hoặc https://"
        }
        val source = EpgSource(
            id = "custom-epg-" + UUID.randomUUID().toString().take(8),
            name = cleanName,
            url = cleanUrl,
            priority = 500,
            enabledByDefault = true,
            defaultCountry = if (vietnamese) "VN" else null,
            homepage = cleanUrl,
        )
        synchronized(lock) {
            stored = stored.copy(customEpgs = stored.customEpgs + source)
            persistAndPublish()
        }
    }

    fun removeCustomPlaylist(id: String) = synchronized(lock) {
        stored = stored.copy(
            customPlaylists = stored.customPlaylists.filterNot { it.id == id },
            playlistOverrides = stored.playlistOverrides - id,
        )
        persistAndPublish()
    }

    fun removeCustomEpg(id: String) = synchronized(lock) {
        stored = stored.copy(
            customEpgs = stored.customEpgs.filterNot { it.id == id },
            epgOverrides = stored.epgOverrides - id,
        )
        persistAndPublish()
    }

    fun resetToDefaults() = synchronized(lock) {
        stored = StoredSources()
        persistAndPublish()
    }

    private fun buildUi(): SourcesUi {
        val playlistUi = buildList {
            defaults.playlists.forEach { source ->
                add(
                    PlaylistUi(
                        source = source,
                        enabled = stored.playlistOverrides[source.id] ?: source.enabledByDefault,
                        custom = false,
                    ),
                )
            }
            stored.customPlaylists.forEach { source ->
                add(
                    PlaylistUi(
                        source = source,
                        enabled = stored.playlistOverrides[source.id] ?: source.enabledByDefault,
                        custom = true,
                    ),
                )
            }
        }.sortedWith(compareByDescending<PlaylistUi> { it.source.priority }.thenBy { it.source.name })

        val epgUi = buildList {
            defaults.epgs.forEach { source ->
                add(
                    EpgUi(
                        source = source,
                        enabled = stored.epgOverrides[source.id] ?: source.enabledByDefault,
                        custom = false,
                    ),
                )
            }
            stored.customEpgs.forEach { source ->
                add(
                    EpgUi(
                        source = source,
                        enabled = stored.epgOverrides[source.id] ?: source.enabledByDefault,
                        custom = true,
                    ),
                )
            }
        }.sortedWith(compareByDescending<EpgUi> { it.source.priority }.thenBy { it.source.name })

        return SourcesUi(playlists = playlistUi, epgs = epgUi)
    }

    private fun persistAndPublish() {
        prefs.edit().putString(KEY_STORED, json.encodeToString(StoredSources.serializer(), stored)).apply()
        _state.value = buildUi()
    }

    private fun loadDefaults(): SourcesConfig {
        return runCatching {
            context.assets.open("sources.json").bufferedReader().use { it.readText() }
                .let { json.decodeFromString(SourcesConfig.serializer(), it) }
        }.getOrElse { SourcesConfig() }
    }

    private fun loadStored(): StoredSources {
        val raw = prefs.getString(KEY_STORED, null) ?: return StoredSources()
        return runCatching {
            json.decodeFromString(StoredSources.serializer(), raw)
        }.getOrDefault(StoredSources())
    }

    private companion object {
        const val KEY_STORED = "stored_sources"
    }
}
