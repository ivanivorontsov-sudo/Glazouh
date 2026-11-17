package org.glazouh.app

import android.content.Context

/** Diarization is available only in the Full variant. */
class SpeakerDiarizationController(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun start(onStatus: (String) -> Unit) {
        onStatus("Разделение собеседников доступно только в полной версии ГлазУха.")
    }

    suspend fun stopAndAnalyze(onStatus: (String) -> Unit): List<SpeakerSegment> {
        onStatus("Разделение собеседников доступно только в полной версии ГлазУха.")
        return emptyList()
    }

    fun cancel() = Unit
    fun destroy() = Unit
}

data class SpeakerSegment(
    val startSeconds: Float,
    val endSeconds: Float,
    val speakerIndex: Int
)
