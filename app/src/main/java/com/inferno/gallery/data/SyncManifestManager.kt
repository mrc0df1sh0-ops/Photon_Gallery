package com.inferno.gallery.data

import android.content.Context
import android.util.Log
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.TelegramBackupEntity
import com.inferno.gallery.data.network.TelegramClient
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SyncManifestManager {
    private const val TAG = "SyncManifestManager"
    private val manifestMutex = Mutex()

    suspend fun updateManifest(context: Context, botToken: String, chatId: String) {
        manifestMutex.withLock {
            val tempFile = java.io.File.createTempFile("sync_manifest_", ".json", context.cacheDir)
        try {
            val db = DatabaseProvider.getDatabase(context)
            val successfulBackups = db.telegramBackupDao().observeAllBackups().first()
                .filter { it.backupStatus == "SUCCESS" }

            val jsonArray = JSONArray()
            val allLocalMedia = db.mediaDao().getAllMedia()

            for (backup in successfulBackups) {
                val mediaItem = allLocalMedia.firstOrNull { it.id == backup.mediaId }
                val obj = JSONObject().apply {
                    put("fileId", backup.telegramFileId)
                    put("thumbId", backup.telegramThumbFileId)
                    put("messageId", backup.telegramMessageId ?: JSONObject.NULL)
                    put("timestamp", backup.backupTimestamp)
                    put("size", mediaItem?.size ?: 0L)
                    put("name", mediaItem?.name ?: "")
                    put("filePath", mediaItem?.filePath ?: "")
                    put("isVideo", mediaItem?.isVideo ?: false)
                }
                jsonArray.put(obj)
            }

            tempFile.writeText(jsonArray.toString(2))

            val client = TelegramClient(botToken, chatId)

            // 1. Upload new manifest
            val uploadResult = client.uploadDocument(tempFile, "application/json")

            // 2. Fetch the old pinned message ID
            val oldPinnedId = client.getPinnedMessageIdOrNull()

            // 3. Pin the new manifest message
            try {
                client.pinChatMessage(uploadResult.messageId)
            } catch (pinEx: Exception) {
                Log.e(TAG, "Failed to pin chat message: ${pinEx.message}")
            }

            // 4. Delete the old pinned message (to save space and reduce clutter)
            if (oldPinnedId != null) {
                try {
                    client.deleteMessage(oldPinnedId)
                } catch (delEx: Exception) {
                    Log.e(TAG, "Failed to delete old pinned manifest message $oldPinnedId: ${delEx.message}")
                }
            }

            Log.d(TAG, "Sync manifest updated successfully. New pinned message ID: ${uploadResult.messageId}")
        } catch (e: com.inferno.gallery.data.network.TelegramMigrationException) {
            Log.i(TAG, "Group chat migrated to supergroup! Updating chat ID to ${e.newChatId}")
            com.inferno.gallery.data.SettingsRepository.getInstance(context).updateTelegramChatId(e.newChatId.toString())
            // Retry once with new chat ID — temp file still exists here
            try {
                val retryClient = TelegramClient(botToken, e.newChatId.toString())
                val uploadResult = retryClient.uploadDocument(tempFile, "application/json")
                val oldPinnedId = retryClient.getPinnedMessageIdOrNull()
                try { retryClient.pinChatMessage(uploadResult.messageId) } catch (ex: Exception) {}
                if (oldPinnedId != null) {
                    try { retryClient.deleteMessage(oldPinnedId) } catch (ex: Exception) {}
                }
                Log.d(TAG, "Sync manifest retry updated successfully.")
            } catch (retryEx: Exception) {
                Log.e(TAG, "Error retrying sync manifest upload: ${retryEx.message}", retryEx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating sync manifest: ${e.message}", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
        }
    }

    /**
     * List of known video file extensions for reliable detection during restore.
     */
    private val VIDEO_EXTENSIONS = setOf(
        ".mp4", ".webm", ".mkv", ".3gp", ".mov", ".avi", ".ts", ".flv", ".m4v"
    )

    private fun isVideoFile(name: String): Boolean {
        val lower = name.lowercase()
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    /**
     * Restore backup records from the pinned sync manifest in the Telegram chat.
     *
     * IMPORTANT: This clears ALL existing backup records before restoring to prevent
     * conflicts when the user switches bots, changes chat IDs, or re-syncs.
     * The manifest is the single source of truth for what's backed up.
     */
    suspend fun restoreFromManifest(context: Context, botToken: String, chatId: String): Boolean {
        try {
            val client = TelegramClient(botToken, chatId)
            val fileId = client.getPinnedManifestFileIdOrNull() ?: return false
            val fileUrl = client.getFileUrl(fileId)
            val manifestText = client.downloadFileText(fileUrl)
            if (manifestText.isBlank()) return false

            val jsonArray = JSONArray(manifestText)
            val db = DatabaseProvider.getDatabase(context)
            val localMedia = db.mediaDao().getAllMedia()
            val backupDao = db.telegramBackupDao()

            // Clear all existing backup records to prevent conflicts from
            // previous bot/chat configurations. The manifest is the source of truth.
            backupDao.clearAllBackups()
            Log.d(TAG, "Cleared existing backup records before manifest restore.")

            var restoreCount = 0
            var cloudOnlyCount = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val fileIdVal = obj.getString("fileId")
                val thumbIdVal = obj.getString("thumbId")
                val messageIdVal = if (obj.isNull("messageId")) null else obj.getLong("messageId")
                val timestampVal = obj.getLong("timestamp")
                val sizeVal = obj.optLong("size", 0L)
                val nameVal = obj.optString("name", "")
                val filePathVal = obj.optString("filePath", "")
                // Use isVideo from manifest if present, otherwise detect from file extension
                val isVideoVal = if (obj.has("isVideo")) obj.getBoolean("isVideo") else isVideoFile(nameVal)

                // Match priority:
                // 1. Same filePath
                // 2. Same name and size
                var matchedMedia = localMedia.firstOrNull { it.filePath == filePathVal }
                    ?: localMedia.firstOrNull { it.name == nameVal && it.size == sizeVal }

                if (matchedMedia == null) {
                    // Cloud-only item — create a placeholder in core_media
                    val mimeType = when {
                        isVideoVal -> "video/mp4"
                        nameVal.endsWith(".png", true) -> "image/png"
                        nameVal.endsWith(".gif", true) -> "image/gif"
                        nameVal.endsWith(".webp", true) -> "image/webp"
                        else -> "image/jpeg"
                    }
                    val newMedia = com.inferno.gallery.data.db.CoreMediaEntity(
                        id = -(timestampVal + i), // Negative ID avoids collision with real MediaStore IDs
                        uriString = "telegram://$fileIdVal",
                        filePath = filePathVal,
                        bucketName = "telegram_cloud",
                        dateAdded = timestampVal / 1000,
                        dateModified = timestampVal / 1000,
                        size = sizeVal,
                        name = nameVal,
                        isVideo = isVideoVal,
                        durationMs = null,
                        mimeType = mimeType
                    )
                    db.mediaDao().insertAll(listOf(newMedia))
                    matchedMedia = newMedia
                    cloudOnlyCount++
                }

                val resolvedMedia = matchedMedia ?: continue
                backupDao.insertOrUpdate(
                    TelegramBackupEntity(
                        mediaId = resolvedMedia.id,
                        telegramFileId = fileIdVal,
                        telegramThumbFileId = thumbIdVal,
                        telegramMessageId = messageIdVal,
                        backupStatus = "SUCCESS",
                        backupTimestamp = timestampVal
                    )
                )
                restoreCount++
            }
            Log.i(TAG, "Restored $restoreCount backup records ($cloudOnlyCount cloud-only) from Telegram sync manifest.")
            return restoreCount > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from sync manifest: ${e.message}", e)
            return false
        }
    }
}
