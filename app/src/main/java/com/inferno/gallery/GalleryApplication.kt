package com.inferno.gallery

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inferno.gallery.workers.MediaSyncWorker
import com.inferno.gallery.workers.OcrIndexWorker

import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.MediaStoreThumbnailFetcher
import com.inferno.gallery.data.network.TelegramCoilFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

class GalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Chain: MediaStore sync → OCR indexing (OCR must wait for sync to populate the DB)
        val syncWorkRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30, java.util.concurrent.TimeUnit.SECONDS
            )
            .build()

        val settingsRepo = SettingsRepository.getInstance(this)
        kotlinx.coroutines.MainScope().launch {
            val ocrEnabled = settingsRepo.ocrIndexingEnabledFlow.first()

            if (ocrEnabled) {
                val ocrIndexRequest = androidx.work.OneTimeWorkRequestBuilder<OcrIndexWorker>()
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                // Chain: sync first, then OCR — prevents OCR from seeing an empty DB
                WorkManager.getInstance(this@GalleryApplication)
                    .beginUniqueWork("MediaSyncWorker", androidx.work.ExistingWorkPolicy.KEEP, syncWorkRequest)
                    .then(ocrIndexRequest)
                    .enqueue()
            } else {
                // No OCR needed, just run sync alone
                WorkManager.getInstance(this@GalleryApplication)
                    .enqueueUniqueWork("MediaSyncWorker", androidx.work.ExistingWorkPolicy.KEEP, syncWorkRequest)
            }

            val autoCleanEnabled = settingsRepo.autoCleanTrashEnabledFlow.first()
            if (autoCleanEnabled) {
                val days = settingsRepo.autoCleanTrashDaysFlow.first()
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
                val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.inferno.gallery.workers.AutoCleanTrashWorker>(
                    24, java.util.concurrent.TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(this@GalleryApplication).enqueueUniquePeriodicWork(
                    "AutoCleanTrashWorker",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // Support high-performance system thumbnails for local MediaStore items
                add(MediaStoreThumbnailFetcher.Factory(context))
                // Support streaming directly from Telegram Cloud
                add(TelegramCoilFetcher.Factory(SettingsRepository.getInstance(context)))
                // Support GIFs, Animated WebP, and Animated HEIF
                add(AnimatedImageDecoder.Factory())
                // Support SVGs
                add(SvgDecoder.Factory())
                // Support Video Frame extraction
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.50) // Use 50% of available heap for buttery scrolling
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                    .maxSizeBytes(512L * 1024L * 1024L) // 512 MB disk cache
                    .build()
            }
            .crossfade(false) // Premium smooth loading transition
            .build()
    }
}
