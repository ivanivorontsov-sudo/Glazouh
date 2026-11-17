package org.glazouh.app

import android.content.Context

/**
 * API-compatible placeholder for Lite.
 * The native speech runtime and T-one model are shipped only in Full.
 */
class SherpaRussianCaptionController(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun start(
        onText: (text: String, isFinal: Boolean) -> Unit,
        onStatus: (String) -> Unit
    ) {
        onStatus("Русский движок T-one доступен в полной сборке ГлазУха.")
    }

    fun stop(onStatus: ((String) -> Unit)? = null) {
        onStatus?.invoke("Русский движок T-one доступен в полной сборке ГлазУха.")
    }

    fun destroy() = Unit
}
