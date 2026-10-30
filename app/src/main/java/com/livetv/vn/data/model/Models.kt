package com.livetv.vn.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamVariant(
    val url: String,
    val sourceId: String,
    val sourceName: String,
    val sourcePriority: Int = 0,
    val headers: Map<String, String> = emptyMap(),
    val qualityScore: Int = 2,
    val qualityLabel: String? = null,
)

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val tvgId: String? = null,
    val country: String? = null,
    val groups: List<String> = emptyList(),
    val streams: List<StreamVariant> = emptyList(),
    val number: Int = 0,
) {
    val isVietnamese: Boolean
        get() = country.equals("VN", ignoreCase = true)

    val bestQualityLabel: String?
        get() = streams.maxByOrNull { it.qualityScore }?.qualityLabel
}

@Serializable
data class SourceError(
    val sourceId: String,
    val sourceName: String,
    val message: String,
)

@Serializable
data class ChannelCatalog(
    val channels: List<Channel> = emptyList(),
    val updatedAt: Long = 0L,
    val errors: List<SourceError> = emptyList(),
) {
    val byId: Map<String, Channel> by lazy { channels.associateBy { it.id } }
    val byNumber: Map<Int, Channel> by lazy { channels.associateBy { it.number } }
}

@Serializable
data class Program(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String = "",
    val category: String = "",
)

@Serializable
data class PlaylistSource(
    val id: String,
    val name: String,
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val priority: Int = 0,
    val enabledByDefault: Boolean = true,
    val defaultCountry: String? = null,
    val homepage: String = "",
)

@Serializable
data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val priority: Int = 0,
    val enabledByDefault: Boolean = true,
    val defaultCountry: String? = null,
    val homepage: String = "",
)

@Serializable
data class SourcesConfig(
    val version: Int = 1,
    val playlists: List<PlaylistSource> = emptyList(),
    val epgs: List<EpgSource> = emptyList(),
)

data class PlaylistUi(
    val source: PlaylistSource,
    val enabled: Boolean,
    val custom: Boolean,
)

data class EpgUi(
    val source: EpgSource,
    val enabled: Boolean,
    val custom: Boolean,
)

data class SourcesUi(
    val playlists: List<PlaylistUi> = emptyList(),
    val epgs: List<EpgUi> = emptyList(),
)

@Serializable
data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val country: String? = null,
    val group: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val qualityScore: Int = 2,
    val qualityLabel: String? = null,
)

enum class QualityMode {
    AUTO,
    HIGH,
    SAVER,
}

fun qualityRank(label: String?): Int = when (label) {
    "4K" -> 4
    "FHD" -> 3
    "HD" -> 2
    "SD" -> 1
    else -> 3
}
