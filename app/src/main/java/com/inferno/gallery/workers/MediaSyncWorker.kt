package com.inferno.gallery.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inferno.gallery.data.LocalMediaRepository
import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.CoreMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class MediaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("MediaSyncWorker", "Starting MediaStore sync...")
            val mediaRepository = LocalMediaRepository(applicationContext.contentResolver)
            val database = DatabaseProvider.getDatabase(applicationContext)
            
            val mediaStoreList = mediaRepository.getImagesListForSync()
            val dbList = database.mediaDao().getAllMedia()
            
            val mediaStoreMap = mediaStoreList.associateBy { it.id }
            val dbMap = dbList.associateBy { it.id }
            
            val toInsert = mutableListOf<CoreMediaEntity>()
            val toDelete = mutableListOf<Long>()
            
            // Find items in MediaStore not in DB or changed (e.g. trashed/restored)
            for (media in mediaStoreList) {
                val dbItem = dbMap[media.id]
                val needsUpdate = dbItem == null || 
                                  dbItem.bucketName != media.bucketName || 
                                  dbItem.dateModified != media.dateModified ||
                                  (!media.isVideo && dbItem.pHash == null) ||
                                  dbItem.fileHash == null

                if (needsUpdate) {
                    var pHash = dbItem?.pHash
                    var lat = dbItem?.latitude
                    var lon = dbItem?.longitude
                    var fileHash = dbItem?.fileHash

                    val file = if (media.path.isNotEmpty()) java.io.File(media.path) else null

                    // Compute fileHash (MD5) for all files if missing
                    if (fileHash == null && file != null && file.exists()) {
                        try {
                            fileHash = com.inferno.gallery.utils.HashUtils.computeFileHash(file)
                        } catch (e: Exception) {
                            Log.e("MediaSyncWorker", "Error computing fileHash for ${media.id}: ${e.message}")
                        }
                    }

                    // Compute pHash (dHash) and GPS for images if missing
                    if (!media.isVideo && pHash == null) {
                        try {
                            if (file != null && file.exists()) {
                                val exif = androidx.exifinterface.media.ExifInterface(media.path)
                                val latLong = exif.latLong
                                if (latLong != null) {
                                    lat = latLong[0]
                                    lon = latLong[1]
                                }
                            }
                            val thumbnail = applicationContext.contentResolver.loadThumbnail(
                                media.uri, android.util.Size(128, 128), null
                            )
                            pHash = com.inferno.gallery.utils.HashUtils.generatePerceptualHash(thumbnail)
                        } catch (e: Exception) {
                            Log.e("MediaSyncWorker", "Error extracting metadata for ${media.id}: ${e.message}")
                        }
                    }

                    toInsert.add(
                        CoreMediaEntity(
                            id = media.id,
                            uriString = media.uri.toString(),
                            filePath = media.path,
                            bucketName = media.bucketName,
                            dateAdded = media.dateAdded,
                            dateModified = media.dateModified,
                            size = media.size,
                            name = media.name,
                            mimeType = dbItem?.mimeType, 
                            isVideo = media.isVideo,
                            durationMs = media.durationMs,
                            isIndexedOcr = dbItem?.isIndexedOcr ?: false,
                            pHash = pHash,
                            latitude = lat,
                            longitude = lon,
                            fileHash = fileHash
                        )
                    )
                }
            }
            
            // Find items in DB not in MediaStore, except those that have been successfully backed up to Telegram
            val successfulBackupIds = database.telegramBackupDao().getSuccessfulBackupIds().toSet()
            for (dbItem in dbList) {
                if (!mediaStoreMap.containsKey(dbItem.id) && !successfulBackupIds.contains(dbItem.id)) {
                    toDelete.add(dbItem.id)
                }
            }
            
            if (toInsert.isNotEmpty()) {
                Log.d("MediaSyncWorker", "Inserting ${toInsert.size} new items into Room SSOT.")
                database.mediaDao().insertAll(toInsert)

                val settingsRepo = SettingsRepository.getInstance(applicationContext)
                val autoBackupEnabled = settingsRepo.telegramBackupEnabledFlow.first()
                if (autoBackupEnabled) {
                    val autoBackupFolders = settingsRepo.telegramAutoBackupFoldersFlow.first()
                    val toBackup = toInsert.filter { autoBackupFolders.contains(it.bucketName) && !it.isVideo }
                    if (toBackup.isNotEmpty()) {
                        Log.d("MediaSyncWorker", "Auto-queueing ${toBackup.size} items for backup...")
                        val backupDao = database.telegramBackupDao()
                        for (item in toBackup) {
                            backupDao.insertOrUpdate(
                                com.inferno.gallery.data.db.TelegramBackupEntity(
                                    mediaId = item.id,
                                    telegramFileId = null,
                                    telegramThumbFileId = null,
                                    telegramMessageId = null,
                                    backupStatus = "PENDING",
                                    backupTimestamp = System.currentTimeMillis()
                                )
                            )
                        }

                        val wifiOnly = settingsRepo.telegramAutoBackupWifiOnlyFlow.first()
                        val constraints = androidx.work.Constraints.Builder().apply {
                            if (wifiOnly) {
                                setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                            } else {
                                setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            }
                        }.build()

                        val backupRequest = androidx.work.OneTimeWorkRequestBuilder<TelegramBackupWorker>()
                            .setConstraints(constraints)
                            .build()

                        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                            "TelegramBackupWorker",
                            androidx.work.ExistingWorkPolicy.KEEP,
                            backupRequest
                        )
                    }
                }

                // Trigger Smart Search auto-indexing if enabled and model is downloaded
                val smartSearchEngine = com.inferno.gallery.data.ai.SmartSearchEngine.getInstance(applicationContext)
                val autoIndexSmartEnabled = settingsRepo.smartSearchAutoIndexFlow.first()
                if (autoIndexSmartEnabled && smartSearchEngine.isModelDownloaded()) {
                    Log.d("MediaSyncWorker", "Auto-indexing new images for Smart Search...")
                    val indexRequest = androidx.work.OneTimeWorkRequestBuilder<SmartSearchIndexWorker>().build()
                    androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "SmartSearchIndexWorker",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        indexRequest
                    )
                }
            }
            
            if (toDelete.isNotEmpty()) {
                Log.d("MediaSyncWorker", "Deleting ${toDelete.size} items from Room SSOT.")
                database.mediaDao().deleteByIds(toDelete)
            }
            
            Log.d("MediaSyncWorker", "Sync complete.")
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MediaSyncWorker", "Sync failed: ${e.message}")
            Result.retry()
        }
    }
}
