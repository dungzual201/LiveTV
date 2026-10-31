package com.livetv.vn.data.source

import com.livetv.vn.data.model.M3uEntry
import com.livetv.vn.data.util.ChannelText
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class M3uParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private class Pending(
        var title: String,
        val attrs: Map<String, String>,
        val headers: MutableMap<String, String> = linkedMapOf(),
        var group: String? = null,
    )

    private val attrRegex = Regex("([A-Za-z0-9_-]+)=(?:\"([^\"]*)\"|([^\\s,]+))")

    fun parse(content: String, playlistUrl: String): List<M3uEntry> {
        val entries = ArrayList<M3uEntry>()
        var pending: Pending? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pending = parseInfo(line, pending?.headers)
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pending?.group = line.substringAfter(':').trim().ifBlank { null }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    parseVlcOption(line.substringAfter(':').trim(), pending?.headers)
                }

                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    parseHttpJson(line.substringAfter(':').trim(), pending?.headers)
                }

                line.startsWith("#") -> Unit

                pending != null -> {
                    val info = pending
                    pending = null
                    val resolved = resolveUrl(line, playlistUrl) ?: return@forEach
                    if (!isSupportedStream(resolved.first)) return@forEach
                    val displayName = info.title.trim()
                        .ifBlank { info.attrs["tvg-name"].orEmpty() }
                        .ifBlank { info.attrs["tvg-id"].orEmpty() }
                        .ifBlank { "Kênh" }
                    val quality = ChannelText.quality(
                        displayName + " " + info.attrs["tvg-name"].orEmpty(),
                        resolved.first,
                    )
                    val fps = ChannelText.fps(
                        displayName + " " + info.attrs["tvg-name"].orEmpty(),
                        resolved.first,
                    )
                    entries += M3uEntry(
                        name = displayName,
                        url = resolved.first,
                        tvgId = info.attrs["tvg-id"]?.ifBlank { null },
                        tvgName = info.attrs["tvg-name"]?.ifBlank { null },
                        logo = info.attrs["tvg-logo"]?.ifBlank { null },
                        country = info.attrs["tvg-country"]?.ifBlank { null },
                        group = info.group ?: info.attrs["group-title"]?.ifBlank { null },
                        headers = info.headers + resolved.second,
                        qualityScore = quality.score,
                        qualityLabel = quality.label,
                        fps = fps,
                    )
                }
            }
        }
        return entries
    }

    private fun parseInfo(line: String, inheritedHeaders: MutableMap<String, String>?): Pending {
        val payload = line.substringAfter(':').trim()
        val comma = findTitleComma(payload)
        val attrPart = if (comma >= 0) payload.substring(0, comma) else payload
        val title = if (comma >= 0) payload.substring(comma + 1).trim() else ""
        val attrs = linkedMapOf<String, String>()
        attrRegex.findAll(attrPart).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            if (value.isNotBlank()) attrs[key] = value.trim()
        }
        return Pending(
            title = title,
            attrs = attrs,
            headers = inheritedHeaders ?: linkedMapOf(),
        )
    }

    private fun findTitleComma(value: String): Int {
        var inQuotes = false
        value.forEachIndexed { index, char ->
            when (char) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return index
            }
        }
        return -1
    }

    private fun parseVlcOption(option: String, headers: MutableMap<String, String>?) {
        if (headers == null) return
        val key = option.substringBefore('=').trim().lowercase()
        val value = option.substringAfter('=', "").trim()
        if (value.isNotBlank()) addHeader(headers, key, value)
    }

    private fun parseHttpJson(raw: String, headers: MutableMap<String, String>?) {
        if (headers == null || raw.isBlank()) return
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return
        element.forEach { (key, value) ->
            val text = runCatching { value.jsonPrimitive.content }.getOrNull()
            if (!text.isNullOrBlank()) addHeader(headers, key, text)
        }
    }

    private fun addHeader(headers: MutableMap<String, String>, rawKey: String, value: String) {
        val key = when (rawKey.lowercase()) {
            "user-agent", "http-user-agent" -> "User-Agent"
            "referer", "referrer", "http-referrer" -> "Referer"
            "origin", "http-origin" -> "Origin"
            else -> rawKey
        }
        headers[key] = value
    }

    private fun resolveUrl(rawInput: String, playlistUrl: String): Pair<String, Map<String, String>>? {
        var raw = rawInput.trim()
        val extraHeaders = linkedMapOf<String, String>()
        val pipe = raw.indexOf('|')
        if (pipe > 0) {
            val extras = raw.substring(pipe + 1)
            if (extras.contains('=')) {
                raw = raw.substring(0, pipe)
                extras.split('&').forEach { pair ->
                    val key = pair.substringBefore('=', "").trim()
                    val value = pair.substringAfter('=', "").trim()
                    if (key.isNotBlank() && value.isNotBlank()) addHeader(extraHeaders, key, value)
                }
            }
        }
        raw = raw.replace(" ", "%20")
        if (raw.isBlank()) return null
        val absolute = runCatching {
            val uri = URI(raw)
            if (uri.isAbsolute) raw else URI(playlistUrl).resolve(raw).toString()
        }.getOrNull() ?: return null
        return absolute to extraHeaders
    }

    private fun isSupportedStream(url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (lower.contains("youtube.com/") || lower.contains("youtu.be/") || lower.contains("twitch.tv/")) {
            return false
        }
        val path = lower.substringBefore('?').substringBefore('#')
        if (path.endsWith(".m3u")) return false
        return true
    }
}
