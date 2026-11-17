package org.glazouh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Live camera analysis with a bounded frame rate and CameraX's KEEP_ONLY_LATEST policy.
 * ImageProxy is always closed immediately after conversion; inference runs off the camera
 * executor and only one inference may be active at a time.
 */
@Composable
fun LiveObjectDetectionCamera(
    detector: OfflineObjectDetector,
    onDetections: (List<OfflineObjectDetector.Detection>) -> Unit,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        onStatus(if (granted) "Камера готова" else "Для непрерывного распознавания нужно разрешение на камеру.")
    }

    if (!hasPermission) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Для непрерывного распознавания предметов нужен доступ к камере.")
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Разрешить камеру") }
        }
        return
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            contentDescription = "Непрерывный предпросмотр задней камеры"
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val inferenceInProgress = remember { AtomicBoolean(false) }
    val lastAcceptedFrame = remember { AtomicLong(0L) }
    var cameraReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView, detector) {
        var provider: ProcessCameraProvider? = null
        var active = true
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            val now = SystemClock.elapsedRealtime()
            val last = lastAcceptedFrame.get()
            val intervalElapsed = last == 0L || now - last >= FRAME_INTERVAL_MS

            if (!intervalElapsed || !inferenceInProgress.compareAndSet(false, true)) {
                imageProxy.close()
                return@setAnalyzer
            }

            lastAcceptedFrame.set(now)
            val rotation = imageProxy.imageInfo.rotationDegrees
            val rawBitmap = try {
                imageProxy.toBitmap()
            } catch (error: Exception) {
                null
            } finally {
                imageProxy.close()
            }

            if (rawBitmap == null) {
                inferenceInProgress.set(false)
                ContextCompat.getMainExecutor(context).execute {
                    if (active) onStatus("Не удалось подготовить кадр камеры.")
                }
                return@setAnalyzer
            }

            val frame = try {
                prepareFrame(rawBitmap, rotation)
            } catch (error: Exception) {
                if (!rawBitmap.isRecycled) rawBitmap.recycle()
                inferenceInProgress.set(false)
                ContextCompat.getMainExecutor(context).execute {
                    if (active) onStatus("Ошибка подготовки кадра: ${error.localizedMessage ?: "неизвестная ошибка"}")
                }
                return@setAnalyzer
            }

            scope.launch {
                try {
                    if (active) {
                        val result = detector.detect(frame, onStatus)
                        if (active) onDetections(result)
                    }
                } catch (error: Exception) {
                    if (active) onStatus(error.localizedMessage ?: "Ошибка непрерывного распознавания.")
                } finally {
                    if (!frame.isRecycled) frame.recycle()
                    inferenceInProgress.set(false)
                }
            }
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!active) return@addListener
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                cameraReady = true
                onStatus("Непрерывное распознавание включено. Анализирую не чаще одного кадра в секунду.")
            } catch (error: Exception) {
                cameraReady = false
                onStatus("Не удалось включить камеру: ${error.localizedMessage ?: "ошибка"}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            active = false
            analysis.clearAnalyzer()
            provider?.unbindAll()
            analysis.close()
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth().height(280.dp)
        )
        Text(
            if (cameraReady) {
                "Камера активна · очередь кадров не накапливается"
            } else {
                "Подключаю камеру…"
            },
            fontSize = 15.dp.value.let { androidx.compose.ui.unit.sp(it) }
        )
    }
}

private fun prepareFrame(source: Bitmap, rotationDegrees: Int): Bitmap {
    val maxDimension = maxOf(source.width, source.height)
    val scale = minOf(1f, MAX_LIVE_FRAME_DIMENSION.toFloat() / maxDimension)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        source
    }

    if (scaled !== source) source.recycle()
    if (rotationDegrees == 0) return scaled

    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
    if (rotated !== scaled) scaled.recycle()
    return rotated
}

private const val FRAME_INTERVAL_MS = 1_000L
private const val MAX_LIVE_FRAME_DIMENSION = 960
