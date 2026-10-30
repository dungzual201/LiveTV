package com.livetv.vn.data.store

import android.content.Context
import com.livetv.vn.data.model.QualityMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("livetv_settings", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet(),
    )
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _qualityMode = MutableStateFlow(
        runCatching {
            QualityMode.valueOf(prefs.getString(KEY_QUALITY, QualityMode.AUTO.name)!!)
        }.getOrDefault(QualityMode.AUTO),
    )
    val qualityMode: StateFlow<QualityMode> = _qualityMode.asStateFlow()

    fun toggleFavorite(channelId: String) {
        val updated = _favorites.value.toMutableSet()
        if (!updated.add(channelId)) updated.remove(channelId)
        _favorites.value = updated
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    fun setFavorite(channelId: String, favorite: Boolean) {
        val updated = _favorites.value.toMutableSet()
        if (favorite) updated.add(channelId) else updated.remove(channelId)
        _favorites.value = updated
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
    }

    fun setQualityMode(mode: QualityMode) {
        _qualityMode.value = mode
        prefs.edit().putString(KEY_QUALITY, mode.name).apply()
    }

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()
        }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_QUALITY = "quality_mode"
        const val KEY_LAST_CHANNEL = "last_channel"
    }
}
