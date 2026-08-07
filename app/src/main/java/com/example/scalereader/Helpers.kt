package com.example.scalereader

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import androidx.work.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- FEEDBACK UTILS ---
object FeedbackUtils {
    fun triggerFeedback(context: Context) {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(150)
        }
    }
}

// --- CSV LOGGER ---
object CsvLogger {
    fun logData(context: Context, weight: String, isSynced: Boolean) {
        try {
            val file = File(context.getExternalFilesDir(null), "riwayat_timbangan.csv")
            val isNew = !file.exists()
            val writer = FileWriter(file, true)
            if (isNew) writer.append("Waktu,Bobot_KG,Status_Kirim\n")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            writer.append("$time,$weight,${if (isSynced) "SYNCED" else "PENDING"}\n")
            writer.flush()
            writer.close()
        } catch (e: Exception) { e.printStackTrace() }
    }
}

// --- NETWORK HTTP ---
fun sendDataToPCWithFallback(context: Context, ipPC: String, weight: String) {
    val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
    val url = "http://$ipPC:5000/api/timbangan"
    val jsonBody = """{"weight": "$weight"}"""
    val request = Request.Builder().url(url).post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            CsvLogger.logData(context, weight, false)
        }
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) CsvLogger.logData(context, weight, true)
            else CsvLogger.logData(context, weight, false)
            response.close()
        }
    })
}

// --- SYNC MANAGER ---
object SyncManager {
    fun syncPendingData(context: Context, ipPC: String, onComplete: (Int) -> Unit) {
        val file = File(context.getExternalFilesDir(null), "riwayat_timbangan.csv")
        if (!file.exists()) { onComplete(0); return }

        val lines = file.readLines().toMutableList()
        if (lines.size <= 1) { onComplete(0); return }

        val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
        var count = 0

        for (i in 1 until lines.size) {
            val row = lines[i].split(",")
            if (row.size >= 3 && row[2].trim() == "PENDING") {
                val json = """{"weight": "${row[1]}", "timestamp": "${row[0]}"}"""
                val req = Request.Builder().url("http://$ipPC:5000/api/timbangan")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                try {
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) { lines[i] = "${row[0]},${row[1]},SYNCED"; count++ }
                    resp.close()
                } catch (e: Exception) { break }
            }
        }
        if (count > 0) {
            val writer = FileWriter(file, false)
            lines.forEach { writer.append("$it\n") }
            writer.flush(); writer.close()
        }
        onComplete(count)
    }
}

// --- WORKMANAGER ---
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ip = inputData.getString("IP_PC") ?: "192.168.1.15"
        var ok = false
        SyncManager.syncPendingData(applicationContext, ip) { ok = true }
        return if (ok) Result.success() else Result.retry()
    }
}

object WorkScheduler {
    fun enqueueOneTimeSync(context: Context, ipPC: String) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = workDataOf("IP_PC" to ipPC)
        val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork("SyncTimbangan", ExistingWorkPolicy.REPLACE, req)
    }
}
