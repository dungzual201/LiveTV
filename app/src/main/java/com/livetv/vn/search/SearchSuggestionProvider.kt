package com.livetv.vn.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import java.util.Calendar
import kotlinx.serialization.json.Json

class SearchSuggestionProvider : ContentProvider() {

    private lateinit var index: SearchIndexStore

    override fun onCreate(): Boolean {
        val context = context ?: return false
        index = SearchIndexStore(
            context.applicationContext,
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (matcher.match(uri) != MATCH_SEARCH) return null
        val rawQuery = when {
            uri.pathSegments.size > 1 -> Uri.decode(uri.pathSegments[1])
            !selectionArgs.isNullOrEmpty() -> Uri.decode(selectionArgs[0])
            !selection.isNullOrBlank() && selection.trim() != "?" -> selection.trim()
            else -> uri.getQueryParameter("q").orEmpty()
        }
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 20) ?: 12
        val results = index.search(rawQuery, limit)
        Log.d("LiveTvSearch", "query=$rawQuery results=${results.size}")
        val cursor = MatrixCursor(COLUMNS, results.size)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        results.forEachIndexed { position, entry ->
            val intentData = "content://android.media.tv/channel"
            val subtitle = buildString {
                append("LiveTV · Kênh ${entry.number.toString().padStart(3, '0')}")
                entry.quality?.let {
                    append(" · ")
                    append(it)
                }
            }
            cursor.addRow(
                arrayOf<Any?>(
                    position.toLong(),
                    entry.name,
                    subtitle,
                    entry.logo,
                    year.toString(),
                    ONE_HOUR_MS,
                    "video/*",
                    1,
                    Intent.ACTION_VIEW,
                    intentData,
                    entry.id,
                ),
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String? =
        if (matcher.match(uri) == MATCH_SEARCH) {
            "vnd.android.cursor.dir/vnd.android.search.suggest"
        } else {
            null
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Không hỗ trợ insert")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Không hỗ trợ delete")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Không hỗ trợ update")

    private companion object {
        const val AUTHORITY = "com.livetv.vn.search"
        const val MATCH_SEARCH = 1
        const val ONE_HOUR_MS = 60L * 60L * 1000L

        val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "search_suggest_query", MATCH_SEARCH)
            addURI(AUTHORITY, "search_suggest_query/*", MATCH_SEARCH)
        }

        val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_DURATION,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_IS_LIVE,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
        )
    }
}
