package org.glazouh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineToneCtcModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional Russian streaming ASR based on the T-one model and sherpa-onnx.
 * The model is fetched only after the user selects this engine, hash-verified,
 * stored in app-private files, and then used locally without network access.
 */
class SherpaRussianCaptionController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preparing = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null

    suspend fun start(
        onText: (text: String, isFinal: Boolean) -> Unit,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            report(onStatus, "Для офлайн-распознавания речи нужно разрешение на микрофон.")
            return@withContext
        }
        if (running.get()) {
            report(onStatus, "Офлайн-распознавание уже работает.")
            return@withContext
        }
        if (!preparing.compareAndSet(false, true)) {
            report(onStatus, "Подготовка русской модели уже выполняется.")
            return@withContext
        }

        worker?.let { previous ->
            if (previous.isAlive) previous.join(1_000)
            check(!previous.isAlive) { "Предыдущее распознавание ещё завершает работу." }
        }
        cancelRequested.set(false)
        var recognizer: OnlineRecognizer? = null
        var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
        var recorder: AudioRecord? = null
        try {
            report(onStatus, "Проверяю русскую офлайн-модель T-one…")
            val files = ensureModelFiles { message -> report(onStatus, message) }
            check(!cancelRequested.get()) { "Подготовка остановлена." }

            report(onStatus, "Загружаю русскую модель в память телефона…")
            val modelConfig = OnlineModelConfig(
                toneCtc = OnlineToneCtcModelConfig(model = files.first.absolutePath),
                tokens = files.second.absolutePath,
                numThreads = 2,
                provider = "cpu"
            )
            val activeRecognizer = OnlineRecognizer(
                config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = MODEL_SAMPLE_RATE, featureDim = 80),
                    modelConfig = modelConfig,
                    endpointConfig = EndpointConfig(
                        rule1 = EndpointRule(false, 1.0f, 0.0f),
                        rule2 = EndpointRule(true, 0.7f, 0.0f),
                        rule3 = EndpointRule(false, 0.0f, 15.0f)
                    ),
                    enableEndpoint = true
                )
            )
            recognizer = activeRecognizer
            val activeStream = activeRecognizer.createStream()
            stream = activeStream

            val minBufferBytes = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBufferBytes > 0) {
                "Телефон не поддерживает выбранный формат записи микрофона."
            }
            val activeRecorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferBytes * 2, INPUT_BUFFER_SAMPLES * 2 * 2)
            )
            recorder = activeRecorder
            check(activeRecorder.state == AudioRecord.STATE_INITIALIZED) {
                "Не удалось открыть микрофон для офлайн-распознавания."
            }
            check(!cancelRequested.get()) { "Подготовка остановлена." }

            activeRecorder.startRecording()
            check(activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Не удалось начать запись с микрофона."
            }
            running.set(true)
            this.activeRecorder = activeRecorder

            val thread = Thread(
                {
                    runRecognitionLoop(activeRecorder, activeRecognizer, activeStream, onText, onStatus)
                },
                "Glazouh-TOne-ASR"
            ).apply { isDaemon = true }
            worker = thread
            thread.start()
            report(onStatus, "Слушаю на устройстве. Русская модель работает без облака.")
            // Ownership of the native recognizer and AudioRecord moved to the worker.
            recognizer = null
            stream = null
            recorder = null
        } catch (error: Exception) {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { stream?.release() }
            runCatching { recognizer?.release() }
            running.set(false)
            activeRecorder = null
            report(onStatus, error.localizedMessage ?: "Не удалось запустить русское офлайн-распознавание.")
            throw error
        } finally {
            preparing.set(false)
        }
    }

    fun stop(onStatus: ((String) -> Unit)? = null) {
        cancelRequested.set(true)
        if (running.compareAndSet(true, false)) {
            val recorder = activeRecorder
            if (recorder != null) {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                }
            }
            if (onStatus != null) report(onStatus, "Останавливаю русские офлайн-субтитры…")
        } else if (preparing.get() && onStatus != null) {
            report(onStatus, "Отменяю загрузку/подготовку русской модели…")
        } else if (onStatus != null) {
            report(onStatus, "Русские офлайн-субтитры остановлены.")
        }
    }

    fun destroy() {
        stop()
    }

    private fun runRecognitionLoop(
        recorder: AudioRecord,
        recognizer: OnlineRecognizer,
        stream: com.k2fsa.sherpa.onnx.OnlineStream,
        onText: (String, Boolean) -> Unit,
        onStatus: (String) -> Unit
    ) {
        val pcm = ShortArray(INPUT_BUFFER_SAMPLES)
        var lastPartial = ""
        var failureMessage: String? = null
        try {
            while (running.get()) {
                val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (!running.get()) break
                if (count == AudioRecord.ERROR_DEAD_OBJECT) {
                    throw IOException("Микрофон был отключён системой.")
                }
                if (count < 0) {
                    throw IOException("Ошибка чтения микрофона: $count")
                }
                val samples = TOneAudioResampler.pcm16kTo8k(pcm, count)
                if (samples.isEmpty()) continue

                stream.acceptWaveform(samples, MODEL_SAMPLE_RATE)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val text = recognizer.getResult(stream).text.trim()
                if (recognizer.isEndpoint(stream)) {
                    if (text.isNotBlank()) reportText(onText, text, true)
                    recognizer.reset(stream)
                    lastPartial = ""
                } else if (text.isNotBlank() && text != lastPartial) {
                    reportText(onText, text, false)
                    lastPartial = text
                }
            }

            stream.inputFinished()
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            val finalText = recognizer.getResult(stream).text.trim()
            if (finalText.isNotBlank() && finalText != lastPartial) {
                reportText(onText, finalText, true)
            }
        } catch (error: Exception) {
            failureMessage = error.localizedMessage ?: "Ошибка русской модели распознавания речи."
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            }
            runCatching { recorder.release() }
            runCatching { stream.release() }
            runCatching { recognizer.release() }
            running.set(false)
            if (activeRecorder === recorder) activeRecorder = null
            if (worker === Thread.currentThread()) worker = null
            report(onStatus, failureMessage ?: "Русские офлайн-субтитры остановлены.")
        }
    }

    private fun ensureModelFiles(onStatus: (String) -> Unit): Pair<File, File> {
        val directory = File(appContext.filesDir, "speech/t-one-russian")
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать закрытую папку для русской модели."
        }
        val model = File(directory, MODEL_FILENAME)
        val tokens = File(directory, TOKENS_FILENAME)

        if (!isVerified(model, MODEL_SHA256, MIN_MODEL_BYTES, MAX_MODEL_BYTES)) {
            report(onStatus, "Скачиваю русскую модель T-one (~138 МБ). Это делается один раз…")
            downloadVerified(
                MODEL_URL, model, MODEL_SHA256,
                MIN_MODEL_BYTES, MAX_MODEL_BYTES
            )
        }
        if (!isVerified(tokens, TOKENS_SHA256, TOKENS_BYTES, TOKENS_BYTES)) {
            report(onStatus, "Подготавливаю словарь русской модели…")
            copyVerifiedTokens(tokens)
        }
        return model to tokens
    }

    private fun isVerified(file: File, hash: String, min: Long, max: Long): Boolean {
        if (!file.isFile || file.length() !in min..max) return false
        return runCatching { sha256(file) == hash }.getOrDefault(false)
    }

    private fun downloadVerified(
        url: String,
        target: File,
        expectedSha256: String,
        minBytes: Long,
        maxBytes: Long
    ) {
        val temporary = File(target.parentFile, "${target.name}.part")
        temporary.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Glazouh-Android/0.2")
        }
        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Сервер русской модели ответил кодом ${connection.responseCode}."
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0) {
                check(declaredLength in minBytes..maxBytes) {
                    "Неожиданный размер файла русской модели: $declaredLength."
                }
            }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        check(!cancelRequested.get()) { "Загрузка русской модели отменена." }
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= maxBytes) { "Файл русской модели превышает допустимый размер." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(temporary.length() in minBytes..maxBytes) {
                "Загруженный файл русской модели имеет неверный размер."
            }
            check(sha256(temporary) == expectedSha256) {
                "Контрольная сумма русской модели не совпала. Файл отклонён."
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) {
                "Не удалось сохранить проверенную русскую модель."
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun copyVerifiedTokens(target: File) {
        val temporary = File(target.parentFile, "${target.name}.part")
        temporary.delete()
        try {
            appContext.assets.open(TOKENS_ASSET).use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(temporary.length() == TOKENS_BYTES && sha256(temporary) == TOKENS_SHA256) {
                "Встроенный словарь русской модели повреждён."
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Не удалось сохранить словарь русской модели." }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun report(callback: (String) -> Unit, message: String) {
        mainHandler.post { callback(message) }
    }

    private fun reportText(callback: (String, Boolean) -> Unit, text: String, isFinal: Boolean) {
        mainHandler.post { callback(text, isFinal) }
    }

    companion object {
        private const val MODEL_FILENAME = "model.onnx"
        private const val TOKENS_FILENAME = "tokens.txt"
        private const val TOKENS_ASSET = "sherpa-t-one/tokens.txt"

        private const val MODEL_URL =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08/resolve/222f592/model.onnx"
        private const val MODEL_SHA256 =
            "5ded080e2a6c86ecc11bcb0902d77524eb3e8b0844cb0c0754347f5aafb4dabc"

        private const val TOKENS_BYTES = 202L
        private const val TOKENS_SHA256 =
            "27f7b3ba2096c572375fba1a6b29af1f80d86e08a329940612908112695f97e0"

        private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 180L * 1024L * 1024L
        private const val INPUT_SAMPLE_RATE = 16_000
        private const val MODEL_SAMPLE_RATE = 8_000
        private const val INPUT_BUFFER_SAMPLES = 1_600
    }
}

internal object TOneAudioResampler {
    /** Converts mono PCM16 at 16 kHz to normalized mono float samples at 8 kHz. */
    fun pcm16kTo8k(input: ShortArray, validSamples: Int = input.size): FloatArray {
        require(validSamples in 0..input.size) { "validSamples is outside the input buffer" }
        val outputCount = validSamples / 2
        return FloatArray(outputCount) { index ->
            val first = input[index * 2].toInt()
            val second = input[index * 2 + 1].toInt()
            ((first + second) / 2f / 32768f).coerceIn(-1f, 1f)
        }
    }
}
