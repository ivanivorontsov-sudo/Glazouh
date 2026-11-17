package org.glazouh.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Uses the Android on-device speech service only; there is no network fallback. */
class OnDeviceCaptionController(
    private val context: Context,
    private val onText: (text: String, isFinal: Boolean) -> Unit,
    private val onStatus: (String) -> Unit
) : RecognitionListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var languageTag = "ru-RU"
    private val restart = Runnable { startListeningSafely() }

    fun start(language: String = "ru-RU") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onStatus("Для офлайн-субтитров нужен Android 12 или новее.")
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onStatus("На этом телефоне нет доступного офлайн-распознавания речи.")
            return
        }
        stop()
        languageTag = language
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                it.setRecognitionListener(this)
            }
            isRunning = true
            onStatus("Слушаю офлайн. Интернет не используется.")
            startListeningSafely()
        } catch (error: Exception) {
            isRunning = false
            onStatus("Не удалось запустить офлайн-распознавание: \${error.localizedMessage ?: "ошибка"}")
            destroyRecognizer()
        }
    }

    fun stop() {
        isRunning = false
        mainHandler.removeCallbacks(restart)
        runCatching { recognizer?.cancel() }
        destroyRecognizer()
        onStatus("Субтитры остановлены")
    }

    fun destroy() = stop()

    private fun startListeningSafely() {
        if (!isRunning) return
        val current = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { current.startListening(intent) }
            .onFailure {
                onStatus("Офлайн-распознавание не запустилось: \${it.localizedMessage ?: "ошибка"}")
            }
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    override fun onPartialResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) onText(text, false)
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) onText(text, true)
        if (isRunning) mainHandler.postDelayed(restart, 250L)
    }

    override fun onError(error: Int) {
        if (!isRunning) return
        val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        if (recoverable) {
            onStatus("Продолжаю слушать офлайн…")
            mainHandler.removeCallbacks(restart)
            mainHandler.postDelayed(restart, 500L)
        } else {
            isRunning = false
            onStatus("Распознавание остановлено (код ошибки $error).")
            destroyRecognizer()
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = onStatus("Слушаю офлайн…")
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
