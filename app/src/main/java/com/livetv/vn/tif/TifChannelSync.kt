package com.livetv.vn.tif

import android.content.ContentUris
import android.content.ContentValues
import android.content.ComponentName
import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputService
import android.net.Uri
import android.util.Log
import com.livetv.vn.search.SearchEntry
import com.livetv.vn.search.SearchIndexStore
import kotlinx.serialization.json.Json

object TifChannelSync {

    private const val TAG = "LiveTvTif"

    fun defaultInputId(context: Context): String =
        ComponentName(context, LiveTvInputService::class.java).flattenToShortString()

    fun syncFromIndex(
        context: Context,
        inputId: String = defaultInputId(context),
    ) {
        val store = SearchIndexStore(
            context.applicationContext,
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
        sync(context, store.allEntries(), inputId)
    }

    fun sync(
        context: Context,
        entries: List<SearchEntry>,
        inputId: String = defaultInputId(context),
    ) {
        val vietnamese = entries
            .filter { it.country.equals("VN", ignoreCase = true) || it.id.startsWith("vn.") }
            .distinctBy { it.number }
            .take(250)
        if (vietnamese.isEmpty()) return

        val resolver = context.contentResolver
        try {
            val existing = HashMap<String, Long>()
            resolver.query(
                TvContract.buildChannelsUriForInput(inputId),
                arrayOf(
                    TvContract.Channels._ID,
                    TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(TvContract.Channels._ID)
                val numberIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NUMBER)
                while (cursor.moveToNext()) {
                    cursor.getString(numberIndex)?.let { number ->
                        existing[number] = cursor.getLong(idIndex)
                    }
                }
            }

            val aliasEntries = vietnamese.firstOrNull { it.id == "vn.vtv1" }?.let { vtv1 ->
                listOf(
                    vtv1.copy(name = "TV One", number = 9001),
                    vtv1.copy(name = "TV1", number = 9002),
                    vtv1.copy(name = "VTV One", number = 9003),
                    vtv1.copy(name = "VTV 1", number = 9004),
                )
            }.orEmpty()

            var inserted = 0
            var updated = 0
            (vietnamese + aliasEntries).forEach { entry ->
                val number = entry.number.toString()
                val values = channelValues(entry, inputId)
                val existingId = existing[number]
                if (existingId != null) {
                    resolver.update(
                        ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, existingId),
                        values,
                        null,
                        null,
                    )
                    updated++
                } else {
                    resolver.insert(TvContract.Channels.CONTENT_URI, values)
                    inserted++
                }
            }
            Log.i(TAG, "Synced $inserted new / $updated existing channels for $inputId")
        } catch (error: Throwable) {
            Log.e(TAG, "TvProvider sync failed: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun channelValues(entry: SearchEntry, inputId: String): ContentValues {
        val watchUri = Uri.Builder()
            .scheme("livetv")
            .authority("watch")
            .appendQueryParameter("channel", entry.id)
            .build()
            .toString()
        return ContentValues().apply {
            put(TvContract.Channels.COLUMN_INPUT_ID, inputId)
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER)
            put(TvContract.Channels.COLUMN_SERVICE_TYPE, TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
            put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, entry.number.toString())
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, entry.name)
            put(TvContract.Channels.COLUMN_DESCRIPTION, "LiveTV · IPTV")
            put(TvContract.Channels.COLUMN_SEARCHABLE, 1)
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, entry.id.encodeToByteArray())
            put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, watchUri)
            put(TvContract.Channels.COLUMN_APP_LINK_TEXT, "Xem trên LiveTV")
            put(TvContract.Channels.COLUMN_APP_LINK_COLOR, 0xFF111418.toInt())
            entry.logo?.takeIf { it.startsWith("http") }?.let { logo ->
                put(TvContract.Channels.COLUMN_APP_LINK_ICON_URI, logo)
                put(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI, logo)
            }
        }
    }
}
