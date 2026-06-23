package com.inferno.gallery.data.network

import java.io.File
import java.io.OutputStream

/**
 * Abstraction for Telegram backup operations.
 * Two implementations:
 *   1. BotApiProvider — uses the existing TelegramClient (Bot API, 50 MB limit)
 *   2. UserbotProvider — uses TDLib MTProto (Userbot, 2 GB limit)
 */
interface TelegramBackupProvider {

    /** Upload a file. Returns an UploadResult with file_id and message_id. */
    suspend fun uploadFile(file: File, mimeType: String): UploadResult

    /** Get a direct download URL for a given file_id. */
    suspend fun getFileUrl(fileId: String): String

    /** Download a file's raw bytes to an output stream. */
    suspend fun downloadFile(fileId: String, output: OutputStream)

    /** Delete a message by its ID. */
    suspend fun deleteMessage(messageId: Long): Boolean

    /** Check if authentication is complete and the provider is ready to use. */
    fun isAuthenticated(): Boolean

    /** The maximum file size (in bytes) this provider supports. */
    val maxFileSizeBytes: Long
}

/**
 * Wraps the existing TelegramClient (Bot API) to implement TelegramBackupProvider.
 * This is a thin adapter — all logic stays in TelegramClient.
 */
class BotApiProvider(
    private val client: TelegramClient
) : TelegramBackupProvider {

    override suspend fun uploadFile(file: File, mimeType: String): UploadResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.uploadDocument(file, mimeType)
        }
    }

    override suspend fun getFileUrl(fileId: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.getFileUrl(fileId)
        }
    }

    override suspend fun downloadFile(fileId: String, output: OutputStream) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = client.getFileUrl(fileId)
            client.downloadFileStream(url, output)
        }
    }

    override suspend fun deleteMessage(messageId: Long): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.deleteMessage(messageId)
        }
    }

    override fun isAuthenticated(): Boolean = true // Bot API is always authenticated if token exists

    override val maxFileSizeBytes: Long = 50L * 1024 * 1024 // 50 MB
}
