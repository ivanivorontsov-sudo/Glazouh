package org.glazouh.app

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ObjectDetectionScreen(
    detector: OfflineObjectDetector,
    tts: TextToSpeech,
    ttsReady: Boolean,
    onBack: () -> Unit,
    onStatus: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var detections by remember { mutableStateOf(emptyList<OfflineObjectDetector.Detection>()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ObjectActionButton("Назад", onBack)
        Text(
            "Наведите камеру на предметы и сделайте снимок. Модель определит распространённые объекты на самом телефоне.",
            fontSize = 17.sp
        )
        Text(
            "При первом запуске скачивается проверенная модель (~14 МБ). После этого распознавание работает офлайн.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        CameraCapture(
            onCaptured = { bitmap ->
                scope.launch {
                    isAnalyzing = true
                    detections = emptyList()
                    try {
                        onStatus("Анализирую изображение на устройстве…")
                        detections = detector.detect(bitmap, onStatus)
                        if (detections.isEmpty()) {
                            onStatus("Уверенно распознать предметы не удалось. Попробуйте другой ракурс или освещение.")
                        } else {
                            val spoken = detections.joinToString(separator = ". ") {
                                "${CocoLabelsRu.displayName(it.label)}, ${(it.confidence * 100).toInt()} процентов"
                            }
                            onStatus("Найдено объектов: ${detections.size}. Результат показан ниже.")
                            if (ttsReady) {
                                val chunks = SpeechTextChunker.split(spoken, TextToSpeech.getMaxSpeechInputLength())
                                chunks.forEachIndexed { index, chunk ->
                                    tts.speak(
                                        chunk,
                                        if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                                        null,
                                        "glazouh-objects-$index"
                                    )
                                }
                            }
                        }
                    } catch (error: Exception) {
                        onStatus(error.localizedMessage ?: "Не удалось выполнить распознавание предметов.")
                    } finally {
                        bitmap.recycle()
                        isAnalyzing = false
                    }
                }
            },
            onStatus = onStatus
        )

        if (isAnalyzing) {
            Text("Распознавание…", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (detections.isNotEmpty()) {
            Text("Обнаруженные объекты", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            detections.forEach { item ->
                Text(
                    "${CocoLabelsRu.displayName(item.label)} · ${String.format(Locale.US, "%.0f", item.confidence * 100)}%",
                    fontSize = 19.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
            ObjectActionButton("Озвучить ещё раз") {
                val spoken = detections.joinToString(separator = ". ") {
                    CocoLabelsRu.displayName(it.label)
                }
                val chunks = SpeechTextChunker.split(spoken, TextToSpeech.getMaxSpeechInputLength())
                chunks.forEachIndexed { index, chunk ->
                    tts.speak(
                        chunk,
                        if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                        null,
                        "glazouh-objects-repeat-$index"
                    )
                }
            }
            ObjectActionButton("Остановить озвучивание") { tts.stop() }
        }

        Text(
            "Ограничение: модель распознаёт типы предметов из набора COCO. Она может ошибаться, не оценивает расстояние и не определяет цвет/состояние светофора. Не используйте её для решения, когда безопасно переходить дорогу.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ObjectActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}
