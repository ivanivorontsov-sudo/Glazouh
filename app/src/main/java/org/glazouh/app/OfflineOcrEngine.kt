package org.glazouh.app

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tesseract OCR adapter. Image processing stays on the device. */
class OfflineOcrEngine(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap, onStatus: (String) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val dataRoot = OcrModelStore(context).ensureRussianAndEnglish(onStatus)
            val tesseract = TessBaseAPI()
            try {
                check(tesseract.init(dataRoot.absolutePath, "rus+eng")) {
                    "Не удалось запустить распознавание. Проверьте языковые модели."
                }
                tesseract.setImage(bitmap)
                tesseract.getUTF8Text().orEmpty().trim()
            } finally {
                tesseract.recycle()
            }
        }
}
