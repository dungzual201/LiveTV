package com.livetv.vn.voice

import android.app.SearchManager
import android.content.Intent
import com.livetv.vn.ui.MainActivity

object VoiceIntents {
    const val EXTRA_CHANNEL_ID = "com.livetv.vn.extra.CHANNEL_ID"
    const val EXTRA_SEARCH_QUERY = "com.livetv.vn.extra.SEARCH_QUERY"

    fun channelIntent(channelId: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setClassName("com.livetv.vn", MainActivity::class.java.name)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    fun searchIntent(query: String): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setClassName("com.livetv.vn", MainActivity::class.java.name)
            putExtra(EXTRA_SEARCH_QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    fun channelIdFrom(intent: Intent): String? {
        val uri = intent.data
        val fromTv = uri
            ?.takeIf { it.scheme == "content" && it.authority == "android.media.tv" }
            ?.pathSegments
            ?.getOrNull(1)
        val fromData = uri?.let {
            if (it.host == "watch") it.getQueryParameter("channel") else null
        }
        val fromExtra = intent.getStringExtra("android.media.tv.extra.CHANNEL_ID")
            ?: intent.getStringExtra("channel_id")
            ?: intent.getStringExtra("media_id")
        val fromLongExtra = listOf(
            "android.media.tv.extra.CHANNEL_ID",
            "channel_id",
            "media_id",
        ).firstNotNullOfOrNull { key ->
            intent.getLongExtra(key, Long.MIN_VALUE)
                .takeIf { it != Long.MIN_VALUE }
                ?.toString()
        }
        return fromTv ?: fromData ?: fromExtra ?: fromLongExtra
            ?: intent.getStringExtra(EXTRA_CHANNEL_ID)
    }

    fun queryFrom(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_SEARCH_QUERY)
            ?: intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra("android.app.search.query")
    }
}
