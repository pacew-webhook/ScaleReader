package com.example.scalereader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Memeriksa izin kamera saat aplikasi pertama kali dibuka
        if (allPermissionsGranted()) {
            initCameraAfterLayoutReady()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST_CODE
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                initCameraAfterLayoutReady()
            } else {
                Toast.makeText(this, "Izin kamera ditolak.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // Mengamankan inisialisasi agar PreviewView sudah siap secara UI
    private fun initCameraAfterLayoutReady() {
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        previewView.post {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val imageAnalyzer = ScaleImageAnalyzer(
                    context = this,
                    onWeightDetected = { weight -> 
                        android.util.Log.d("ScaleReader", "Berat terdeteksi: $weight")
                    },
                    onStatusUpdate = { message ->
                        runOnUiThread {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                )

                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), imageAnalyzer) }

                val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, 
                    CameraSelector.DEFAULT_BACK_CAMERA, 
                    preview, 
                    analysisUseCase
                )
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Gagal memulai kamera: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
