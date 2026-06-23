package com.inferno.gallery.data.network

import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.inferno.gallery.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A custom Coil Fetcher that intercepts 'telegram://' URIs.
 * Supports both Bot API and Userbot (TDLib) modes.
 *
 * Bot API: Resolves the dynamic Telegram download URL and delegates to Coil's HTTP pipeline.
 * Userbot: Downloads via TDLib's native file download when no bot tokens are available.
 */
class TelegramCoilFetcher(
    private val uri: Uri,
    private val options: Options,
    private val settings: SettingsRepository,
    private val imageLoader: ImageLoader
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        try {
            val fileId = uri.host ?: return@withContext null
            val backupMode = settings.telegramBackupModeFlow.first()

            if (backupMode == "userbot") {
                // Try Bot API first (if bot tokens exist) since it's faster for thumbnails
                val botTokens = settings.telegramBotTokensFlow.first()
                if (botTokens.isNotEmpty()) {
                    return@withContext fetchViaBotApi(fileId, botTokens.first())
                }
                // No bot tokens — use TDLib native download
                return@withContext fetchViaTdLib(fileId)
            }

            // Bot API mode
            val botTokens = settings.telegramBotTokensFlow.first()
            if (botTokens.isEmpty()) return@withContext null
            return@withContext fetchViaBotApi(fileId, botTokens.first())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchViaBotApi(fileId: String, botToken: String): FetchResult? {
        val client = TelegramClient(botToken, "")
        val fileUrl = client.getFileUrl(fileId)

        val request = ImageRequest.Builder(options.context)
            .data(fileUrl)
            .size(options.size)
            .scale(options.scale)
            .build()

        val result = imageLoader.execute(request)
        return if (result is SuccessResult) {
            ImageFetchResult(
                image = result.image,
                isSampled = result.isSampled,
                dataSource = DataSource.NETWORK
            )
        } else {
            null
        }
    }

    /**
     * Download a file via TDLib (userbot-only mode, no bot tokens).
     * Downloads to a temp file, then delegates to Coil for decoding.
     */
    private suspend fun fetchViaTdLib(fileId: String): FetchResult? {
        val userbotProvider = UserbotProvider.getInstance(options.context)
        if (!userbotProvider.isAuthenticated()) return null

        val tempFile = File(options.context.cacheDir, "tdlib_thumb_${fileId.hashCode()}")
        try {
            tempFile.outputStream().use { output ->
                userbotProvider.downloadFile(fileId, output)
            }
            if (!tempFile.exists() || tempFile.length() == 0L) return null

            // Delegate the temp file to Coil for decoding
            val request = ImageRequest.Builder(options.context)
                .data(tempFile)
                .size(options.size)
                .scale(options.scale)
                .build()

            val result = imageLoader.execute(request)
            return if (result is SuccessResult) {
                ImageFetchResult(
                    image = result.image,
                    isSampled = result.isSampled,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } finally {
            tempFile.delete()
        }
    }

    class Factory(
        private val settings: SettingsRepository
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "telegram") return null
            return TelegramCoilFetcher(data, options, settings, imageLoader)
        }
    }
}

