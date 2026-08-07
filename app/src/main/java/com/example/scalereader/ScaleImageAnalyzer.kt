package com.example.scalereader

import android.content.Context
import android.os.Build
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
import java.net.URLEncoder
import java.util.concurrent.Executors

class ScaleImageAnalyzer(
    private val context: Context,
    private val onWeightDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTimestamp = 0L
    private val scanIntervalMs = 500L

    private var lastDetectedValue = ""
    private var consecutiveCount = 0
    private var lastSentValue = ""
    private val requiredStabilityCount = 2

    private val networkExecutor = Executors.newSingleThreadExecutor()

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
                .addOnCompleteListener {
                    imageProxy.close()
                }
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
            sendToGoogleForm(currentWeight)
            onWeightDetected(currentWeight)
        }
    }

    private fun sendToGoogleForm(weight: String) {
        networkExecutor.execute {
            try {
                val formUrl = "https://docs.google.com/forms/d/e/1FAIpQLSf2DVcCrNONx49zxLjcMKTRKhqByvSMHxdteZltxBPwXzckFw/formResponse"
                val postData = "entry.1928234641=" + URLEncoder.encode(weight, "UTF-8")

                val url = URL(formUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(postData)
                writer.flush()
                writer.close()

                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun triggerVibration() {
        try {
            @Suppress("DEPRECATION")
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
