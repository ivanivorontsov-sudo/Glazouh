package org.glazouh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.SystemClock
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.components.containers.AudioData.AudioDataFormat
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class SoundEvent(val label: String, val score: Float)

class SoundEventMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val active = AtomicBoolean(false)
    private val preparing = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    @Volatile private var classifier: AudioClassifier? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var executor: ScheduledThreadPoolExecutor? = null

    suspend fun start(
        onStatus: (String) -> Unit,
        onEvents: (List<SoundEvent>) -> Unit
    ) = withContext(Dispatchers.IO) {
        check(preparing.compareAndSet(false, true)) { "Подготовка распознавания звуков уже выполняется." }
        cancelRequested.set(false)
        try {
            check(appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "Для распознавания окружающих звуков нужно разрешение на микрофон."
            }
            check(active.compareAndSet(false, true)) { "Распознавание звуков уже запущено." }
            onStatus("Проверяю офлайн-модель YAMNet…")
            val model = ensureModel()
            check(!cancelRequested.get()) { "Запуск распознавания звуков отменён." }

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(model.absolutePath)
                .setDelegate(Delegate.CPU)
                .build()
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.AUDIO_STREAM)
                .setScoreThreshold(SCORE_THRESHOLD)
                .setMaxResults(MAX_RESULTS)
                .setResultListener { result: AudioClassifierResult ->
                    val events = result.classifications
                        .firstOrNull()
                        ?.categories
                        .orEmpty()
                        .map { SoundEvent(SoundEventLabelsRu.translate(it.categoryName), it.score) }
                    onEvents(events)
                }
                .setErrorListener { error: RuntimeException ->
                    onStatus(error.localizedMessage ?: "Ошибка анализа звука.")
                }
                .build()

            val createdClassifier = AudioClassifier.createFromOptions(appContext, options)
            synchronized(lock) {
                if (cancelRequested.get()) {
                    createdClassifier.close()
                    throw IOException("Запуск распознавания звуков отменён.")
                }
                classifier = createdClassifier
            }

            val createdRecorder = createdClassifier.createAudioRecord(
                AudioFormat.CHANNEL_IN_DEFAULT,
                SAMPLE_RATE,
                BUFFER_SIZE_BYTES
            )
            check(createdRecorder.state == AudioRecord.STATE_INITIALIZED) {
                createdRecorder.release()
                "Не удалось инициализировать микрофон для анализа звуков."
            }
            synchronized(lock) {
                if (cancelRequested.get()) {
                    createdRecorder.release()
                    throw IOException("Запуск распознавания звуков отменён.")
                }
                recorder = createdRecorder
                createdRecorder.startRecording()
                check(createdRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Не удалось начать запись звука."
                }
                val pool = ScheduledThreadPoolExecutor(1)
                executor = pool
                pool.scheduleAtFixedRate(
                    { classifyNextWindow(onStatus) },
                    0L,
                    WINDOW_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )
            }
            onStatus("Слушаю окружающие звуки локально. YAMNet распознаёт 521 категорию.")
        } catch (error: Exception) {
            active.set(false)
            stopInternal()
            throw error
        } finally {
            preparing.set(false)
        }
    }

    fun stop() {
        cancelRequested.set(true)
        active.set(false)
        stopInternal()
    }

    fun destroy() = stop()

    private fun classifyNextWindow(onStatus: (String) -> Unit) {
        synchronized(lock) {
            if (!active.get()) return
            val currentClassifier = classifier ?: return
            val currentRecorder = recorder ?: return
            try {
                val audioData = AudioData.create(
                    AudioDataFormat.create(currentRecorder.format),
                    SAMPLE_RATE
                )
                audioData.load(currentRecorder)
                currentClassifier.classifyAsync(audioData, SystemClock.uptimeMillis())
            } catch (error: RuntimeException) {
                onStatus(error.localizedMessage ?: "Не удалось обработать аудиофрагмент.")
                active.set(false)
                stopInternal()
            }
        }
    }

    private fun stopInternal() {
        synchronized(lock) {
            executor?.shutdownNow()
            executor = null
            recorder?.let { activeRecorder ->
                runCatching {
                    if (activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        activeRecorder.stop()
                    }
                }
                runCatching { activeRecorder.release() }
            }
            recorder = null
            runCatching { classifier?.close() }
            classifier = null
        }
    }

    private fun ensureModel(): File {
        val directory = File(appContext.filesDir, "audio/yamnet")
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку аудиомодели." }
        val model = File(directory, MODEL_FILENAME)
        if (model.isFile && model.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES &&
            runCatching { sha256(model) == MODEL_SHA256 }.getOrDefault(false)
        ) return model

        val temporary = File(directory, MODEL_FILENAME + ".part")
        temporary.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Glazouh-Android/0.3")
        }
        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Сервер YAMNet ответил кодом " + connection.responseCode + "."
            }
            val declared = connection.contentLengthLong
            if (declared > 0) check(declared in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                "Неожиданный размер модели YAMNet: $declared."
            }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        check(!cancelRequested.get()) { "Загрузка YAMNet отменена." }
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_MODEL_BYTES) { "Модель YAMNet превысила допустимый размер." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temporary.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                "Загруженная модель YAMNet имеет неверный размер."
            }
            check(sha256(temporary) == MODEL_SHA256) {
                "SHA-256 модели YAMNet не совпал. Файл отклонён."
            }
            if (model.exists()) model.delete()
            check(temporary.renameTo(model)) { "Не удалось сохранить модель YAMNet." }
            return model
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val WINDOW_SAMPLES = 15_600
        private const val BUFFER_SIZE_BYTES = WINDOW_SAMPLES * Float.SIZE_BYTES * 2
        private const val WINDOW_INTERVAL_MS = 488L
        private const val SCORE_THRESHOLD = 0.35f
        private const val MAX_RESULTS = 5
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val MAX_MODEL_BYTES = 15_000_000L
        private const val MODEL_FILENAME = "yamnet.tflite"
        private const val MODEL_SHA256 = "4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf"
        private const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite"
    }
}
