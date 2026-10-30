package com.livetv.vn.tif

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvInputService
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.livetv.vn.voice.VoiceIntents

class LiveTvInputService : TvInputService() {

    override fun onCreateSession(inputId: String): Session {
        Log.i(TAG, "onCreateSession($inputId)")
        return LiveTvSession(this)
    }

    private class LiveTvSession(context: Context) : TvInputService.Session(context) {

        private val appContext = context.applicationContext

        override fun onTune(channelUri: Uri): Boolean {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
            val channel = resolveChannel(channelUri)
            Log.i(TAG, "onTune($channelUri) appChannel=${channel?.first} name=${channel?.second}")
            val intent = when {
                channel?.first != null -> VoiceIntents.channelIntent(channel.first!!)
                channel?.second != null -> VoiceIntents.searchIntent(channel.second!!)
                else -> null
            }
            if (intent != null) {
                runCatching { appContext.startActivity(intent) }
                    .onFailure { Log.e(TAG, "Cannot open LiveTV: ${it.message}") }
            }
            notifyVideoAvailable()
            return true
        }

        private fun resolveChannel(uri: Uri): Pair<String?, String?>? {
            return runCatching {
                appContext.contentResolver.query(
                    uri,
                    arrayOf(
                        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
                        TvContract.Channels.COLUMN_DISPLAY_NAME,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val dataIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA)
                    val nameIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME)
                    val appId = if (dataIndex >= 0) cursor.getBlob(dataIndex)?.toString(Charsets.UTF_8) else null
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    appId to name
                }
            }.getOrNull()
        }

        override fun onSetSurface(surface: Surface?): Boolean = true

        override fun onSetStreamVolume(volume: Float) = Unit

        override fun onSetCaptionEnabled(enabled: Boolean) = Unit

        override fun onRelease() = Unit
    }

    private companion object {
        const val TAG = "LiveTvTif"
    }
}
