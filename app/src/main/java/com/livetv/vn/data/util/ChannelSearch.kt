package com.livetv.vn.data.util

import com.livetv.vn.data.model.Channel
import java.text.Normalizer
import java.util.Locale

object ChannelSearch {

    private val requestWords = Regex(
        "\\b(mo|bat|xem|kenh|so|truyen|hinh|tv|channel|play|watch|on|live|tren|ung|dung)\\b",
    )
    private val marks = Regex("\\p{M}+")

    data class PreparedQuery(val key: String, val numeric: Int?)

    fun prepareQuery(rawQuery: String): PreparedQuery? {
        val query = rawQuery.trim()
        if (query.isBlank()) return null
        val ascii = Normalizer.normalize(query, Normalizer.Form.NFD)
            .replace(marks, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
        val stripped = ChannelText.nameKey(ascii.replace(requestWords, " "))
            .ifBlank { ChannelText.nameKey(query) }
        val numeric = stripped.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.toIntOrNull()
        if (stripped.isBlank() && numeric == null) return null
        return PreparedQuery(stripped, numeric)
    }

    fun score(query: PreparedQuery, nameKey: String, idKey: String, number: Int): Int = when {
        query.key.isBlank() -> 0
        nameKey == query.key -> 140
        idKey == query.key -> 135
        query.numeric != null && number == query.numeric -> 125
        nameKey.startsWith(query.key) -> 110
        idKey.startsWith(query.key) -> 105
        query.key.length >= 3 && nameKey.contains(query.key) -> 80
        query.key.length >= 3 && idKey.contains(query.key) -> 75
        else -> 0
    }

    fun find(channels: List<Channel>, rawQuery: String): Channel? =
        rank(channels, rawQuery).firstOrNull()

    fun rank(channels: List<Channel>, rawQuery: String): List<Channel> {
        if (channels.isEmpty()) return emptyList()
        val query = prepareQuery(rawQuery) ?: return emptyList()
        return channels.mapNotNull { channel ->
            val score = score(
                query,
                ChannelText.nameKey(channel.name),
                ChannelText.idKey(channel.tvgId),
                channel.number,
            )
            if (score == 0) null else channel to score
        }
            .sortedWith(
                compareByDescending<Pair<Channel, Int>> { it.second }
                    .thenBy { it.first.number },
            )
            .map { it.first }
    }
}
