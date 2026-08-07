package com.example.scalereader

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ScaleImageAnalyzer(
    private val context: Context,
    private val onWeightDetected: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTimestamp = 0L
    private val scanIntervalMs = 500L
    private var lastDetectedValue = ""
    private var consecutiveCount = 0
    private var lastSentValue = ""
    private val requiredStabilityCount = 2
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < scanIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val cleanText = line.text.replace(Regex("[^0-9.]"), "").trim()
                            if (cleanText.isNotEmpty() && cleanText.matches(Regex("^\\d+(\\.\\d+)?$"))) {
                                processStableWeight(cleanText)
                            }
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun processStableWeight(currentWeight: String) {
        if (currentWeight == lastDetectedValue) {
            consecutiveCount++
        } else {
            lastDetectedValue = currentWeight
            consecutiveCount = 1
        }

        if (consecutiveCount >= requiredStabilityCount && currentWeight != lastSentValue) {
            lastSentValue = currentWeight
            triggerVibration()
            sendToGoogleSheets(currentWeight)
            onWeightDetected(currentWeight)
        }
    }

    private fun sendToGoogleSheets(weight: String) {
        networkExecutor.execute {
            try {
                val webAppUrl = "https://script.google.com/macros/s/AKfycbybQTTzgv1ewRStBsncoHxeJqLXmbezHwtcYROHmxvCK8CMmrUHZNc3-bqCAcEzISDkzw/exec"
                val url = URL(webAppUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.instanceFollowRedirects = true
                conn.doOutput = true

                val jsonPayload = """{"weight": ${weight.toDoubleOrNull() ?: 0.0}, "unit": "kg"}"""
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonPayload); it.flush() }

                val responseCode = conn.responseCode
                mainHandler.post {
                    if (responseCode == 200) {
                        onStatusUpdate("Data $weight kg berhasil terkirim!")
                    } else {
                        onStatusUpdate("Gagal kirim, kode: $responseCode")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                mainHandler.post {
                    onStatusUpdate("Error: ${e.message}")
                }
            }
        }
    }

    private fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }
}
