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

/**
 * Background WorkManager worker that runs after [MediaSyncWorker].
 *
 * Three-stage coroutine pipeline (OPT-5):
 *  Stage 1 (IO)      — Load MediaStore thumbnail → fill FloatBuffer
 *  Stage 2 (Default) — ONNX inference → FloatArray embedding
 *  Stage 3 (IO)      — Batch-insert embeddings into Room (30 per commit)
 *
 * Additional optimisations applied:
 *  OPT-1 — MediaStore ContentResolver.loadThumbnail (Q+) / getThumbnail (pre-Q)
 *  OPT-3 — Each bitmap recycled immediately after pixel extraction
 *  OPT-4 — Batch Room inserts via SearchDao.insertVectors()
 *  OPT-6 — No LIMIT clause; entire unindexed set processed in one run
 *  OPT-7 — Expedited work request; getForegroundInfo() satisfies the contract
 */
class AIIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AIIndexWorker"
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "ai_indexing_channel"
        // PERF OPT-5: Batch write size and channel capacities.
        private const val WRITE_BATCH_SIZE = 30
        private const val LOAD_CHANNEL_CAPACITY = 8   // Stage 1 → Stage 2
        private const val EMBED_CHANNEL_CAPACITY = 8  // Stage 2 → Stage 3
    }

    // PERF OPT-7: getForegroundInfo() is required for expedited workers.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Preparing…")

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val context = applicationContext
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI Indexing",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows progress while indexing photos for smart search" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Indexing photos for Smart Search")
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

    // ── Data classes passed through the pipeline channels ──────────────────────

    /** Stage 1 output — raw thumbnail pixels packed into a float buffer. */
    private data class LoadedImage(
        val entity: CoreMediaEntity,
        val uri: Uri,
        val pixels: IntArray     // already normalised pixel ints; 256×256
    )

    /** Stage 2 output — the CLIP embedding as a raw byte array ready for Room. */
    private data class EmbeddedImage(
        val entity: CoreMediaEntity,
        val vectorBytes: ByteArray
    )

    // ────────────────────────────────────────────────────────────────────────────

    override suspend fun doWork(): Result {
        try {
            // PERF OPT-7: Promote to foreground service immediately.
            setForeground(createForegroundInfo("Starting…"))

            val db = DatabaseProvider.getDatabase(applicationContext)
            val imageEncoder = ONNXImageEncoder(applicationContext)
            imageEncoder.initialize()

            // PERF OPT-6: No LIMIT — load all unindexed images in one query.
            val unindexed = withContext(Dispatchers.IO) {
                db.mediaDao().getUnindexedClipMedia() // already filters isVideo = 0
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

            Log.d(TAG, "AI indexing started. Unindexed: ${unindexed.size}, total: $totalImageCount")

            setProgress(workDataOf(
                "progress" to alreadyIndexed,
                "total" to totalImageCount,
                "recent_uris" to emptyArray<String>()
            ))

            // PERF OPT-5: Three-stage concurrent pipeline.
            val loadChannel  = Channel<LoadedImage>(LOAD_CHANNEL_CAPACITY)
            val embedChannel = Channel<EmbeddedImage>(EMBED_CHANNEL_CAPACITY)

            coroutineScope {

                // ── STAGE 1: Load thumbnails (IO) ────────────────────────────
                launch(Dispatchers.IO) {
                    for (entity in unindexed) {
                        if (isStopped) break
                        try {
                            val uri = Uri.parse(entity.uriString)

                            // PERF OPT-1: Use MediaStore thumbnail API instead of
                            // decoding the full original file. This is 4-10× faster
                            // for high-resolution images and avoids OOM on RAW files.
                            val thumbnail: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                runCatching {
                                    applicationContext.contentResolver.loadThumbnail(
                                        uri,
                                        android.util.Size(512, 512),
                                        null
                                    )
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

                            // Scale to 256×256 for the encoder
                            val targetSize = 256
                            val scaled = Bitmap.createScaledBitmap(thumbnail, targetSize, targetSize, true)

                            // PERF OPT-3: Recycle thumbnail immediately after scaling.
                            thumbnail.recycle()

                            // Extract pixels, then recycle the scaled bitmap immediately.
                            val pixels = IntArray(targetSize * targetSize)
                            scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

                            // PERF OPT-3: Recycle scaled bitmap immediately after pixels are copied.
                            scaled.recycle()

                            loadChannel.send(LoadedImage(entity, uri, pixels))

                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage1 load failed for ${entity.name}: ${e.message}")
                            runCatching { db.mediaDao().updateClipIndexStatus(entity.id, true) }
                        }
                    }
                    loadChannel.close()
                }

                // ── STAGE 2: ONNX inference (Default / CPU) ─────────────────
                launch(Dispatchers.Default) {
                    val targetSize = 256
                    val pixelCount = targetSize * targetSize
                    val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
                    val std  = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

                    for (loaded in loadChannel) {
                        if (isStopped) break
                        try {
                            // Build the float buffer from the pre-extracted pixels
                            val floatBuffer = java.nio.FloatBuffer.allocate(3 * pixelCount)
                            for (i in 0 until pixelCount) {
                                val px = loaded.pixels[i]
                                floatBuffer.put(i,                r(px, mean[0], std[0]))
                                floatBuffer.put(i + pixelCount,   g(px, mean[1], std[1]))
                                floatBuffer.put(i + 2 * pixelCount, b(px, mean[2], std[2]))
                            }

                            val embedding = imageEncoder.encodeFromBuffer(floatBuffer, targetSize)

                            // Serialize FloatArray → little-endian ByteArray
                            val byteBuffer = ByteBuffer.allocate(embedding.size * 4)
                                .order(ByteOrder.LITTLE_ENDIAN)
                            embedding.forEach { byteBuffer.putFloat(it) }

                            embedChannel.send(EmbeddedImage(loaded.entity, byteBuffer.array()))

                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage2 inference failed for ${loaded.entity.name}: ${e.message}")
                            runCatching { db.mediaDao().updateClipIndexStatus(loaded.entity.id, true) }
                        }
                    }
                    embedChannel.close()
                }

                // ── STAGE 3: Batch write to Room (IO) ───────────────────────
                launch(Dispatchers.IO) {
                    val vectorBatch   = mutableListOf<MediaVectorEntity>()
                    val indexedIdBatch = mutableListOf<Long>()

                    suspend fun flush() {
                        if (vectorBatch.isEmpty()) return
                        // PERF OPT-4: Batch insert — one transaction for WRITE_BATCH_SIZE rows.
                        db.searchDao().insertVectors(vectorBatch.toList())
                        indexedIdBatch.forEach { id ->
                            db.mediaDao().updateClipIndexStatus(id, true)
                        }
                        vectorBatch.clear()
                        indexedIdBatch.clear()
                    }

                    for (embedded in embedChannel) {
                        if (isStopped) break

                        vectorBatch.add(MediaVectorEntity(
                            mediaId = embedded.entity.id,
                            clipVector = embedded.vectorBytes
                        ))
                        indexedIdBatch.add(embedded.entity.id)
                        processedCount++

                        recentUris.addLast(embedded.entity.uriString)
                        if (recentUris.size > 5) recentUris.removeFirst()

                        if (vectorBatch.size >= WRITE_BATCH_SIZE) {
                            flush()
                        }

                        // Update UI progress
                        val current = alreadyIndexed + processedCount
                        setProgress(workDataOf(
                            "progress" to current,
                            "total" to totalImageCount,
                            "recent_uris" to recentUris.toTypedArray()
                        ))
                        setForeground(createForegroundInfo("$current / $totalImageCount images"))
                    }

                    // Flush any remaining items in the partial batch
                    flush()
                }
            } // end coroutineScope — all three stages have completed

            Log.d(TAG, "AI indexing complete. Indexed $processedCount images.")
            return Result.success()

        } catch (e: Throwable) {
            Log.e(TAG, "AIIndexWorker failed fatally: ${e.message}")
            e.printStackTrace()
            return Result.retry()
        }
    }

    // ── Helpers for per-channel pixel normalisation ───────────────────────────
    private fun r(pixel: Int, mean: Float, std: Float) =
        ((pixel shr 16 and 0xFF) / 255.0f - mean) / std
    private fun g(pixel: Int, mean: Float, std: Float) =
        ((pixel shr 8 and 0xFF) / 255.0f - mean) / std
    private fun b(pixel: Int, mean: Float, std: Float) =
        ((pixel and 0xFF) / 255.0f - mean) / std
}
