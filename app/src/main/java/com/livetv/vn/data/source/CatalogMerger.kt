package com.livetv.vn.data.source

import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.model.ChannelCatalog
import com.livetv.vn.data.model.PlaylistSource
import com.livetv.vn.data.model.SourceError
import com.livetv.vn.data.model.StreamVariant
import com.livetv.vn.data.model.M3uEntry
import com.livetv.vn.data.util.ChannelText
import java.util.Locale

data class PlaylistSnapshot(
    val source: PlaylistSource,
    val entries: List<M3uEntry>,
    val fromCache: Boolean = false,
    val error: String? = null,
)

object CatalogMerger {

    private class Acc(
        val id: String,
        val name: String,
        val country: String?,
    ) {
        val streams = LinkedHashMap<String, StreamVariant>()
        val groups = LinkedHashSet<String>()
        var logo: String? = null
        var tvgId: String? = null
    }

    private val vnOrder = mapOf(
        "vtv1" to 1,
        "vtv2" to 2,
        "vtv3" to 3,
        "vtv4" to 4,
        "vtv5" to 5,
        "vtv5taynambo" to 6,
        "vtv5taynguyen" to 7,
        "vtv6" to 8,
        "vtv7" to 9,
        "vtv8" to 10,
        "vtv9" to 11,
        "vtv10" to 12,
        "anninhtv" to 13,
        "quocphongvietnam" to 14,
        "htv1" to 20,
        "htv2" to 21,
        "htv3" to 22,
        "htv4" to 23,
        "htv5" to 24,
        "htv7" to 25,
        "htv9" to 26,
        "htvsports" to 27,
        "htvcfamily" to 28,
        "htvcmovies" to 29,
        "htvcwomen" to 30,
        "vtv1hd" to 1,
    )

    private val vietnameseDisplayNames = mapOf(
        "vn.vtv5taynambo" to "VTV5 Tây Nam Bộ",
        "vn.vtv5taynguyen" to "VTV5 Tây Nguyên",
    )

    private val internationalCountryOrder = mapOf(
        "US" to 1,
        "GB" to 2,
        "FR" to 3,
        "DE" to 4,
        "AU" to 5,
        "CA" to 6,
        "JP" to 7,
        "KR" to 8,
        "CN" to 9,
        "TW" to 10,
        "HK" to 11,
        "SG" to 12,
        "TH" to 13,
        "ID" to 14,
        "MY" to 15,
        "PH" to 16,
        "IN" to 17,
    )

    fun merge(snapshots: List<PlaylistSnapshot>, now: Long = System.currentTimeMillis()): ChannelCatalog {
        val ordered = snapshots.sortedByDescending { it.source.priority }
        val accumulators = LinkedHashMap<String, Acc>()
        val errors = ArrayList<SourceError>()

        ordered.forEach { snapshot ->
            if (snapshot.error != null) {
                errors += SourceError(snapshot.source.id, snapshot.source.name, snapshot.error)
            }
            snapshot.entries.forEach { entry ->
                val country = ChannelText.normalizeCountry(entry.country)
                    ?: ChannelText.countryFromTvgId(entry.tvgId)
                    ?: ChannelText.normalizeCountry(snapshot.source.defaultCountry)
                val displayName = ChannelText.cleanName(
                    entry.name.ifBlank { entry.tvgName ?: entry.tvgId ?: "Kênh" },
                )
                if (displayName.isBlank()) return@forEach
                val id = ChannelText.identityId(country, displayName)
                val acc = accumulators.getOrPut(id) {
                    Acc(
                        id = id,
                        name = displayName,
                        country = country,
                    )
                }
                if (acc.logo.isNullOrBlank() && !entry.logo.isNullOrBlank()) {
                    acc.logo = entry.logo
                }
                if (acc.tvgId.isNullOrBlank() && !entry.tvgId.isNullOrBlank()) {
                    acc.tvgId = entry.tvgId
                }
                entry.group?.split(';')?.map(String::trim)?.filter { it.isNotBlank() }?.forEach {
                    if (acc.groups.size < 6) acc.groups += it
                }
                if (acc.streams.size < 40 && !acc.streams.containsKey(entry.url)) {
                    acc.streams[entry.url] = StreamVariant(
                        url = entry.url,
                        sourceId = snapshot.source.id,
                        sourceName = snapshot.source.name,
                        sourcePriority = snapshot.source.priority,
                        headers = entry.headers,
                        qualityScore = entry.qualityScore,
                        qualityLabel = entry.qualityLabel,
                        fps = entry.fps,
                    )
                }
            }
        }

        val sorted = accumulators.values.sortedWith(
            compareBy<Acc> { if (it.country == "VN") 0 else if (it.country == null) 2 else 1 }
                .thenBy {
                    if (it.country == "VN") {
                        vnOrder[ChannelText.nameKey(it.name)] ?: 1000
                    } else {
                        0
                    }
                }
                .thenBy {
                    if (it.country != "VN") {
                        internationalCountryOrder[it.country] ?: 500
                    } else {
                        0
                    }
                }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )

        val channels = sorted.mapIndexed { index, acc ->
            Channel(
                id = acc.id,
                name = vietnameseDisplayNames[acc.id] ?: acc.name,
                logo = acc.logo,
                tvgId = acc.tvgId,
                country = acc.country,
                groups = acc.groups.toList(),
                streams = acc.streams.values.toList(),
                number = index + 1,
            )
        }
        return ChannelCatalog(
            channels = channels,
            updatedAt = now,
            errors = errors,
        )
    }
}
