package com.livetv.vn.data.util

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

object ChannelText {

    data class Quality(val score: Int, val label: String?)

    private val isoCountries: Set<String> by lazy { Locale.getISOCountries().toSet() }

    private val bracketFlag = Regex(
        "\\[(?:geo[- ]?blocked|not\\s*24/7|offline|24/7)\\]",
        RegexOption.IGNORE_CASE,
    )
    private val qualityParen = Regex(
        "\\((?:\\d{3,4}[pi]?|hd|fhd|uhd|sd|4k|8k|hevc|h\\.?26[45])\\)",
        RegexOption.IGNORE_CASE,
    )
    private val trailingQuality = Regex(
        "(?:(?<=\\s)|^)(?:\\d{3,4}[pi]?|fhd|uhd|hd|sd|4k|8k)$",
        RegexOption.IGNORE_CASE,
    )
    private val nonAlphaNum = Regex("[^\\p{L}\\p{N}]+")
    private val marks = Regex("\\p{M}+")

    private val qualityTokens = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "hdr", "hq",
        "1080", "1080p", "1080i", "720", "720p", "576", "576p",
        "480", "480p", "360", "360p", "2160", "2160p",
    )

    private val aliases = mapOf(
        "antv" to "anninhtv",
        "anninhtv" to "anninhtv",
        "qpvn" to "quocphongvietnam",
        "quocphongvietnam" to "quocphongvietnam",
        "htvthethao" to "htvsports",
        "htvsports" to "htvsports",
    )

    fun normalizeCountry(raw: String?): String? {
        val cleaned = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
        if (cleaned.isBlank()) return null
        if (cleaned.contains(';')) return normalizeCountry(cleaned.substringBefore(';'))
        return when (cleaned) {
            "UK", "EN" -> "GB"
            "USA" -> "US"
            else -> if (cleaned in isoCountries) cleaned else null
        }
    }

    fun countryFromTvgId(tvgId: String?): String? {
        val value = tvgId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withoutSuffix = value.substringBefore('@')
        val parts = withoutSuffix.split('.', '_', '-').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        var candidate = parts.last()
        if (candidate.equals("hd", true) || candidate.equals("sd", true) ||
            candidate.equals("fhd", true) || candidate.equals("uhd", true)
        ) {
            candidate = parts.getOrNull(parts.lastIndex - 1) ?: return null
        }
        if (!candidate.all { it.isLetter() }) return null
        return normalizeCountry(candidate)
    }

    fun quality(name: String, url: String? = null): Quality {
        val nameText = name.lowercase(Locale.ROOT)
        val urlText = url?.lowercase(Locale.ROOT).orEmpty()
        val fromName = qualityFromText(nameText)
        val fromUrl = qualityFromText(urlText)
        if (fromName == null && fromUrl == null) return Quality(2, null)
        if (fromName == null) return fromUrl!!
        if (fromUrl == null) return fromName
        return if (fromName.score <= fromUrl.score) fromName else fromUrl
    }

    private fun qualityFromText(text: String): Quality? {
        fun present(vararg values: String): Boolean =
            values.any { Regex("(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])").containsMatchIn(text) }

        return when {
            present("2160", "2160p", "4k", "uhd") -> Quality(4, "4K")
            present("1080", "1080p", "1080i", "fhd") -> Quality(3, "FHD")
            present("720", "720p", "hd") -> Quality(2, "HD")
            present("576", "576p", "480", "480p", "360", "360p", "sd") -> Quality(1, "SD")
            else -> null
        }
    }

    fun cleanName(raw: String): String {
        var value = raw
            .replace(bracketFlag, " ")
            .replace(qualityParen, " ")
            .replace('Ⓢ', ' ')
            .replace('Ⓣ', ' ')
            .replace('Ⓨ', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        repeat(2) {
            value = value.replace(trailingQuality, "").trim()
        }
        value = value.replace(Regex("^[\\s\\-–—:•]+|[\\s\\-–—:•]+$"), "")
        return value.ifBlank { raw.trim() }
    }

    fun nameKey(raw: String): String {
        val cleaned = cleanName(raw)
        val ascii = removeDiacritics(cleaned).lowercase(Locale.ROOT)
        val tokens = nonAlphaNum.split(ascii)
            .filter { it.isNotBlank() }
            .map(::stripQualitySuffix)
            .filterNot { it in qualityTokens }
        return applyAlias(tokens.joinToString(""))
    }

    fun idKey(rawId: String?): String {
        if (rawId.isNullOrBlank()) return ""
        var value = rawId.substringBefore('@')
            .replace(qualityParen, " ")
        val ascii = removeDiacritics(value).lowercase(Locale.ROOT)
        var tokens = nonAlphaNum.split(ascii)
            .filter { it.isNotBlank() }
            .map(::stripQualitySuffix)
            .filterNot { it in qualityTokens }
        if (tokens.size > 1) {
            val last = tokens.last()
            if (last.uppercase(Locale.ROOT) in isoCountries || last == "uk") {
                tokens = tokens.dropLast(1)
            }
        }
        tokens = tokens.filterNot { it in qualityTokens }
        return applyAlias(tokens.joinToString(""))
    }

    private fun stripQualitySuffix(token: String): String {
        var value = token
        value = value.replace(Regex("(?<=\\d)(?:fhd|uhd|hd|sd)"), "")
        for (suffix in listOf("fhd", "uhd", "4k", "8k", "hd", "sd")) {
            if (value.length > suffix.length + 1 && value.endsWith(suffix)) {
                value = value.dropLast(suffix.length)
            }
        }
        return value
    }

    private fun applyAlias(key: String): String = aliases[key] ?: key

    private fun removeDiacritics(value: String): String {
        val nfd = Normalizer.normalize(value, Normalizer.Form.NFD)
        return nfd.replace(marks, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }

    fun identityBase(country: String?, name: String): String {
        val cc = normalizeCountry(country)?.lowercase(Locale.ROOT) ?: "intl"
        val key = nameKey(name).ifBlank { "n" + sha1(name).take(8) }
        return "$cc|$key"
    }

    fun identityId(country: String?, name: String): String {
        val base = identityBase(country, name)
        val simple = base.replace('|', '.')
        if (simple.all { it.code < 128 }) return simple
        val cc = simple.substringBefore('.')
        val digest = sha1(base).take(12)
        return "$cc.$digest"
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
