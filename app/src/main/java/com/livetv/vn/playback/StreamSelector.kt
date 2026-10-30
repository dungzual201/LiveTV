package com.livetv.vn.playback

import com.livetv.vn.data.model.QualityMode
import com.livetv.vn.data.model.StreamVariant
import com.livetv.vn.data.store.HealthStore

object StreamSelector {

    fun order(
        streams: List<StreamVariant>,
        health: HealthStore,
        mode: QualityMode,
    ): List<StreamVariant> {
        if (streams.size < 2) return streams.distinctBy { it.url }
        fun alive(stream: StreamVariant): Boolean = health.score(stream.url) > DEAD_THRESHOLD

        val comparator = when (mode) {
            QualityMode.HIGH -> compareByDescending<StreamVariant> { alive(it) }
                .thenByDescending { it.qualityScore }
                .thenByDescending { health.score(it.url) }
                .thenByDescending { it.sourcePriority }
            QualityMode.SAVER -> compareByDescending<StreamVariant> { alive(it) }
                .thenByDescending { health.score(it.url) }
                .thenBy { it.qualityScore }
                .thenByDescending { it.sourcePriority }
            QualityMode.AUTO -> compareByDescending<StreamVariant> { alive(it) }
                .thenByDescending { health.score(it.url) + it.qualityScore * 0.45 }
                .thenByDescending { it.sourcePriority }
        }
        return streams.distinctBy { it.url }.sortedWith(comparator)
    }

    private const val DEAD_THRESHOLD = -1.5
}
