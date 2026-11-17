package org.glazouh.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads official Tesseract fast models into app-private storage. */
class OcrModelStore(context: Context) {
    private val dataRoot = File(context.filesDir, "tesseract")
    private val tessdataDir = File(dataRoot, "tessdata")

    suspend fun ensureRussianAndEnglish(onStatus: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            check(tessdataDir.exists() || tessdataDir.mkdirs()) { "Не удалось создать папку моделей" }
            listOf("rus", "eng").forEach { language ->
                val target = File(tessdataDir, "$language.traineddata")
                if (target.length() < MIN_MODEL_BYTES) {
                    onStatus("Загружаю офлайн-модель: $language")
                    downloadModel(language, target)
                }
            }
            dataRoot
        }

    private fun downloadModel(language: String, target: File) {
        require(language in SUPPORTED_LANGUAGES)
        val url = URL("$MODEL_BASE_URL/$language.traineddata")
        val temporary = File(target.parentFile, "${target.name}.part")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Glazouh-Android/0.1")
        }
        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Сервер языковой модели ответил: ${connection.responseCode}"
            }
            val declaredLength = connection.contentLengthLong
            check(declaredLength in MIN_MODEL_BYTES..MAX_MODEL_BYTES) { "Неожиданный размер модели" }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(temporary.length() >= MIN_MODEL_BYTES && temporary.length() <= MAX_MODEL_BYTES) {
                "Загруженная модель повреждена или имеет неверный размер"
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Не удалось сохранить языковую модель" }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MODEL_BASE_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main"
        private const val MIN_MODEL_BYTES = 100_000L
        private const val MAX_MODEL_BYTES = 40L * 1024L * 1024L
        private val SUPPORTED_LANGUAGES = setOf("rus", "eng")
    }
}
