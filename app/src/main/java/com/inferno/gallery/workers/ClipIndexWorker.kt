package com.inferno.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inferno.gallery.data.ai.ONNXImageEncoder
import com.inferno.gallery.data.db.CoreMediaEntity
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.MediaVectorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClipIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ClipIndexWorker"
        private const val NOTIFICATION_ID = 43
        private const val CHANNEL_ID = "clip_indexing_channel"
        private const val WRITE_BATCH_SIZE = 1
        private const val LOAD_CHANNEL_CAPACITY = 8
        private const val EMBED_CHANNEL_CAPACITY = 8
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Preparing…")

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val context = applicationContext
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Semantic Indexing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows progress while indexing photos for visual search" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Visual Semantic Indexing")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private data class LoadedImage(
        val entity: CoreMediaEntity,
        val uri: Uri,
        val pixels: IntArray?
    )

    private data class EmbeddedImage(
        val entity: CoreMediaEntity,
        val vectorBytes: ByteArray?
    )

    override suspend fun doWork(): Result {
        try {
            setForeground(createForegroundInfo("Starting…"))

            val db = DatabaseProvider.getDatabase(applicationContext)
            val imageEncoder = ONNXImageEncoder(applicationContext)
            imageEncoder.initialize()

            val unindexed = withContext(Dispatchers.IO) {
                db.mediaDao().getUnindexedClipMedia()
            }

            if (unindexed.isEmpty()) {
                Log.d(TAG, "Nothing to index.")
                return Result.success()
            }

            val totalImageCount = withContext(Dispatchers.IO) {
                db.mediaDao().getTotalImageCount()
            }
            val alreadyIndexed = totalImageCount - unindexed.size
            var processedCount = 0
            val recentUris = ArrayDeque<String>(5)

            Log.d(TAG, "CLIP indexing started. Unindexed: ${unindexed.size}, total: $totalImageCount")

            setProgress(workDataOf(
                "progress" to alreadyIndexed,
                "total" to totalImageCount,
                "recent_uris" to emptyArray<String>()
            ))

            val loadChannel  = Channel<LoadedImage>(LOAD_CHANNEL_CAPACITY)
            val embedChannel = Channel<EmbeddedImage>(EMBED_CHANNEL_CAPACITY)

            coroutineScope {
                launch(Dispatchers.IO) {
                    for (entity in unindexed) {
                        if (isStopped) break
                        try {
                            val uri = Uri.parse(entity.uriString)

                            val thumbnail: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                runCatching {
                                    applicationContext.contentResolver.loadThumbnail(uri, android.util.Size(512, 512), null)
                                }.getOrNull()
                            } else {
                                @Suppress("DEPRECATION")
                                runCatching {
                                    android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                                        applicationContext.contentResolver,
                                        entity.id,
                                        android.provider.MediaStore.Images.Thumbnails.MINI_KIND,
                                        null
                                    )
                                }.getOrNull()
                            }

                            if (thumbnail == null) {
                                Log.w(TAG, "No thumbnail for ${entity.name}, skipping.")
                                withContext(Dispatchers.IO) {
                                    db.mediaDao().updateClipIndexStatus(entity.id, true)
                                }
                                continue
                            }

                            val targetSize = 256
                            val scaled = Bitmap.createScaledBitmap(thumbnail, targetSize, targetSize, true)
                            val pixels = IntArray(targetSize * targetSize)
                            scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
                            
                            scaled.recycle()
                            thumbnail.recycle()

                            loadChannel.send(LoadedImage(entity, uri, pixels))

                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage1 load failed for ${entity.name}: ${e.message}")
                            runCatching { 
                                db.mediaDao().updateClipIndexStatus(entity.id, true)
                            }
                        }
                    }
                    loadChannel.close()
                }

                launch(Dispatchers.Default) {
                    val targetSize = 256
                    val pixelCount = targetSize * targetSize
                    val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
                    val std  = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

                    for (loaded in loadChannel) {
                        if (isStopped) break
                        try {
                            var vectorBytes: ByteArray? = null

                            if (loaded.pixels != null) {
                                val floatBuffer = java.nio.FloatBuffer.allocate(3 * pixelCount)
                                for (i in 0 until pixelCount) {
                                    val px = loaded.pixels[i]
                                    floatBuffer.put(i,                r(px, mean[0], std[0]))
                                    floatBuffer.put(i + pixelCount,   g(px, mean[1], std[1]))
                                    floatBuffer.put(i + 2 * pixelCount, b(px, mean[2], std[2]))
                                }

                                val embedding = imageEncoder.encodeFromBuffer(floatBuffer, targetSize)

                                val byteBuffer = ByteBuffer.allocate(embedding.size * 4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                embedding.forEach { byteBuffer.putFloat(it) }
                                vectorBytes = byteBuffer.array()
                            }

                            embedChannel.send(EmbeddedImage(loaded.entity, vectorBytes))

                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage2 inference failed for ${loaded.entity.name}: ${e.message}")
                            runCatching { 
                                db.mediaDao().updateClipIndexStatus(loaded.entity.id, true)
                            }
                        }
                    }
                    embedChannel.close()
                }

                launch(Dispatchers.IO) {
                    val vectorBatch   = mutableListOf<MediaVectorEntity>()
                    val clipIndexedBatch = mutableListOf<Long>()

                    suspend fun flush() {
                        if (vectorBatch.isNotEmpty()) {
                            db.searchDao().insertVectors(vectorBatch.toList())
                        }
                        if (clipIndexedBatch.isNotEmpty()) {
                            db.mediaDao().markClipIndexed(clipIndexedBatch.toList())
                        }
                        vectorBatch.clear()
                        clipIndexedBatch.clear()
                    }

                    for (embedded in embedChannel) {
                        if (isStopped) break

                        if (embedded.vectorBytes != null) {
                            vectorBatch.add(MediaVectorEntity(
                                mediaId = embedded.entity.id,
                                clipVector = embedded.vectorBytes
                            ))
                            clipIndexedBatch.add(embedded.entity.id)
                        }

                        processedCount++
                        recentUris.addLast(embedded.entity.uriString)
                        if (recentUris.size > 5) recentUris.removeFirst()

                        if (clipIndexedBatch.size >= WRITE_BATCH_SIZE) {
                            flush()
                        }

                        val current = alreadyIndexed + processedCount
                        setProgress(workDataOf(
                            "progress" to current,
                            "total" to totalImageCount,
                            "recent_uris" to recentUris.toTypedArray()
                        ))
                        setForeground(createForegroundInfo("$current / $totalImageCount images"))
                    }

                    flush()
                }
            } 

            Log.d(TAG, "CLIP indexing complete. Indexed $processedCount images.")
            return Result.success()

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "ClipIndexWorker failed fatally: ${e.message}")
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun r(pixel: Int, mean: Float, std: Float) =
        ((pixel shr 16 and 0xFF) / 255.0f - mean) / std
    private fun g(pixel: Int, mean: Float, std: Float) =
        ((pixel shr 8 and 0xFF) / 255.0f - mean) / std
    private fun b(pixel: Int, mean: Float, std: Float) =
        ((pixel and 0xFF) / 255.0f - mean) / std
}
