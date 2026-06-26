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
            val settingsRepo = SettingsRepository.getInstance(applicationContext)

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
                                  dbItem.dateModified != media.dateModified

                if (needsUpdate) {
                    toInsert.add(
                        CoreMediaEntity(
                            id = media.id,
                            uriString = media.uri.toString(),
                            filePath = media.path,
                            bucketName = media.bucketName,
                            // Preserve the original dateAdded from Room if it exists
                            // (e.g., vault unhide pre-inserts with the original date,
                            // but MediaStore gives a new DATE_ADDED = today)
                            dateAdded = dbItem?.dateAdded ?: media.dateAdded,
                            dateModified = media.dateModified,
                            size = media.size,
                            name = media.name,
                            mimeType = dbItem?.mimeType,
                            isVideo = media.isVideo,
                            durationMs = media.durationMs,
                            isIndexedOcr = dbItem?.isIndexedOcr ?: false,
                            // Preserve existing hashes/GPS if present (don't overwrite)
                            pHash = dbItem?.pHash,
                            latitude = dbItem?.latitude,
                            longitude = dbItem?.longitude,
                            fileHash = dbItem?.fileHash
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

                val autoBackupEnabled = settingsRepo.telegramBackupEnabledFlow.first()
                if (autoBackupEnabled) {
                    val autoBackupFolders = settingsRepo.telegramAutoBackupFoldersFlow.first()
                    val backupMode = settingsRepo.telegramBackupModeFlow.first()
                        // Userbot mode supports video upload (up to 2 GB); Bot API mode is photos only
                        val toBackup = toInsert.filter { item ->
                            autoBackupFolders.contains(item.bucketName) &&
                                    (backupMode == "userbot" || !item.isVideo)
                        }

                        val backupDao = database.telegramBackupDao()
                        if (toBackup.isNotEmpty()) {
                            Log.d("MediaSyncWorker", "Auto-queueing ${toBackup.size} items for backup...")
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
                        }

                        // Always check if there are ANY pending backups to resume
                        val pendingCount = backupDao.getPendingBackups().size
                        if (pendingCount > 0) {
                            Log.d("MediaSyncWorker", "Resuming backup for $pendingCount pending items.")
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
            }

            if (toDelete.isNotEmpty()) {
                Log.d("MediaSyncWorker", "Deleting ${toDelete.size} items from Room SSOT.")
                database.mediaDao().deleteByIds(toDelete)
            }

            val autoIndexSmartEnabled = settingsRepo.smartSearchAutoIndexFlow.first()
            val smartSearchEngine = com.inferno.gallery.data.ai.SmartSearchEngine.getInstance(applicationContext)
            val unindexedSmartCount = database.embeddingDao().getUnindexedMediaIds().size
            if (autoIndexSmartEnabled && smartSearchEngine.isModelDownloaded() && unindexedSmartCount > 0) {
                Log.d("MediaSyncWorker", "Auto-indexing $unindexedSmartCount images for Smart Search...")
                val indexRequest = androidx.work.OneTimeWorkRequestBuilder<SmartSearchIndexWorker>().build()
                androidx.work.WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "SmartSearchIndexWorker",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    indexRequest
                )
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
