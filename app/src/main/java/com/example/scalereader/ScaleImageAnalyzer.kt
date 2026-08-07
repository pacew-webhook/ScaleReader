package com.example.scalereader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.View
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ScaleImageAnalyzer(
    private val context: Context,
    private val previewView: PreviewView,
    private val scanBox: View,
    private val onWeightDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTimestamp = 0L
    private val scanIntervalMs = 400L

    // Anti-Debounce Variables
    private var lastDetectedValue = ""
    private var consecutiveCount = 0
    private var lastSentValue = ""
    private val requiredStabilityCount = 3

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < scanIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        try {
            val croppedBitmap = cropToScanBox(imageProxy, previewView, scanBox)
            val processedBitmap = preprocessFor7Segment(croppedBitmap)

            val inputImage = InputImage.fromBitmap(processedBitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val cleanWeight = visionText.text.replace(Regex("[^0-9.]"), "").trim()
                    if (cleanWeight.isNotEmpty() && cleanWeight.matches(Regex("^\\d+(\\.\\d+)?$"))) {
                        processStableWeight(cleanWeight)
                    }
                }
                .addOnCompleteListener { imageProxy.close() }

        } catch (e: Exception) {
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
            FeedbackUtils.triggerFeedback(context)
            onWeightDetected(currentWeight)
        }
    }

    private fun cropToScanBox(imageProxy: ImageProxy, preview: PreviewView, box: View): Bitmap {
        val fullBitmap = imageProxyToBitmap(imageProxy)
        val boxLoc = IntArray(2).also { box.getLocationOnScreen(it) }
        val prevLoc = IntArray(2).also { preview.getLocationOnScreen(it) }

        val boxLeft = boxLoc[0] - prevLoc[0]
        val boxTop = boxLoc[1] - prevLoc[1]

        val scaleX = fullBitmap.width.toFloat() / preview.width.toFloat()
        val scaleY = fullBitmap.height.toFloat() / preview.height.toFloat()

        val cropX = (boxLeft * scaleX).toInt().coerceIn(0, fullBitmap.width - 1)
        val cropY = (boxTop * scaleY).toInt().coerceIn(0, fullBitmap.height - 1)
        var cropW = (box.width * scaleX).toInt()
        var cropH = (box.height * scaleY).toInt()

        if (cropX + cropW > fullBitmap.width) cropW = fullBitmap.width - cropX
        if (cropY + cropH > fullBitmap.height) cropH = fullBitmap.height - cropY

        return Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap
    }

    private fun preprocessFor7Segment(inputBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(inputBitmap, srcMat)

        val grayMat = Mat()
        val blurMat = Mat()
        val binaryMat = Mat()
        val dilatedMat = Mat()

        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY)
        Imgproc.GaussianBlur(grayMat, blurMat, Size(5.0, 5.0), 0.0)
        Imgproc.adaptiveThreshold(blurMat, binaryMat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)
        
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(binaryMat, dilatedMat, kernel)

        val outputMat = Mat()
        Imgproc.cvtColor(dilatedMat, outputMat, Imgproc.COLOR_GRAY2RGBA)

        val resultBitmap = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, resultBitmap)
        return resultBitmap
    }
}
