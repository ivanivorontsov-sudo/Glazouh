package org.glazouh.app

import android.content.Context

/** Environmental sound recognition is available only in the Full variant. */
class SoundEventMonitor(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun start(
        onStatus: (String) -> Unit,
        onEvents: (List<SoundEvent>) -> Unit
    ) {
        onStatus("Распознавание окружающих звуков доступно только в полной версии ГлазУха.")
        onEvents(emptyList())
    }

    fun stop() = Unit
    fun destroy() = Unit
}

data class SoundEvent(val label: String, val score: Float)
