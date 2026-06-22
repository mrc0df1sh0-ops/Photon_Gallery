package com.inferno.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Log
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.TelegramBackupEntity
import com.inferno.gallery.data.network.RateLimitException
import com.inferno.gallery.data.network.TelegramClient
import com.inferno.gallery.data.network.TelegramMigrationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class TelegramBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TelegramBackupWorker"
        private const val NOTIFICATION_ID = 77
        private const val CHANNEL_ID = "telegram_backup_channel"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Preparing backup…")

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val context = applicationContext
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cloud Backup",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows progress while backing up photos to Telegram" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Telegram Cloud Backup")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun isBatteryTooLow(context: Context): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter) ?: return false
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return batteryPct < 35 && !isCharging
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = DatabaseProvider.getDatabase(applicationContext)
        val settings = SettingsRepository.getInstance(applicationContext)

        val batteryPauseEnabled = settings.telegramAutoBackupBatteryLowPauseFlow.first()
        if (batteryPauseEnabled && isBatteryTooLow(applicationContext)) {
            Log.d(TAG, "Battery level is low (< 35%) and not charging. Rescheduling backup worker...")
            return@withContext Result.retry()
        }

        val botTokens = settings.telegramBotTokensFlow.first()
        val chatId = settings.telegramChatIdFlow.first()
        val stripLocation = settings.telegramStripLocationFlow.first()

        if (botTokens.isEmpty() || chatId.isBlank()) {
            Log.d(TAG, "Telegram credentials missing.")
            return@withContext Result.success()
        }

        val backupDao = database.telegramBackupDao()
        val mediaDao = database.mediaDao()
        
        // Retry FAILED backups (reset to PENDING so they get re-processed)
        val failedBackups = backupDao.getFailedBackups()
        if (failedBackups.isNotEmpty()) {
            Log.d(TAG, "Retrying ${failedBackups.size} previously failed backups.")
            for (failed in failedBackups) {
                backupDao.insertOrUpdate(failed.copy(backupStatus = "PENDING"))
            }
        }

        val pendingBackups = backupDao.getPendingBackups()
        if (pendingBackups.isEmpty()) {
            Log.d(TAG, "No pending backups found.")
            return@withContext Result.success()
        }

        // Pre-load all media into a map ONCE (avoids per-item full table scans)
        val allMediaMap = mediaDao.getAllMedia().associateBy { it.id }

        Log.d(TAG, "Starting Telegram backup. Pending count: ${pendingBackups.size}, Bots count: ${botTokens.size}")

        // Show foreground notification with progress
        val totalPending = pendingBackups.size
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        setForeground(createForegroundInfo("Backing up 0/$totalPending photos…"))

        // Thread-safe channel to distribute pending media tasks
        val channel = Channel<TelegramBackupEntity>(pendingBackups.size)
        for (backup in pendingBackups) {
            channel.trySend(backup)
        }
        channel.close()

        val sharedChatId = AtomicReference(chatId)

        // Spawn parallel coroutines, one for each configured bot token (maximum of 2 active concurrent bots to avoid rate limits and spam flags)
        val maxConcurrency = minOf(botTokens.size, 2)
        val activeTokens = botTokens.take(maxConcurrency)
        val jobs = activeTokens.map { botToken ->
            launch {
                for (backup in channel) {
                    if (isStopped) {
                        Log.d(TAG, "Worker stopped by OS.")
                        break
                    }

                    // Verify the backup is still pending in the database (not cancelled/removed)
                    val currentBackup = backupDao.getBackupForMedia(backup.mediaId)
                    if (currentBackup == null || currentBackup.backupStatus != "PENDING") {
                        Log.d(TAG, "Backup for media ${backup.mediaId} is no longer pending or was cancelled. Skipping.")
                        continue
                    }

                    // Fetch the media metadata from pre-loaded map
                    val mediaEntity = allMediaMap[backup.mediaId]
                    if (mediaEntity == null) {
                        Log.w(TAG, "Media item ${backup.mediaId} not found in core_media, skipping.")
                        backupDao.deleteBackup(backup.mediaId)
                        continue
                    }
                    if (mediaEntity.isVideo) {
                        Log.w(TAG, "Video backup not supported: ${mediaEntity.name}. Removing from queue.")
                        backupDao.deleteBackup(backup.mediaId)
                        continue
                    }

                    if (mediaEntity.size > 50 * 1024 * 1024L) {
                        Log.w(TAG, "Media item ${mediaEntity.name} exceeds 50MB limit (${mediaEntity.size} bytes). Marking as FAILED.")
                        backupDao.insertOrUpdate(
                            TelegramBackupEntity(
                                mediaId = mediaEntity.id,
                                telegramFileId = null,
                                telegramThumbFileId = null,
                                backupStatus = "FAILED",
                                backupTimestamp = System.currentTimeMillis()
                            )
                        )
                        continue
                    }

                    val uri = Uri.parse(mediaEntity.uriString)
                    var tempFile: File? = null

                    try {
                        // 1. Create a temp file in cache directory to protect the original file
                        tempFile = File(applicationContext.cacheDir, "backup_${mediaEntity.id}_${mediaEntity.name}")
                        
                        applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        } ?: throw java.io.FileNotFoundException("Could not open input stream for local media URI")

                        // GPS Location Metadata Stripping (for images only)
                        if (stripLocation && !mediaEntity.isVideo) {
                            try {
                                val exifInterface = androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)
                                val gpsAttributes = listOf(
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP,
                                    androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD
                                )
                                for (attr in gpsAttributes) {
                                    exifInterface.setAttribute(attr, null)
                                }
                                exifInterface.saveAttributes()
                                Log.d(TAG, "Stripped GPS metadata from ${mediaEntity.name}")
                            } catch (ex: Exception) {
                                Log.e(TAG, "GPS metadata stripping failed: ${ex.message}")
                            }
                        }

                        // 2. Upload using Telegram Bot API with current chat ID
                        val mimeType = mediaEntity.mimeType ?: if (mediaEntity.isVideo) "video/mp4" else "image/jpeg"
                        Log.d(TAG, "Uploading ${mediaEntity.name} via bot ${botToken.take(6)}... (${tempFile.length()} bytes)")
                        
                        val client = TelegramClient(botToken, sharedChatId.get())
                        val uploadResult = client.uploadDocument(tempFile, mimeType)

                        // 3. Update status to success in database
                        backupDao.insertOrUpdate(
                            TelegramBackupEntity(
                                mediaId = mediaEntity.id,
                                telegramFileId = uploadResult.fileId,
                                telegramThumbFileId = uploadResult.thumbFileId,
                                telegramMessageId = uploadResult.messageId,
                                backupStatus = "SUCCESS",
                                backupTimestamp = System.currentTimeMillis()
                            )
                        )
                        val done = completedCount.incrementAndGet()
                        Log.d(TAG, "Upload success for ${mediaEntity.name}. file_id: ${uploadResult.fileId} ($done/$totalPending)")
                        setForeground(createForegroundInfo("Backing up $done/$totalPending photos…"))

                        // Periodic manifest sync every 10 uploads for crash protection
                        if (done % 10 == 0) {
                            try {
                                Log.d(TAG, "Periodic manifest sync at $done/$totalPending uploads")
                                com.inferno.gallery.data.SyncManifestManager.updateManifest(
                                    applicationContext,
                                    botToken,
                                    sharedChatId.get()
                                )
                            } catch (manifestEx: Exception) {
                                Log.w(TAG, "Periodic manifest sync failed (non-fatal): ${manifestEx.message}")
                            }
                        }

                        // Add randomized delay (jitter) of 5 to 9 seconds to strictly comply with Telegram's 20-messages-per-minute per chat limit
                        val jitterDelay = (5000L..9000L).random()
                        delay(jitterDelay)

                    } catch (e: RateLimitException) {
                        val waitTime = e.retryAfterSeconds + 5 // Add 5 second buffer to be completely safe
                        Log.w(TAG, "Telegram Rate limit hit! Need to wait $waitTime seconds.")
                        delay(waitTime * 1000L)
                        break // Break this worker to pause requests from this bot

                    } catch (e: TelegramMigrationException) {
                        Log.i(TAG, "Group chat migrated to supergroup! Updating chat ID to ${e.newChatId}")
                        settings.updateTelegramChatId(e.newChatId.toString())
                        sharedChatId.set(e.newChatId.toString())
                        
                        // Retry the upload for this specific item once
                        try {
                            Log.d(TAG, "Retrying upload of ${mediaEntity.name} with new chat ID...")
                            val retryClient = TelegramClient(botToken, e.newChatId.toString())
                            val uploadResult = retryClient.uploadDocument(tempFile!!, mediaEntity.mimeType ?: if (mediaEntity.isVideo) "video/mp4" else "image/jpeg")
                            backupDao.insertOrUpdate(
                                TelegramBackupEntity(
                                    mediaId = mediaEntity.id,
                                    telegramFileId = uploadResult.fileId,
                                    telegramThumbFileId = uploadResult.thumbFileId,
                                    telegramMessageId = uploadResult.messageId,
                                    backupStatus = "SUCCESS",
                                    backupTimestamp = System.currentTimeMillis()
                                )
                            )
                            Log.d(TAG, "Retry upload success for ${mediaEntity.name}. file_id: ${uploadResult.fileId}")
                            
                            // Add randomized delay (jitter) of 5 to 9 seconds to strictly comply with Telegram's limits
                            val jitterDelay = (5000L..9000L).random()
                            delay(jitterDelay)
                        } catch (retryException: Exception) {
                            Log.e(TAG, "Retry upload failed for ${mediaEntity.name}: ${retryException.message}", retryException)
                            backupDao.insertOrUpdate(
                                TelegramBackupEntity(
                                    mediaId = mediaEntity.id,
                                    telegramFileId = null,
                                    telegramThumbFileId = null,
                                    backupStatus = "FAILED",
                                    backupTimestamp = System.currentTimeMillis()
                                )
                            )
                        }

                    } catch (e: java.net.SocketTimeoutException) {
                        // Timeout during upload (common for large videos) — retry once
                        Log.w(TAG, "Timeout uploading ${mediaEntity.name}, retrying once...")
                        try {
                            val retryClient = TelegramClient(botToken, sharedChatId.get())
                            val mimeType2 = mediaEntity.mimeType ?: if (mediaEntity.isVideo) "video/mp4" else "image/jpeg"
                            val uploadResult = retryClient.uploadDocument(tempFile!!, mimeType2)
                            backupDao.insertOrUpdate(
                                TelegramBackupEntity(
                                    mediaId = mediaEntity.id,
                                    telegramFileId = uploadResult.fileId,
                                    telegramThumbFileId = uploadResult.thumbFileId,
                                    telegramMessageId = uploadResult.messageId,
                                    backupStatus = "SUCCESS",
                                    backupTimestamp = System.currentTimeMillis()
                                )
                            )
                            val done = completedCount.incrementAndGet()
                            Log.d(TAG, "Retry upload success for ${mediaEntity.name} ($done/$totalPending)")
                            setForeground(createForegroundInfo("Backing up $done/$totalPending photos…"))
                            val jitterDelay = (5000L..9000L).random()
                            delay(jitterDelay)
                        } catch (retryEx: Exception) {
                            Log.e(TAG, "Retry also failed for ${mediaEntity.name}: ${retryEx.message}")
                            backupDao.insertOrUpdate(
                                TelegramBackupEntity(
                                    mediaId = mediaEntity.id,
                                    telegramFileId = null,
                                    telegramThumbFileId = null,
                                    backupStatus = "FAILED",
                                    backupTimestamp = System.currentTimeMillis()
                                )
                            )
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to backup media ${mediaEntity.name}: ${e.message}", e)
                        backupDao.insertOrUpdate(
                            TelegramBackupEntity(
                                mediaId = mediaEntity.id,
                                telegramFileId = null,
                                telegramThumbFileId = null,
                                backupStatus = "FAILED",
                                backupTimestamp = System.currentTimeMillis()
                            )
                        )
                    } finally {
                        tempFile?.let {
                            if (it.exists()) {
                                it.delete()
                            }
                        }
                    }
                }
            }
        }

        jobs.joinAll()

        // 4. Update the pinned sync manifest in the chat
        if (activeTokens.isNotEmpty() && sharedChatId.get().isNotBlank()) {
            com.inferno.gallery.data.SyncManifestManager.updateManifest(
                applicationContext,
                activeTokens.first(),
                sharedChatId.get()
            )
        }

        return@withContext Result.success()
    }
}
