package com.livetv.vn.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.model.QualityMode
import com.livetv.vn.data.model.StreamVariant
import com.livetv.vn.data.store.HealthStore
import com.livetv.vn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class LivePlayer(
    context: Context,
    private val health: HealthStore,
    private val qualityMode: () -> QualityMode,
    private val scope: CoroutineScope,
    private val onState: (PlaybackSnapshot) -> Unit,
) {
    private val appContext = context.applicationContext
    private val headerState = StreamHeaderState()
    private val dataSourceFactory = HeaderRoutingDataSource.Factory(headerState, USER_AGENT)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setRenderersFactory(
            DefaultRenderersFactory(appContext).setEnableDecoderFallback(true),
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_REBUFFER_MS,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .build()

    private var mediaSession: MediaSession? = null

    private var channel: Channel? = null
    private var orderedStreams: List<StreamVariant> = emptyList()
    private var nextIndex = 0
    private var attemptLimit = 0
    private var attemptReady = false
    private var advancing = false
    private var released = false
    private var hostStarted = false
    private var pausedByHost = false
    private var attemptStartedAt = 0L
    private var lastFailureReason: String? = null
    private var currentVariant: StreamVariant? = null

    private var connectJob: Job? = null
    private var stallJob: Job? = null
    private var stableJob: Job? = null

    val currentChannelId: String? get() = channel?.id

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (released) return
                    when (playbackState) {
                        Player.STATE_READY -> onReady()
                        Player.STATE_BUFFERING -> scheduleStallTimeout()
                        Player.STATE_ENDED -> failCurrent("Luồng đã kết thúc")
                        Player.STATE_IDLE -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (released) return
                    failCurrent(describePlaybackError(error))
                }
            },
        )
        onState(PlaybackSnapshot())
    }

    fun play(channel: Channel) {
        if (released) return
        advancing = true
        cancelJobs()
        player.stop()
        player.clearMediaItems()
        headerState.headers = emptyMap()
        this.channel = channel
        orderedStreams = StreamSelector.order(channel.streams, health, qualityMode())
        attemptLimit = minOf(orderedStreams.size, MAX_AUTO_ATTEMPTS)
        nextIndex = 0
        attemptReady = false
        advancing = false
        pausedByHost = !hostStarted
        lastFailureReason = null
        currentVariant = null
        if (attemptLimit == 0) {
            emitError(channel, "Kênh này chưa có nguồn phát")
            return
        }
        startAttempt()
    }

    fun retry() {
        val active = channel ?: return
        play(active)
    }

    fun stop() {
        if (released) return
        advancing = true
        cancelJobs()
        player.stop()
        player.clearMediaItems()
        mediaSession?.release()
        mediaSession = null
        headerState.headers = emptyMap()
        channel = null
        orderedStreams = emptyList()
        currentVariant = null
        nextIndex = 0
        attemptLimit = 0
        attemptReady = false
        advancing = false
        pausedByHost = false
        lastFailureReason = null
        onState(PlaybackSnapshot())
    }

    fun onHostStop() {
        if (released) return
        hostStarted = false
        if (channel != null) {
            pausedByHost = true
            player.playWhenReady = false
        }
    }

    fun onHostStart() {
        if (released) return
        hostStarted = true
        if (pausedByHost) {
            pausedByHost = false
            if (channel != null && !advancing) player.playWhenReady = true
        }
    }

    fun release() {
        if (released) return
        released = true
        cancelJobs()
        mediaSession?.release()
        mediaSession = null
        player.release()
    }

    private fun startAttempt() {
        val activeChannel = channel ?: return
        if (nextIndex >= attemptLimit) {
            emitError(activeChannel, lastFailureReason ?: "Không có nguồn phát khả dụng")
            return
        }
        cancelJobs()
        val variant = orderedStreams[nextIndex]
        currentVariant = variant
        attemptReady = false
        advancing = false
        attemptStartedAt = SystemClock.elapsedRealtime()
        headerState.headers = variant.headers
        val status = if (nextIndex == 0) {
            PlaybackStatus.CONNECTING
        } else {
            PlaybackStatus.FAILOVER
        }
        emit(
            snapshot(
                activeChannel = activeChannel,
                status = status,
                error = if (status == PlaybackStatus.FAILOVER) lastFailureReason else null,
            ),
        )
        try {
            val item = buildMediaItem(activeChannel, variant)
            player.setMediaItem(item)
            ensureMediaSession()
            player.prepare()
            player.playWhenReady = !pausedByHost
        } catch (error: Exception) {
            failCurrent(error.message ?: "Không khởi tạo được nguồn phát")
            return
        }
        connectJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!released && !advancing && !attemptReady) {
                failCurrent("Hết thời gian kết nối nguồn")
            }
        }
    }

    private fun onReady() {
        if (advancing || released) return
        val activeChannel = channel ?: return
        attemptReady = true
        connectJob?.cancel()
        stallJob?.cancel()
        emit(
            snapshot(
                activeChannel = activeChannel,
                status = PlaybackStatus.PLAYING,
                error = null,
            ),
        )
        stableJob?.cancel()
        val readyLatency = SystemClock.elapsedRealtime() - attemptStartedAt
        stableJob = scope.launch {
            delay(SUCCESS_STABLE_MS)
            val variant = currentVariant
            if (!released && !advancing && attemptReady && variant != null) {
                health.recordSuccess(variant.url, readyLatency)
            }
        }
    }

    private fun scheduleStallTimeout() {
        if (!attemptReady || advancing || released) return
        stallJob?.cancel()
        stallJob = scope.launch {
            delay(STALL_TIMEOUT_MS)
            if (!released && !advancing && attemptReady &&
                player.playbackState == Player.STATE_BUFFERING
            ) {
                failCurrent("Nguồn bị treo khi đang phát")
            }
        }
    }

    private fun failCurrent(reason: String) {
        if (advancing || released) return
        val activeChannel = channel ?: return
        advancing = true
        attemptReady = false
        cancelJobs()
        lastFailureReason = reason
        currentVariant?.let { health.recordFailure(it.url) }
        player.stop()
        nextIndex++
        if (nextIndex >= attemptLimit) {
            emitError(
                activeChannel,
                "$reason. Đã thử $attemptLimit nguồn.",
                lastAttempt = attemptLimit,
            )
            return
        }
        startAttempt()
    }

    private fun ensureMediaSession() {
        if (mediaSession != null || released) return
        mediaSession = MediaSession.Builder(appContext, player)
            .setId("livetv-local-session")
            .setSessionActivity(openAppIntent())
            .build()
    }

    private fun emitError(
        activeChannel: Channel,
        message: String,
        lastAttempt: Int = attemptLimit,
    ) {
        emit(
            PlaybackSnapshot(
                channelId = activeChannel.id,
                channelName = activeChannel.name,
                status = PlaybackStatus.ERROR,
                attempt = lastAttempt,
                total = attemptLimit,
                sourceName = currentVariant?.sourceName,
                qualityLabel = currentVariant?.qualityLabel,
                error = message,
            ),
        )
    }

    private fun emit(snapshot: PlaybackSnapshot) {
        onState(snapshot)
    }

    private fun snapshot(
        activeChannel: Channel,
        status: PlaybackStatus,
        error: String?,
    ): PlaybackSnapshot = PlaybackSnapshot(
        channelId = activeChannel.id,
        channelName = activeChannel.name,
        status = status,
        attempt = nextIndex + 1,
        total = attemptLimit,
        sourceName = currentVariant?.sourceName,
        qualityLabel = currentVariant?.qualityLabel,
        error = error,
    )

    private fun cancelJobs() {
        connectJob?.cancel()
        stallJob?.cancel()
        stableJob?.cancel()
        connectJob = null
        stallJob = null
        stableJob = null
    }

    private fun buildMediaItem(activeChannel: Channel, variant: StreamVariant): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(activeChannel.name)
            .setArtist(variant.sourceName)
            .setSubtitle(variant.qualityLabel ?: "Live TV")
        activeChannel.logo?.takeIf { it.startsWith("http") }?.let {
            runCatching { metadata.setArtworkUri(Uri.parse(it)) }
        }
        val builder = MediaItem.Builder()
            .setUri(variant.url)
            .setMediaId(activeChannel.id)
            .setMediaMetadata(metadata.build())
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(3_500)
                    .setMinOffsetMs(1_500)
                    .setMaxOffsetMs(18_000)
                    .setMinPlaybackSpeed(0.95f)
                    .setMaxPlaybackSpeed(1.05f)
                    .build(),
            )
        inferMimeType(variant.url)?.let(builder::setMimeType)
        return builder.build()
    }

    private fun inferMimeType(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".ism") || path.endsWith(".isml") -> MimeTypes.APPLICATION_SS
            path.endsWith(".mp4") || path.endsWith(".mkv") ||
                path.endsWith(".webm") || path.endsWith(".ts") -> null
            else -> MimeTypes.APPLICATION_M3U8
        }
    }

    private fun describePlaybackError(error: PlaybackException): String {
        if (error.cause is HttpDataSource.InvalidResponseCodeException) {
            val code = (error.cause as HttpDataSource.InvalidResponseCodeException).responseCode
            return when (code) {
                401, 403 -> "Nguồn chặn truy cập (HTTP $code)"
                404 -> "Không tìm thấy luồng (HTTP 404)"
                429 -> "Nguồn giới hạn kết nối (HTTP 429)"
                else -> "Nguồn trả về lỗi HTTP $code"
            }
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "Không kết nối được nguồn"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Nguồn trả về lỗi HTTP"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "Luồng HTTP bị hệ thống chặn"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "Nguồn trả về nội dung không hợp lệ"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> "Luồng IPTV bị lỗi định dạng"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> "Định dạng luồng không được hỗ trợ"
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            -> "Thiết bị không giải mã được luồng này"
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            -> "Luồng yêu cầu DRM không hỗ trợ"
            else -> "Không phát được nguồn (${error.errorCodeName})"
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val USER_AGENT = "LiveTV/1.0 (Android TV)"
        const val MAX_AUTO_ATTEMPTS = 12
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val STALL_TIMEOUT_MS = 30_000L
        const val SUCCESS_STABLE_MS = 6_000L

        const val MIN_BUFFER_MS = 8_000
        const val MAX_BUFFER_MS = 30_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_000
        const val BUFFER_FOR_REBUFFER_MS = 3_000
    }
}
