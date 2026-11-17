import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.glazouh.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.glazouh.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    flavorDimensions += "speech"
    productFlavors {
        create("lite") {
            dimension = "speech"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "SHERPA_ENABLED", "false")
        }
        create("full") {
            dimension = "speech"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
            buildConfigField("boolean", "SHERPA_ENABLED", "true")
        }
    }
}


val sherpaOnnxVersion = "2.0.1"
val sherpaOnnxSha256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
val sherpaOnnxAar = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")

fun sha256Of(file: File): String =
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

val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    outputs.file(sherpaOnnxAar)
    doLast {
        val minimumBytes = 45_000_000L

        if (sherpaOnnxAar.exists() && sherpaOnnxAar.length() >= minimumBytes) {
            val existingHash = sha256Of(sherpaOnnxAar)
            if (existingHash == sherpaOnnxSha256) {
                logger.lifecycle("Verified cached sherpa-onnx $sherpaOnnxVersion AAR")
                return@doLast
            }
            sherpaOnnxAar.delete()
        }

        sherpaOnnxAar.parentFile.mkdirs()
        val partial = File(sherpaOnnxAar.parentFile, "${sherpaOnnxAar.name}.part")
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
        var lastError: Exception? = null

        for (attempt in 1..3) {
            try {
                logger.lifecycle("Downloading sherpa-onnx runtime (attempt $attempt/3)")
                partial.delete()
                URI(url).toURL().openStream().use { input ->
                    partial.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                check(partial.length() >= minimumBytes) {
                    "Incomplete sherpa-onnx AAR download: ${partial.length()} bytes"
                }
                val actualHash = sha256Of(partial)
                check(actualHash == sherpaOnnxSha256) {
                    "sherpa-onnx AAR SHA-256 mismatch: expected $sherpaOnnxSha256, got $actualHash"
                }
                check(partial.renameTo(sherpaOnnxAar)) {
                    "Could not move the verified sherpa-onnx AAR into app/libs"
                }
                logger.lifecycle("Verified sherpa-onnx AAR (${sherpaOnnxAar.length()} bytes)")
                return@doLast
            } catch (error: Exception) {
                lastError = error
                partial.delete()
                logger.lifecycle("sherpa-onnx download failed: ${error.message}")
            }
        }
        throw GradleException("Could not download verified sherpa-onnx runtime: ${lastError?.message}")
    }
}

// Download the optional native speech runtime only for Full variants.
tasks.matching { it.name.startsWith("preFull") }.configureEach {
    dependsOn(downloadSherpaOnnx)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")

    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // On-device COCO object detection. Model file is downloaded on demand and SHA-256 verified.
    implementation("com.google.mediapipe:tasks-vision:1.2.2")
    // Apache-2.0 offline Russian streaming ASR runtime; downloaded and SHA-256 verified by Gradle.
    "fullImplementation"(files(sherpaOnnxAar))
    // BZip2/TAR extraction for the hash-pinned offline diarization model bundle.
    "fullImplementation"("org.apache.commons:commons-compress:1.27.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
