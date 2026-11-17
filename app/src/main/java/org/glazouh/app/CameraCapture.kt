package org.glazouh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraCapture(
    onCaptured: (Bitmap) -> Unit,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
        onStatus(if (granted) "Камера готова" else "Нужно разрешение на камеру")
    }

    if (!hasPermission) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Для чтения надписей нужно разрешить доступ к камере.")
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Разрешить камеру") }
        }
        return
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            contentDescription = "Предпросмотр задней камеры"
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView, imageCapture) {
        var provider: ProcessCameraProvider? = null
        var active = true
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
                    imageCapture
                )
                cameraReady = true
                onStatus("Камера готова. Наведите её на надпись и сделайте снимок.")
            } catch (error: Exception) {
                cameraReady = false
                onStatus("Не удалось открыть камеру: \${error.localizedMessage ?: "ошибка"}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            active = false
            provider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxWidth().height(280.dp)
        )
        Button(
            onClick = { captureImage(context, imageCapture, cameraExecutor, onCaptured, onStatus) },
            enabled = cameraReady,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сфотографировать и прочитать") }
    }
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    executor: ExecutorService,
    onCaptured: (Bitmap) -> Unit,
    onStatus: (String) -> Unit
) {
    val outputFile = File(context.cacheDir, "glazouh-capture-\${System.nanoTime()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    onStatus("Снимаю изображение…")

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val rawBitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    if (rawBitmap == null) {
                        onStatus("Не удалось прочитать снимок")
                        return
                    }
                    val rotation = runCatching {
                        ExifInterface(outputFile).rotationDegrees
                    }.getOrDefault(0)
                    val finalBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        Bitmap.createBitmap(
                            rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                        ).also { rawBitmap.recycle() }
                    } else {
                        rawBitmap
                    }
                    onCaptured(finalBitmap)
                } catch (error: Exception) {
                    onStatus("Ошибка обработки снимка: \${error.localizedMessage ?: "ошибка"}")
                } finally {
                    outputFile.delete()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                outputFile.delete()
                onStatus("Ошибка камеры: \${exception.localizedMessage ?: "неизвестная ошибка"}")
            }
        }
    )
}
