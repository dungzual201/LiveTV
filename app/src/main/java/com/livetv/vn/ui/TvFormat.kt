package com.livetv.vn.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val viLocale = Locale.forLanguageTag("vi-VN")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", viLocale)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM · HH:mm", viLocale)
private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM", viLocale)
private val dateShortFormatter = DateTimeFormatter.ofPattern("dd/MM")

fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormatter)

fun formatDateTime(millis: Long): String =
    if (millis <= 0L) "chưa có"
    else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormatter)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(viLocale) else it.toString() }

fun formatDateShort(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateShortFormatter)

fun formatRange(startMs: Long, endMs: Long): String =
    "${formatClock(startMs)} – ${formatClock(endMs)}"

fun formatRemaining(endMs: Long, nowMs: Long): String {
    val minutes = ((endMs - nowMs) / 60_000).coerceAtLeast(0)
    return when {
        minutes >= 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "còn $h giờ" else "còn $h giờ $m phút"
        }
        else -> "còn $minutes phút"
    }
}

fun formatDuration(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}g ${minutes % 60}p"
    else -> "${minutes}p"
}

fun isToday(millis: Long): Boolean =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

fun channelNumber(number: Int): String = number.toString().padStart(3, '0')

fun sourceCountText(count: Int): String =
    if (count <= 1) "$count nguồn" else "$count nguồn"
