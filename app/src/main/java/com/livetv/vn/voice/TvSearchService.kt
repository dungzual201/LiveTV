package com.livetv.vn.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.livetv.vn.LiveTvApp
import com.livetv.vn.data.guide.ProgramGuide
import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.util.ChannelSearch
import com.livetv.vn.data.util.ChannelText
import com.livetv.vn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TvSearchService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = (application as LiveTvApp).graph

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "LiveTVVoiceSearch").apply {
            setSessionActivity(openAppIntent())
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                        val clean = query?.trim().orEmpty()
                        if (clean.isEmpty()) {
                            startActivity(openAppIntentActivity())
                        } else {
                            startActivity(VoiceIntents.searchIntent(clean))
                        }
                    }

                    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                        if (!mediaId.isNullOrBlank()) {
                            startActivity(VoiceIntents.channelIntent(mediaId))
                        }
                    }

                    override fun onPlay() {
                        startActivity(openAppIntentActivity())
                    }
                },
            )
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "LiveTV")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Truyền hình trực tuyến")
                    .build(),
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID,
                    )
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1f)
                    .build(),
            )
            isActive = true
        }
        sessionToken = session.sessionToken
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }
        val immediate = browserItems()
        if (immediate.isNotEmpty()) {
            result.sendResult(immediate)
            return
        }
        result.detach()
        scope.launch {
            runCatching { graph.catalog.loadCached() }
            result.sendResult(browserItems())
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: MediaBrowserServiceCompat.Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val immediate = searchItems(query)
        if (immediate.isNotEmpty()) {
            result.sendResult(immediate)
            return
        }
        result.detach()
        scope.launch {
            runCatching { graph.catalog.loadCached() }
            result.sendResult(searchItems(query))
        }
    }

    private fun browserItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return graph.catalog.catalog.value.channels
            .take(120)
            .map(::toMediaItem)
            .toMutableList()
    }

    private fun searchItems(query: String?): MutableList<MediaBrowserCompat.MediaItem> {
        val channels = graph.catalog.catalog.value.channels
        if (channels.isEmpty()) return mutableListOf()
        val clean = query?.trim().orEmpty()
        if (clean.isEmpty()) return channels.take(80).map(::toMediaItem).toMutableList()
        val top = ChannelSearch.find(channels, clean)
        val key = ChannelText.nameKey(clean)
        val matches = channels.filter { channel ->
            channel.id == top?.id ||
                (key.isNotBlank() && (
                    ChannelText.nameKey(channel.name).contains(key) ||
                        ChannelText.idKey(channel.tvgId).contains(key)
                    ))
        }.take(24)
        return (if (matches.isNotEmpty()) matches else listOfNotNull(top))
            .map(::toMediaItem)
            .toMutableList()
    }

    private fun toMediaItem(channel: Channel): MediaBrowserCompat.MediaItem {
        val now = System.currentTimeMillis()
        val current = ProgramGuide.current(graph.epg.programs.value[channel.id], now)
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(channel.id)
            .setTitle(channel.name)
            .setSubtitle(
                current?.title?.let { "Đang phát: $it" }
                    ?: "Kênh ${channel.number.toString().padStart(3, '0')}",
            )
            .setIconUri(channel.logo?.takeIf { it.startsWith("http") }?.let(android.net.Uri::parse))
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntentActivity(): Intent =
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
