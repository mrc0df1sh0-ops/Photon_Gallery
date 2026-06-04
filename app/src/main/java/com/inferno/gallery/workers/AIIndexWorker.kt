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

    /** Stage 1 output — holds optional pixels for CLIP and optional unscaled bitmap for OCR. */
    private data class LoadedImage(
        val entity: CoreMediaEntity,
        val uri: Uri,
        val pixels: IntArray?,     // 256x256 scaled pixels (null if already clip-indexed)
        val thumbnail: Bitmap?     // unscaled bitmap (null if already ocr-indexed)
    )

    /** Stage 2 output — holds the CLIP embedding byte array and/or OCR extracted text. */
    private data class EmbeddedImage(
        val entity: CoreMediaEntity,
        val vectorBytes: ByteArray?,
        val extractedText: String?
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
                db.mediaDao().getUnindexedMedia() // already filters isVideo = 0
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
                            val needsClip = !entity.isIndexedClip
                            val needsOcr = !entity.isIndexedOcr
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
                                    db.mediaDao().updateOcrIndexStatus(entity.id, true)
                                }
                                continue
                            }

                            var pixels: IntArray? = null
                            if (needsClip) {
                                // Scale to 256×256 for the ONNX encoder
                                val targetSize = 256
                                val scaled = Bitmap.createScaledBitmap(thumbnail, targetSize, targetSize, true)
                                
                                pixels = IntArray(targetSize * targetSize)
                                scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
                                
                                // PERF OPT-3: Recycle scaled bitmap immediately after pixels are copied.
                                scaled.recycle()
                            }

                            // Keep thumbnail alive if we need OCR; otherwise recycle it.
                            val finalThumbnail = if (needsOcr) {
                                thumbnail
                            } else {
                                thumbnail.recycle()
                                null
                            }

                            loadChannel.send(LoadedImage(entity, uri, pixels, finalThumbnail))

                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage1 load failed for ${entity.name}: ${e.message}")
                            runCatching { 
                                db.mediaDao().updateClipIndexStatus(entity.id, true)
                                db.mediaDao().updateOcrIndexStatus(entity.id, true)
                            }
                        }
                    }
                    loadChannel.close()
                }

                // ── STAGE 2: ONNX inference & ML Kit OCR (Default / CPU) ─────
                launch(Dispatchers.Default) {
                    val targetSize = 256
                    val pixelCount = targetSize * targetSize
                    val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
                    val std  = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

                    val textRecognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                    )

                    for (loaded in loadChannel) {
                        if (isStopped) break
                        try {
                            var vectorBytes: ByteArray? = null
                            var extractedText: String? = null

                            // 1. Process CLIP Embedding
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

                            // 2. Process ML Kit OCR
                            if (loaded.thumbnail != null) {
                                val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(loaded.thumbnail, 0)
                                extractedText = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                                    textRecognizer.process(inputImage)
                                        .addOnSuccessListener { visionText ->
                                            cont.resumeWith(kotlin.Result.success(visionText.text))
                                        }
                                        .addOnFailureListener {
                                            cont.resumeWith(kotlin.Result.success(null))
                                        }
                                }
                                loaded.thumbnail.recycle()
                            }

                            embedChannel.send(EmbeddedImage(loaded.entity, vectorBytes, extractedText))

                        } catch (e: Throwable) {
                            Log.e(TAG, "Stage2 inference failed for ${loaded.entity.name}: ${e.message}")
                            runCatching { 
                                db.mediaDao().updateClipIndexStatus(loaded.entity.id, true)
                                db.mediaDao().updateOcrIndexStatus(loaded.entity.id, true)
                            }
                        }
                    }
                    embedChannel.close()
                }

                // ── STAGE 3: Batch write to Room (IO) ───────────────────────
                launch(Dispatchers.IO) {
                    val vectorBatch   = mutableListOf<MediaVectorEntity>()
                    val clipIndexedBatch = mutableListOf<Long>()
                    val ocrIndexedBatch  = mutableListOf<Long>()

                    suspend fun flush() {
                        if (vectorBatch.isNotEmpty()) {
                            db.searchDao().insertVectors(vectorBatch.toList())
                        }
                        if (clipIndexedBatch.isNotEmpty()) {
                            db.mediaDao().markClipIndexed(clipIndexedBatch.toList())
                        }
                        if (ocrIndexedBatch.isNotEmpty()) {
                            db.mediaDao().markOcrIndexed(ocrIndexedBatch.toList())
                        }
                        vectorBatch.clear()
                        clipIndexedBatch.clear()
                        ocrIndexedBatch.clear()
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

                        if (!embedded.extractedText.isNullOrBlank()) {
                            DatabaseProvider.insertFtsRow(db, embedded.entity.id, embedded.extractedText, "")
                            ocrIndexedBatch.add(embedded.entity.id)
                        } else if (embedded.extractedText != null || !embedded.entity.isIndexedOcr) {
                            // Even if no text is found, we attempted OCR, so mark it done
                            ocrIndexedBatch.add(embedded.entity.id)
                        }

                        processedCount++
                        recentUris.addLast(embedded.entity.uriString)
                        if (recentUris.size > 5) recentUris.removeFirst()

                        if (clipIndexedBatch.size >= WRITE_BATCH_SIZE || ocrIndexedBatch.size >= WRITE_BATCH_SIZE) {
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
