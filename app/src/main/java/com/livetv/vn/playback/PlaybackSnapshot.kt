package com.livetv.vn.playback

enum class PlaybackStatus {
    IDLE,
    CONNECTING,
    PLAYING,
    FAILOVER,
    ERROR,
}

data class PlaybackSnapshot(
    val channelId: String? = null,
    val channelName: String = "",
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val attempt: Int = 0,
    val total: Int = 0,
    val sourceName: String? = null,
    val qualityLabel: String? = null,
    val error: String? = null,
) {
    val hasChannel: Boolean get() = channelId != null
    val isError: Boolean get() = status == PlaybackStatus.ERROR
    val isBuffering: Boolean get() = status == PlaybackStatus.CONNECTING || status == PlaybackStatus.FAILOVER
}
