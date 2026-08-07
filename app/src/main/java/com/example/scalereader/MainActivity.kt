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
import com.example.scalereader.databinding.ActivityMainBinding // Sesuaikan jika menggunakan ViewBinding, atau pakai findViewById di bawah
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cek apakah izin kamera sudah diberikan
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            // Jika belum, minta izin secara pop-up ke pengguna
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

    // Menangkap pilihan pengguna pada pop-up izin
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera ditolak. Aplikasi tidak bisa berjalan.", Toast.LENGTH_LONG).show()
                finish() // Menutup aplikasi jika izin ditolak
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
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

            val preview = Preview.Builder().build().also {
                // Menghubungkan preview kamera ke PreviewView di activity_main.xml
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.previewView).surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, 
                    CameraSelector.DEFAULT_BACK_CAMERA, 
                    preview, 
                    analysisUseCase
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
