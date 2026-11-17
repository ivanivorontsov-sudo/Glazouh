package org.glazouh.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Locale

private val GlazouhColors = darkColorScheme(
    primary = Color(0xFFFFFF00),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF001014),
    background = Color(0xFF080808),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B)
)

private enum class MainMode { HOME, READER, CAPTIONS }

@Composable
fun GlazouhApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(MainMode.HOME) }
    var status by remember { mutableStateOf("Готов помочь. Выберите режим.") }
    var recognizedText by remember { mutableStateOf("") }
    var isReading by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf(emptyList<String>()) }
    var partialCaption by remember { mutableStateOf("") }
    var captionLanguage by remember { mutableStateOf("ru-RU") }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember(context) {
        TextToSpeech(context) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
        }
    }
    LaunchedEffect(ttsReady) {
        if (ttsReady) tts.setLanguage(Locale.forLanguageTag("ru-RU"))
        if (ttsReady) tts.setSpeechRate(0.92f)
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val captionController = remember(context) {
        OnDeviceCaptionController(
            context = context,
            onText = { text, isFinal ->
                if (isFinal) {
                    transcript = (transcript + text).takeLast(30)
                    partialCaption = ""
                } else {
                    partialCaption = text
                }
            },
            onStatus = { status = it }
        )
    }
    DisposableEffect(captionController) {
        onDispose { captionController.destroy() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captionController.start(captionLanguage)
        else status = "Для субтитров нужно разрешение на микрофон."
    }

    MaterialTheme(colorScheme = GlazouhColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "ГлазУх",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Доступность без обязательного облака", fontSize = 19.sp)
                Text(
                    status,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                when (mode) {
                    MainMode.HOME -> {
                        LargeActionButton("Прочитать текст камерой") { mode = MainMode.READER }
                        LargeActionButton("Живые офлайн-субтитры") { mode = MainMode.CAPTIONS }
                        Text(
                            "Камера и распознавание текста работают на телефоне. " +
                                "При первом запуске потребуется скачать языковые модели.",
                            fontSize = 16.sp
                        )
                        Text(
                            "Субтитры используют системный офлайн-движок Android, если он доступен. " +
                                "Распознавание говорящих пока не реализовано.",
                            fontSize = 16.sp
                        )
                    }

                    MainMode.READER -> {
                        LargeActionButton("Назад") { mode = MainMode.HOME }
                        Text(
                            "Наведите заднюю камеру на упаковку, документ или табличку. " +
                                "Затем нажмите большую кнопку съёмки.",
                            fontSize = 18.sp
                        )
                        CameraCapture(
                            onCaptured = { bitmap ->
                                scope.launch {
                                    isReading = true
                                    status = "Распознаю текст на устройстве…"
                                    recognizedText = ""
                                    try {
                                        val result = OfflineOcrEngine(context).recognize(bitmap) {
                                            status = it
                                        }
                                        recognizedText = result
                                        if (result.isBlank()) {
                                            status = "Текст не найден. Приблизьте камеру и улучшите освещение."
                                        } else {
                                            status = "Готово. Прочитанный текст показан ниже."
                                            if (ttsReady) {
                                                speakLongText(tts, result, "glazouh-ocr")
                                            } else {
                                                status = "Текст распознан; голосовой движок пока не готов."
                                            }
                                        }
                                    } catch (error: Exception) {
                                        status = error.localizedMessage ?: "Не удалось распознать текст."
                                    } finally {
                                        bitmap.recycle()
                                        isReading = false
                                    }
                                }
                            },
                            onStatus = { status = it }
                        )
                        if (isReading) Text("Обработка…", fontSize = 20.sp)
                        if (recognizedText.isNotBlank()) {
                            Text("Распознанный текст", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(
                                recognizedText,
                                fontSize = 20.sp,
                                modifier = Modifier.fillMaxWidth().semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                            LargeActionButton("Прочитать ещё раз") {
                                if (ttsReady) {
                                    speakLongText(tts, recognizedText, "glazouh-repeat")
                                    status = "Читаю распознанный текст."
                                }
                            }
                            LargeActionButton("Остановить озвучивание") {
                                tts.stop()
                                status = "Озвучивание остановлено."
                            }
                            LargeActionButton("Скопировать текст") {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ГлазУх", recognizedText))
                                status = "Текст скопирован в буфер обмена."
                            }
                        }
                        Text(
                            "Важно: OCR может ошибаться в цифрах, дозировках и сроках годности. " +
                                "Не используйте результат как единственное основание для приёма лекарства.",
                            color = Color(0xFFFFD166),
                            fontSize = 15.sp
                        )
                    }

                    MainMode.CAPTIONS -> {
                        LargeActionButton("Назад") {
                            captionController.stop()
                            mode = MainMode.HOME
                        }
                        Text(
                            "Речь распознаётся системным офлайн-сервисом Android. " +
                                "Если на устройстве нет локальной модели выбранного языка, режим не запустится.",
                            fontSize = 17.sp
                        )
                        LargeActionButton(
                            if (captionLanguage == "ru-RU") {
                                "Язык: Русский · нажмите для English"
                            } else {
                                "Language: English · нажмите для Русский"
                            }
                        ) {
                            captionController.stop()
                            captionLanguage = if (captionLanguage == "ru-RU") "en-US" else "ru-RU"
                            partialCaption = ""
                            status = if (captionLanguage == "ru-RU") {
                                "Выбран русский язык. Нажмите «Начать субтитры»."
                            } else {
                                "Выбран английский язык. Нажмите «Начать субтитры»."
                            }
                        }
                        LargeActionButton("Начать субтитры") {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) captionController.start(captionLanguage)
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        LargeActionButton("Остановить") { captionController.stop() }
                        LargeActionButton("Очистить стенограмму") {
                            transcript = emptyList()
                            partialCaption = ""
                            status = "Стенограмма очищена."
                        }
                        Text("Последняя фраза", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(
                            partialCaption.ifBlank { "Пока тишина." },
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text("Стенограмма", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        transcript.forEachIndexed { index, line ->
                            Text(
                                "${index + 1}. $line",
                                fontSize = 19.sp,
                                modifier = Modifier.fillMaxWidth().semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                        }
                        if (transcript.isEmpty()) Text("Здесь появятся распознанные фразы.", fontSize = 17.sp)
                        Text(
                            "Кто именно говорит, пока не определяется. " +
                                "Разделение собеседников будет отдельным офлайн-модулем.",
                            fontSize = 15.sp,
                            color = Color(0xFFFFD166)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Ранняя альфа · Не заменяет помощь сопровождающего в опасных ситуациях.",
                    fontSize = 13.sp,
                    color = Color(0xFFBBBBBB)
                )
            }
        }
    }
}

private fun speakLongText(tts: TextToSpeech, text: String, utterancePrefix: String) {
    val chunks = SpeechTextChunker.split(text, TextToSpeech.getMaxSpeechInputLength())
    val requestId = "$utterancePrefix-${System.nanoTime()}"
    chunks.forEachIndexed { index, chunk ->
        val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts.speak(chunk, queueMode, null, "$requestId-$index")
    }
}

@Composable
private fun LargeActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
