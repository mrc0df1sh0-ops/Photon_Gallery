package com.inferno.gallery.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.OutputStream

/**
 * TDLib-backed Telegram Userbot provider.
 * Uses the user's own Telegram account (MTProto) to upload files.
 * Supports up to 2 GB file uploads.
 */
class UserbotProvider(private val context: Context) : TelegramBackupProvider {

    companion object {
        private const val TAG = "UserbotProvider"
        // These are public test/open-source credentials. Replace with your own from my.telegram.org
        const val API_ID = 94575
        const val API_HASH = "a3406de8d171bb422bb6ddf3bbd800e2"

        @Volatile
        private var INSTANCE: UserbotProvider? = null

        fun getInstance(context: Context): UserbotProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserbotProvider(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ── Auth State ──
    sealed class AuthState {
        data object Idle : AuthState()
        data object WaitPhone : AuthState()
        data object WaitCode : AuthState()
        data object WaitPassword : AuthState()
        data object Ready : AuthState()
        data class Error(val message: String) : AuthState()
        data object LoggingOut : AuthState()
        data object LoggedOut : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private var client: Client? = null
    private var savedMessagesChatId: Long = 0L
    private var targetChatId: Long = 0L // The chat ID where backups go (private channel or saved messages)

    // ── Client Initialization ──

    suspend fun initialize() {
        if (client != null) return

        withContext(Dispatchers.IO) {
            try {
                Client.setLogMessageHandler(0) { verbosityLevel, message ->
                    if (verbosityLevel <= 1) Log.e(TAG, "TDLib: $message")
                }

                client = Client.create({ update -> handleUpdate(update) }, null, null)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TDLib client", e)
                _authState.value = AuthState.Error("Failed to initialize: ${e.message}")
            }
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthState(update.authorizationState)
            }
            else -> {} // We don't need to handle other updates for backup
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val params = TdApi.SetTdlibParameters()
                params.databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                params.filesDirectory = File(context.cacheDir, "tdlib_files").absolutePath
                params.useMessageDatabase = false
                params.useChatInfoDatabase = true
                params.useFileDatabase = false
                params.useSecretChats = false
                params.apiId = API_ID
                params.apiHash = API_HASH
                params.systemLanguageCode = "en"
                params.deviceModel = "Android"
                params.systemVersion = android.os.Build.VERSION.RELEASE
                params.applicationVersion = "1.0"
                client?.send(params) { result ->
                    if (result is TdApi.Error) {
                        Log.e(TAG, "SetTdlibParameters error: ${result.message}")
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitPhone
            }

            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = AuthState.WaitCode
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = AuthState.WaitPassword
            }

            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthState.Ready
                // Fetch user info
                client?.send(TdApi.GetMe()) { result ->
                    if (result is TdApi.User) {
                        _userName.value = buildString {
                            append(result.firstName)
                            if (result.lastName.isNotBlank()) append(" ${result.lastName}")
                            val usernames = result.usernames
                            if (usernames != null && usernames.activeUsernames.isNotEmpty()) {
                                append(" (@${usernames.activeUsernames[0]})")
                            }
                        }
                        savedMessagesChatId = result.id
                    }
                }
            }

            is TdApi.AuthorizationStateLoggingOut -> {
                _authState.value = AuthState.LoggingOut
            }

            is TdApi.AuthorizationStateClosed -> {
                // If we were logging out, transition to LoggedOut; otherwise Idle
                val wasLoggingOut = _authState.value is AuthState.LoggingOut
                _authState.value = if (wasLoggingOut) AuthState.LoggedOut else AuthState.Idle
                client = null
            }

            else -> {}
        }
    }

    // ── Auth Actions ──

    fun sendPhoneNumber(phone: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        settings.allowFlashCall = false
        settings.allowMissedCall = false
        settings.isCurrentPhoneNumber = false
        settings.allowSmsRetrieverApi = false
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, settings)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    fun sendPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    fun logout() {
        _authState.value = AuthState.LoggingOut
        client?.send(TdApi.LogOut()) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Logout error: ${result.message}")
                _authState.value = AuthState.Idle
            }
        }
    }

    /**
     * Force-reset the userbot state: destroys the client, clears auth state,
     * and deletes TDLib database files so the next init starts fresh.
     */
    fun forceReset() {
        _authState.value = AuthState.Idle
        _userName.value = ""
        try {
            client?.send(TdApi.Close()) { }
        } catch (_: Exception) {}
        client = null
        targetChatId = 0L
        savedMessagesChatId = 0L
        // Delete TDLib database files for a clean fresh start
        try {
            val tdlibDir = java.io.File(context.filesDir, "tdlib")
            if (tdlibDir.exists()) tdlibDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete TDLib data", e)
        }
    }

    fun setTargetChatId(chatId: Long) {
        targetChatId = chatId
    }

    fun getEffectiveChatId(): Long = if (targetChatId != 0L) targetChatId else savedMessagesChatId

    // ── Chat Discovery ──

    data class UserbotChat(val id: Long, val title: String, val type: String)

    fun getPrivateChannels(callback: (List<UserbotChat>) -> Unit) {
        val chatList = mutableListOf<UserbotChat>()
        // Get chats using TDLib
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), 100)) { result ->
            if (result is TdApi.Chats) {
                var remaining = result.chatIds.size
                if (remaining == 0) {
                    callback(chatList)
                    return@send
                }
                for (chatId in result.chatIds) {
                    client?.send(TdApi.GetChat(chatId)) { chatResult ->
                        if (chatResult is TdApi.Chat) {
                            val chatType = chatResult.type
                            // Include private channels (supergroups that are channels) and saved messages
                            when {
                                chatType is TdApi.ChatTypeSupergroup && chatType.isChannel -> {
                                    chatList.add(UserbotChat(chatResult.id, chatResult.title, "channel"))
                                }
                                chatId == savedMessagesChatId -> {
                                    chatList.add(UserbotChat(chatResult.id, "Saved Messages", "saved"))
                                }
                            }
                        }
                        remaining--
                        if (remaining <= 0) {
                            callback(chatList.sortedWith(compareBy<UserbotChat> { it.type != "saved" }.thenBy { it.title }))
                        }
                    }
                }
            } else {
                callback(emptyList())
            }
        }
    }

    // ── TelegramBackupProvider Implementation ──

    override suspend fun uploadFile(file: File, mimeType: String): UploadResult {
        return withContext(Dispatchers.IO) {
            val chatId = getEffectiveChatId()
            if (chatId == 0L) throw IllegalStateException("No target chat ID set")

            val inputFile = TdApi.InputFileLocal(file.absolutePath)
            val document = TdApi.InputMessageDocument(inputFile, null, false, null)

            val sendMsg = TdApi.SendMessage()
            sendMsg.chatId = chatId
            sendMsg.inputMessageContent = document
            val result = sendSync(sendMsg)
            if (result is TdApi.Message) {
                val content = result.content
                val fileId = when (content) {
                    is TdApi.MessageDocument -> content.document.document.remote.id
                    is TdApi.MessagePhoto -> content.photo.sizes.lastOrNull()?.photo?.remote?.id ?: ""
                    is TdApi.MessageVideo -> content.video.video.remote.id
                    else -> ""
                }
                UploadResult(
                    fileId = fileId,
                    thumbFileId = fileId, // TDLib doesn't separate thumb
                    messageId = result.id
                )
            } else if (result is TdApi.Error) {
                throw TelegramApiException(result.code, result.message, "TDLib upload failed: ${result.message}")
            } else {
                throw java.io.IOException("Unexpected TDLib result: $result")
            }
        }
    }

    override suspend fun getFileUrl(fileId: String): String {
        // TDLib doesn't use URLs — it downloads files directly via the client
        // Return the fileId itself; the fetcher will handle it differently
        return "tdlib://$fileId"
    }

    override suspend fun downloadFile(fileId: String, output: OutputStream) {
        withContext(Dispatchers.IO) {
            // 1. Resolve remote file ID to a TDLib File object
            val remoteResult = sendSync(TdApi.GetRemoteFile(fileId, null))
            if (remoteResult is TdApi.Error) {
                throw java.io.IOException("Failed to resolve remote file: ${remoteResult.message}")
            }
            val remoteFile = remoteResult as TdApi.File
            val localFileId = remoteFile.id

            // 2. Download the file (synchronous = true blocks until complete)
            val downloadResult = sendSync(TdApi.DownloadFile(localFileId, 1, 0, 0, true))
            if (downloadResult is TdApi.Error) {
                throw java.io.IOException("TDLib download failed: ${downloadResult.message}")
            }

            // 3. Read the downloaded file from disk
            val downloadedFile = downloadResult as TdApi.File
            if (downloadedFile.local.isDownloadingCompleted) {
                File(downloadedFile.local.path).inputStream().use { input ->
                    input.copyTo(output)
                }
            } else {
                throw java.io.IOException("File download did not complete: ${downloadedFile.local.path}")
            }
        }
    }

    override suspend fun deleteMessage(messageId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val chatId = getEffectiveChatId()
            val result = sendSync(TdApi.DeleteMessages(chatId, longArrayOf(messageId), true))
            result is TdApi.Ok
        }
    }

    override fun isAuthenticated(): Boolean = _authState.value == AuthState.Ready

    override val maxFileSizeBytes: Long = 2L * 1024 * 1024 * 1024 // 2 GB

    // ── Sync Send Helper ──

    private suspend fun sendSync(function: TdApi.Function<*>): TdApi.Object {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client?.send(function) { result ->
                if (cont.isActive) {
                    cont.resumeWith(Result.success(result))
                }
            } ?: cont.resumeWith(Result.success(TdApi.Error(0, "Client not initialized")))
        }
    }
}
