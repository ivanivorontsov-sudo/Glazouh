package org.glazouh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class SpeakerSegment(
    val startSeconds: Float,
    val endSeconds: Float,
    val speakerIndex: Int
)

/** Offline diarization. Audio stays in RAM and is discarded after analysis. */
class SpeakerDiarizationController(context: Context) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val samplesLock = Any()
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    private var samples = ShortArray(INITIAL_SAMPLES)
    private var sampleCount = 0
    private var truncated = false
    @Volatile private var recordingFailure: String? = null

    suspend fun start(onStatus: (String) -> Unit) = withContext(Dispatchers.IO) {
        check(appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Для разделения собеседников нужно разрешение на микрофон."
        }
        check(!running.get()) { "Запись разговора уже выполняется." }
        check(worker?.isAlive != true) { "Предыдущая запись ещё завершается." }
        onStatus("Проверяю модели разделения собеседников…")
        ensureModels(onStatus)
        synchronized(samplesLock) {
            sampleCount = 0
            truncated = false
        }
        recordingFailure = null
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "Телефон не поддерживает запись PCM 16 кГц." }
        val activeRecorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, BUFFER_SAMPLES * 4)
        )
        check(activeRecorder.state == AudioRecord.STATE_INITIALIZED) {
            activeRecorder.release()
            "Не удалось открыть микрофон."
        }
        try {
            activeRecorder.startRecording()
            check(activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Не удалось начать запись."
            }
            recorder = activeRecorder
            running.set(true)
            worker = Thread({
                val buffer = ShortArray(BUFFER_SAMPLES)
                try {
                    while (running.get()) {
                        val count = activeRecorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        if (!running.get()) break
                        if (count == AudioRecord.ERROR_DEAD_OBJECT) throw IOException("Микрофон был отключён системой.")
                        if (count < 0) throw IOException("Ошибка чтения микрофона: $count")
                        appendSamples(buffer, count)
                    }
                } catch (error: Exception) {
                    recordingFailure = error.localizedMessage ?: "Ошибка записи аудио."
                    running.set(false)
                } finally {
                    runCatching {
                        if (activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) activeRecorder.stop()
                    }
                    activeRecorder.release()
                    if (recorder === activeRecorder) recorder = null
                }
            }, "Glazouh-Diarization-Recorder").apply { isDaemon = true; start() }
            onStatus("Записываю разговор локально. Максимальная длительность — 3 минуты.")
        } catch (error: Exception) {
            running.set(false)
            runCatching { activeRecorder.release() }
            recorder = null
            throw error
        }
    }

    suspend fun stopAndAnalyze(onStatus: (String) -> Unit): List<SpeakerSegment> =
        withContext(Dispatchers.IO) {
            check(running.get() || recordingFailure != null) { "Сейчас нет активной записи." }
            running.set(false)
            recorder?.let { active ->
                runCatching {
                    if (active.recordingState == AudioRecord.RECORDSTATE_RECORDING) active.stop()
                }
            }
            worker?.join(5_000)
            check(worker?.isAlive != true) { "Запись ещё останавливается. Попробуйте снова через секунду." }
            recordingFailure?.let { failure ->
                synchronized(samplesLock) {
                    samples.fill(0)
                    sampleCount = 0
                }
                throw IOException(failure)
            }
            val captured: ShortArray
            val wasTruncated: Boolean
            synchronized(samplesLock) {
                captured = samples.copyOf(sampleCount)
                wasTruncated = truncated
                sampleCount = 0
                samples.fill(0)
                samples = ShortArray(INITIAL_SAMPLES)
            }
            if (captured.size < SAMPLE_RATE * MIN_SECONDS) {
                captured.fill(0)
                throw IOException("Запись слишком короткая. Нужно хотя бы $MIN_SECONDS секунд.")
            }
            onStatus("Анализирую голоса на устройстве…")
            val floatSamples = FloatArray(captured.size) { captured[it].toFloat() / 32768f }
            try {
                val models = ensureModels(onStatus)
                val diarizer = OfflineSpeakerDiarization(
                    config = OfflineSpeakerDiarizationConfig(
                        segmentation = OfflineSpeakerSegmentationModelConfig(
                            pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                                model = models.first.absolutePath,
                                windowShiftRatio = 0.1f
                            ),
                            numThreads = 2,
                            provider = "cpu"
                        ),
                        embedding = SpeakerEmbeddingExtractorConfig(
                            model = models.second.absolutePath,
                            numThreads = 2,
                            provider = "cpu"
                        ),
                        clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
                        minDurationOn = 0.3f,
                        minDurationOff = 0.5f
                    )
                )
                try {
                    check(diarizer.sampleRate() == SAMPLE_RATE) {
                        "Модель ожидает " + diarizer.sampleRate() + " Гц, запись выполнена с частотой " + SAMPLE_RATE + " Гц."
                    }
                    val result = diarizer.process(floatSamples)
                    val segments = result.map {
                        SpeakerSegment(it.start, it.end, it.speaker)
                    }.filter { it.endSeconds > it.startSeconds }
                    onStatus(
                        if (wasTruncated) "Анализ завершён. Обработаны первые 3 минуты записи."
                        else "Анализ завершён. Найдено голосовых фрагментов: " + segments.size + "."
                    )
                    segments
                } finally {
                    diarizer.release()
                }
            } finally {
                captured.fill(0)
                floatSamples.fill(0f)
            }
        }

    fun cancel() {
        running.set(false)
        recorder?.let { active ->
            runCatching {
                if (active.recordingState == AudioRecord.RECORDSTATE_RECORDING) active.stop()
            }
        }
        synchronized(samplesLock) {
            samples.fill(0)
            sampleCount = 0
        }
    }

    fun destroy() = cancel()

    private fun appendSamples(source: ShortArray, count: Int) {
        synchronized(samplesLock) {
            val remaining = MAX_SAMPLES - sampleCount
            val accepted = minOf(count, remaining)
            if (accepted <= 0) {
                truncated = true
                return
            }
            if (sampleCount + accepted > samples.size) {
                var newSize = samples.size
                while (newSize < sampleCount + accepted) newSize = minOf(MAX_SAMPLES, newSize * 2)
                samples = samples.copyOf(newSize)
            }
            source.copyInto(samples, sampleCount, 0, accepted)
            sampleCount += accepted
            if (accepted < count) truncated = true
        }
    }

    private fun ensureModels(onStatus: (String) -> Unit): Pair<File, File> {
        val directory = File(appContext.filesDir, "speech/speaker-diarization")
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку моделей." }
        val segmentation = File(directory, SEGMENTATION_FILENAME)
        val segmentationManifest = File(directory, SEGMENTATION_MANIFEST)
        val embedding = File(directory, EMBEDDING_FILENAME)
        if (!verifiedExtractedSegmentation(segmentation, segmentationManifest)) {
            onStatus("Скачиваю модель анализа голосовых фрагментов (~6,6 МБ)…")
            val archive = File(directory, "segmentation.tar.bz2")
            downloadVerified(SEGMENTATION_URL, archive, SEGMENTATION_SHA, SEGMENTATION_BYTES, SEGMENTATION_BYTES)
            try {
                extractSegmentation(archive, segmentation)
                val manifestContent = SEGMENTATION_SHA + "\n" + segmentation.length() + "\n" + sha256(segmentation)
                val manifestPart = File(directory, SEGMENTATION_MANIFEST + ".part")
                manifestPart.writeText(manifestContent)
                if (segmentationManifest.exists()) segmentationManifest.delete()
                check(manifestPart.renameTo(segmentationManifest)) { "Не удалось сохранить контрольную сумму модели сегментации." }
            } finally {
                archive.delete()
            }
        }
        if (!verified(embedding, EMBEDDING_SHA, EMBEDDING_BYTES)) {
            onStatus("Скачиваю модель голосовых признаков (~39,6 МБ)…")
            downloadVerified(EMBEDDING_URL, embedding, EMBEDDING_SHA, EMBEDDING_BYTES, EMBEDDING_BYTES)
        }
        return segmentation to embedding
    }

    private fun extractSegmentation(archive: File, target: File) {
        val temporary = File(target.parentFile, target.name + ".part")
        temporary.delete()
        var found = false
        try {
            BZip2CompressorInputStream(FileInputStream(archive)).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (entry.isDirectory) continue
                        if (entry.name == "sherpa-onnx-pyannote-segmentation-3-0/model.onnx") {
                            check(entry.size in 1..10_000_000) { "Неверный размер модели сегментации." }
                            temporary.outputStream().buffered().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= 10_000_000) { "Модель сегментации превышает лимит." }
                                    output.write(buffer, 0, count)
                                }
                            }
                            found = true
                            break
                        }
                    }
                }
            }
            check(found && temporary.isFile && temporary.length() > 0) {
                "В архиве не найдена ожидаемая модель сегментации."
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Не удалось сохранить модель сегментации." }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun verified(file: File, sha: String, expectedBytes: Long): Boolean =
        file.isFile && file.length() == expectedBytes &&
            runCatching { sha256(file) == sha }.getOrDefault(false)

    private fun verifiedExtractedSegmentation(model: File, manifest: File): Boolean {
        if (!model.isFile || model.length() !in 1..10_000_000 || !manifest.isFile) return false
        return runCatching {
            val fields = manifest.readLines()
            fields.size == 3 &&
                fields[0] == SEGMENTATION_SHA &&
                fields[1].toLong() == model.length() &&
                fields[2] == sha256(model)
        }.getOrDefault(false)
    }

    private fun downloadVerified(
        url: String, target: File, expectedSha: String, minBytes: Long, maxBytes: Long
    ) {
        val temporary = File(target.parentFile, target.name + ".part")
        temporary.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Glazouh-Android/0.3")
        }
        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Сервер модели ответил кодом " + connection.responseCode + "."
            }
            val declared = connection.contentLengthLong
            if (declared > 0) check(declared in minBytes..maxBytes) { "Неожиданный размер модели: $declared." }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= maxBytes) { "Загрузка модели превысила допустимый размер." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temporary.length() in minBytes..maxBytes) { "Загруженная модель имеет неверный размер." }
            check(sha256(temporary) == expectedSha) { "SHA-256 модели не совпал. Файл отклонён." }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Не удалось сохранить проверенную модель." }
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
        private const val BUFFER_SAMPLES = 4_096
        private const val MIN_SECONDS = 4
        private const val MAX_SECONDS = 180
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_SECONDS
        private const val INITIAL_SAMPLES = SAMPLE_RATE * 10
        private const val SEGMENTATION_FILENAME = "pyannote-segmentation.onnx"
        private const val SEGMENTATION_MANIFEST = "pyannote-segmentation.manifest"
        private const val SEGMENTATION_BYTES = 6_958_444L
        private const val SEGMENTATION_SHA = "24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488"
        private const val SEGMENTATION_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
        private const val EMBEDDING_FILENAME = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
        private const val EMBEDDING_BYTES = 39_593_761L
        private const val EMBEDDING_SHA = "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b"
        private const val EMBEDDING_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    }
}
