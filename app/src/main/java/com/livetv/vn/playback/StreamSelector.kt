package com.livetv.vn.playback

import com.livetv.vn.data.model.QualityMode
import com.livetv.vn.data.model.StreamVariant
import com.livetv.vn.data.store.HealthStore

object StreamSelector {

    fun order(
        streams: List<StreamVariant>,
        health: HealthStore,
        mode: QualityMode,
        isVtv: Boolean = false,
    ): List<StreamVariant> {
        if (streams.size < 2) return streams.distinctBy { it.url }
        fun alive(stream: StreamVariant): Boolean = health.score(stream.url) > DEAD_THRESHOLD

        val comparator = when (mode) {
            QualityMode.HIGH -> compareByDescending<StreamVariant> { alive(it) }
                .thenByDescending { it.qualityScore }
                .thenByDescending { fpsRank(it) }
                .thenByDescending { officialRank(it) }
                .thenByDescending { health.score(it.url) }
                .thenByDescending { it.sourcePriority }
            QualityMode.SAVER -> compareByDescending<StreamVariant> { alive(it) }
                .thenByDescending { health.score(it.url) }
                .thenBy { it.qualityScore }
                .thenByDescending { it.sourcePriority }
            QualityMode.AUTO -> if (isVtv) {
                compareByDescending<StreamVariant> { alive(it) }
                    .thenByDescending { it.qualityScore }
                    .thenByDescending { fpsRank(it) }
                    .thenByDescending { officialRank(it) }
                    .thenByDescending { it.sourcePriority }
                    .thenByDescending { health.score(it.url) }
            } else {
                compareByDescending<StreamVariant> { alive(it) }
                    .thenByDescending {
                        health.score(it.url) + it.qualityScore * 0.45 + fpsRank(it) * 0.35
                    }
                    .thenByDescending { it.sourcePriority }
            }
        }
        return streams.distinctBy { it.url }.sortedWith(comparator)
    }

    private fun fpsRank(stream: StreamVariant): Int = when {
        (stream.fps ?: 0) >= 60 -> 2
        (stream.fps ?: 0) >= 50 -> 1
        else -> 0
    }

    private fun officialRank(stream: StreamVariant): Int =
        if (stream.sourceId == "vtvgo-built-in") 1 else 0

    private const val DEAD_THRESHOLD = -1.5
}
