package com.inferno.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inferno.gallery.data.db.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val NOTIFICATION_ID = 55
        private const val CHANNEL_ID = "model_download_channel"

        private const val TEXT_MODEL_URL = "https://huggingface.co/Xenova/clip-vit-base-patch16/resolve/main/onnx/text_model_quantized.onnx"
        private const val VISION_MODEL_URL = "https://huggingface.co/Xenova/clip-vit-base-patch16/resolve/main/onnx/vision_model_quantized.onnx"
        private const val TOKENIZER_URL = "https://huggingface.co/Xenova/clip-vit-base-patch16/resolve/main/tokenizer.json"

        private const val TEXT_MODEL_NAME = "text_model_quantized.onnx"
        private const val VISION_MODEL_NAME = "vision_model_quantized.onnx"
        private const val TOKENIZER_NAME = "tokenizer.json"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Preparing model download…")

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val context = applicationContext
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Search Model Setup",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows progress while downloading AI model files" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading Smart Search Model")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        try {
            setForeground(createForegroundInfo("Connecting to Hugging Face…"))

            val modelDir = File(applicationContext.filesDir, "smart_search_model_v2")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val client = OkHttpClient()

            val downloadTargets = listOf(
                Triple(TOKENIZER_URL, TOKENIZER_NAME, 2.2 * 1024 * 1024L), // Approx tokenizer weight
                Triple(TEXT_MODEL_URL, TEXT_MODEL_NAME, 36.3 * 1024 * 1024L), // Approx text model weight
                Triple(VISION_MODEL_URL, VISION_MODEL_NAME, 87.7 * 1024 * 1024L) // Approx patch16 vision model weight
            )

            val totalBytesExpected = downloadTargets.sumOf { it.third }
            var totalBytesDownloaded = 0L

            for (target in downloadTargets) {
                if (isStopped) break
                val url = target.first
                val fileName = target.second
                
                val finalFile = File(modelDir, fileName)
                // If it already exists and seems complete, skip
                if (finalFile.exists() && finalFile.length() > target.third * 0.95) {
                    totalBytesDownloaded += finalFile.length()
                    Log.d(TAG, "$fileName already exists and appears complete, skipping.")
                    continue
                }

                val tempFile = File(modelDir, "$fileName.tmp")
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Failed to download $fileName: code ${response.code}")
                    }

                    val body = response.body ?: throw java.io.IOException("Empty response body for $fileName")
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(tempFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var fileBytesDownloaded = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            inputStream.close()
                            outputStream.close()
                            tempFile.delete()
                            break
                        }
                        outputStream.write(buffer, 0, bytesRead)
                        fileBytesDownloaded += bytesRead
                        totalBytesDownloaded += bytesRead

                        val overallPercent = ((totalBytesDownloaded.toFloat() / totalBytesExpected.toFloat()) * 100).toInt().coerceIn(0, 100)
                        setProgress(workDataOf("progress" to overallPercent))
                        setForeground(createForegroundInfo("Downloaded $overallPercent% ($fileName)"))
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    if (!isStopped) {
                        if (tempFile.exists()) {
                            if (finalFile.exists()) finalFile.delete()
                            tempFile.renameTo(finalFile)
                        }
                    }
                }
            }

            if (isStopped) {
                Log.d(TAG, "Download worker stopped before completion.")
                return Result.failure()
            }

            // Clear all old embeddings so they get re-calculated with the new model
            val db = DatabaseProvider.getDatabase(applicationContext)
            withContext(Dispatchers.IO) {
                db.embeddingDao().clearAllEmbeddings()
            }

            Log.d(TAG, "Model download completed successfully. Cleared old database embeddings for re-indexing.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "ModelDownloadWorker failed fatally: ${e.message}", e)
            return Result.retry()
        }
    }
}
