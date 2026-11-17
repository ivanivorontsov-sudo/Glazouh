package org.glazouh.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Optional, on-device COCO object detection.
 *
 * The model is downloaded once to app-private storage, verified against a pinned
 * SHA-256 digest, and then memory-mapped. The photo is never uploaded.
 */
class OfflineObjectDetector(private val context: Context) {
    data class Detection(
        val label: String,
        val confidence: Float,
        val bounds: RectF
    )

    private val mutex = Mutex()
    private val modelFile = File(File(context.filesDir, "models"), MODEL_FILENAME)
    private var objectDetector: ObjectDetector? = null
    private var mappedModel: MappedByteBuffer? = null

    suspend fun detect(
        bitmap: Bitmap,
        onStatus: (String) -> Unit = {}
    ): List<Detection> = withContext(Dispatchers.Default) {
        require(!bitmap.isRecycled) { "Изображение уже освобождено" }

        mutex.withLock {
            val detector = objectDetector ?: createDetector(onStatus)
            val result = detector.detect(BitmapImageBuilder(bitmap).build())

            result.detections().mapNotNull { detection ->
                val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                val label = category.categoryName()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val box = detection.boundingBox()
                Detection(
                    label = label,
                    confidence = category.score(),
                    bounds = RectF(
                        (box.left / bitmap.width).coerceIn(0f, 1f),
                        (box.top / bitmap.height).coerceIn(0f, 1f),
                        (box.right / bitmap.width).coerceIn(0f, 1f),
                        (box.bottom / bitmap.height).coerceIn(0f, 1f)
                    )
                )
            }.sortedByDescending { it.confidence }
        }
    }

    private suspend fun createDetector(onStatus: (String) -> Unit): ObjectDetector {
        report(onStatus, "Проверяю локальную модель распознавания предметов…")
        val validModel = withContext(Dispatchers.IO) { ensureModel() }
        report(onStatus, "Подготавливаю модель на устройстве…")

        val buffer = withContext(Dispatchers.IO) {
            FileChannel.open(validModel.toPath(), StandardOpenOption.READ).use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size())
            }
        }

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(SCORE_THRESHOLD)
            .setMaxResults(MAX_RESULTS)
            .build()

        return ObjectDetector.createFromOptions(context, options).also {
            mappedModel = buffer
            objectDetector = it
        }
    }

    private suspend fun ensureModel(): File {
        modelFile.parentFile?.let { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "Не удалось создать папку для модели"
            }
        }

        if (modelFile.exists() && sha256(modelFile) == MODEL_SHA256) {
            return modelFile
        }

        val temporary = File(modelFile.parentFile, "$MODEL_FILENAME.part")
        temporary.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Glazouh-Android/0.2")
        }

        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Сервер модели ответил кодом ${connection.responseCode}"
            }
            val declaredSize = connection.contentLengthLong
            check(declaredSize in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                "Неожиданный размер модели: $declaredSize"
            }

            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_MODEL_BYTES) { "Файл модели превышает допустимый размер" }
                        output.write(buffer, 0, read)
                    }
                }
            }

            check(temporary.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                "Загруженная модель имеет неверный размер"
            }
            check(sha256(temporary) == MODEL_SHA256) {
                "Контрольная сумма модели не совпала. Файл отклонён."
            }

            if (modelFile.exists()) modelFile.delete()
            check(temporary.renameTo(modelFile)) {
                "Не удалось сохранить проверенную модель"
            }
            return modelFile
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
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

    private suspend fun report(onStatus: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main) { onStatus(message) }
    }

    suspend fun close() {
        mutex.withLock {
            objectDetector?.close()
            objectDetector = null
            mappedModel = null
        }
    }

    companion object {
        private const val MODEL_FILENAME = "efficientdet_lite0.tflite"
        private const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite"
        private const val MODEL_SHA256 =
            "40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58"
        private const val MIN_MODEL_BYTES = 5L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 25L * 1024L * 1024L
        private const val SCORE_THRESHOLD = 0.45f
        private const val MAX_RESULTS = 8
    }
}
