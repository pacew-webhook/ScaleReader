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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cek izin kamera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Inisialisasi ScaleImageAnalyzer dengan 3 parameter yang lengkap
            val imageAnalyzer = ScaleImageAnalyzer(
                context = this,
                onWeightDetected = { weight -> 
                    // Parameter ini sekarang terisi agar tidak error kompilasi
                    android.util.Log.d("ScaleReader", "Berat terdeteksi: $weight")
                },
                onStatusUpdate = { message ->
                    // Memunculkan Toast ke layar HP saat pengiriman data sukses/gagal
                    runOnUiThread {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            )

            val analysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), imageAnalyzer) }

            val preview = Preview.Builder().build()
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysisUseCase)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}
