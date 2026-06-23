@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.inferno.gallery.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.TelegramBackupEntity
import com.inferno.gallery.data.db.CloudMediaItem
import com.inferno.gallery.data.db.MediaWithBackup
import com.inferno.gallery.data.LocalMediaRepository
import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.DockStyle
import com.inferno.gallery.data.FavoritesManager
import com.inferno.gallery.data.VaultAuthManager
import com.inferno.gallery.data.BucketNames
import com.inferno.gallery.data.MediaQueryBuilder
import android.util.Log

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flatMapLatest
import androidx.paging.PagingData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.paging.insertSeparators
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import com.inferno.gallery.workers.OcrIndexWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

import androidx.compose.runtime.Immutable

@Immutable
data class GalleryItem(
    val id: String,
    val uri: Uri,
    val bucketName: String,
    val dateAdded: Long,
    val size: Long,
    val name: String,
    val dateModified: Long,
    val path: String,
    val isVideo: Boolean = false,
    val durationMs: Long? = null,
    val searchScore: Float? = null,
    val telegramFileId: String? = null,
    val telegramThumbFileId: String? = null,
    /** Pre-computed on IO: whether the local file exists on disk. */
    val localExists: Boolean = true,
    /** Pre-computed on IO: the URI to use for thumbnail loading (local or telegram). */
    val resolvedUri: Uri = uri,
    val pHash: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fileHash: String? = null
)

data class BackupProgress(
    val total: Int,
    val pending: Int,
    val successful: Int,
    val failed: Int,
    val progress: Float
)

sealed class GalleryListItem {
    data class Item(val galleryItem: GalleryItem) : GalleryListItem()
    data class Header(val title: String) : GalleryListItem()
}

enum class SortOrder {
    NewToOld,
    OldToNew,
    SmallToBig,
    BigToSmall,
    NameAsc
}

enum class ViewMode {
    Immersive,
    Grouped
}

@Immutable
data class AlbumBucket(
    val bucketName: String,
    val coverUri: Uri,
    val itemCount: Int,
    val totalSizeBytes: Long = 0L,
    val maxDate: Long = 0L,
    val coverUris: List<Uri> = emptyList()
)

@Immutable
data class DuplicateGroup(
    val pHash: Long,
    val items: List<GalleryItem>
)

sealed class DuplicateScanState {
    data object Idle : DuplicateScanState()
    data class Scanning(val processed: Int, val total: Int) : DuplicateScanState()
    data object Done : DuplicateScanState()
}

enum class SearchMode { SMART, FTS }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalMediaRepository(application.contentResolver)
    val settingsRepository = SettingsRepository.getInstance(application)
    private val favoritesManager = FavoritesManager(application)
    private val database = DatabaseProvider.getDatabase(application)

    // ── Toast Event Channel ──
    private val _toastEvent = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    private fun showToast(message: String) {
        _toastEvent.trySend(message)
    }

    /** True while the initial fast sync is running — used to show loading UI on first launch. */
    private val _isInitialSyncRunning = MutableStateFlow(false)
    val isInitialSyncRunning: StateFlow<Boolean> = _isInitialSyncRunning.asStateFlow()

    private var mediaStoreObserverJob: kotlinx.coroutines.Job? = null

    private val mediaStoreObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            mediaStoreObserverJob?.cancel()
            mediaStoreObserverJob = viewModelScope.launch {
                kotlinx.coroutines.delay(1000) // Debounce rapid MediaStore changes
                Log.d("GalleryViewModel", "MediaStore change detected, enqueueing MediaSyncWorker...")
                val syncRequest = OneTimeWorkRequestBuilder<com.inferno.gallery.workers.MediaSyncWorker>().build()
                WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "MediaSyncWorker_Foreground", // Use a different name from the background auto-backup one to allow both or just replace
                    ExistingWorkPolicy.REPLACE, 
                    syncRequest
                )
            }
        }
    }

    init {
        // Fast initial sync: if Room is empty, bulk-insert MediaStore metadata
        // directly on IO. This populates the grid in ~1s instead of waiting
        // ~10s for WorkManager to schedule and execute MediaSyncWorker.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = database.mediaDao().getAllMedia().size
                if (count == 0) {
                    _isInitialSyncRunning.value = true
                    Log.d("GalleryViewModel", "Room empty — running fast initial sync")
                    val mediaList = repository.getImagesListForSync()
                    if (mediaList.isNotEmpty()) {
                        val entities = mediaList.map { media ->
                            com.inferno.gallery.data.db.CoreMediaEntity(
                                id = media.id,
                                uriString = media.uri.toString(),
                                filePath = media.path,
                                bucketName = media.bucketName,
                                dateAdded = media.dateAdded,
                                dateModified = media.dateModified,
                                size = media.size,
                                name = media.name,
                                mimeType = null,
                                isVideo = media.isVideo,
                                durationMs = media.durationMs,
                                isIndexedOcr = false,
                                pHash = null,
                                latitude = null,
                                longitude = null,
                                fileHash = null
                            )
                        }
                        database.mediaDao().insertAll(entities)
                        Log.d("GalleryViewModel", "Fast sync complete: ${entities.size} items inserted")
                    }
                    _isInitialSyncRunning.value = false
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Fast sync failed: ${e.message}")
                _isInitialSyncRunning.value = false
            }
        }

        getApplication<Application>().contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )
        getApplication<Application>().contentResolver.registerContentObserver(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaStoreObserver)
    }

    /**
     * Resolves localExists and resolvedUri for a GalleryItem.
     * Skips File.exists() entirely for local-only items (no Telegram backup)
     * to avoid unnecessary disk I/O in hot paths like paging.
     */
    private fun resolveItemFields(
        uri: Uri,
        path: String,
        telegramThumbFileId: String?,
        telegramFileId: String?
    ): Pair<Boolean, Uri> {
        // Fast path: no Telegram backup → always local, skip disk check
        if (telegramThumbFileId == null && telegramFileId == null) {
            return true to uri
        }
        // Slow path: has Telegram backup → check if local file still exists
        val exists = java.io.File(path).exists()
        val resolved = when {
            telegramThumbFileId != null && !exists -> Uri.parse("telegram://$telegramThumbFileId")
            telegramFileId != null && !exists -> Uri.parse("telegram://$telegramFileId")
            else -> uri
        }
        return exists to resolved
    }

    // ── Shared SQL Query Builder ──
    // Delegates to MediaQueryBuilder for testability
    private fun buildMediaConditions(
        bucket: String?,
        filterIndex: Int,
        excluded: Set<String> = emptySet(),
        favIds: Set<String> = emptySet(),
        ftsIds: List<String> = emptyList(),
        smartIds: List<String> = emptyList()
    ): MediaQueryBuilder.QueryConditions {
        return MediaQueryBuilder.buildMediaConditions(bucket, filterIndex, excluded, favIds, ftsIds, smartIds)
    }

    private fun buildWhereClause(qc: MediaQueryBuilder.QueryConditions): String {
        return MediaQueryBuilder.buildWhereClause(qc)
    }

    private fun buildOrderClause(order: SortOrder): String = MediaQueryBuilder.buildOrderClause(order.name)


    // ── Excluded Folders ──
    val excludedFolders: StateFlow<Set<String>> = settingsRepository.excludedFoldersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun toggleExcludedFolder(bucketName: String) {
        viewModelScope.launch {
            val current = excludedFolders.value.toMutableSet()
            if (current.contains(bucketName)) current.remove(bucketName) else current.add(bucketName)
            settingsRepository.updateExcludedFolders(current)
        }
    }

    // ── Private Space ──
    val vaultAuthManager = VaultAuthManager()
    private val vaultRepository = com.inferno.gallery.data.VaultRepository(application, database.vaultDao())

    val vaultItems: StateFlow<List<com.inferno.gallery.data.db.VaultMediaEntity>> = vaultRepository.vaultItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val vaultItemCount: StateFlow<Int> = vaultRepository.vaultCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val isVaultUnlocked: StateFlow<Boolean> = vaultAuthManager.isAuthenticated

    fun hideMedia(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = vaultRepository.hideMedia(uris)
            showToast("$count item${if (count != 1) "s" else ""} hidden")
        }
    }

    fun unhideMedia(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = vaultRepository.unhideMedia(ids)
            showToast("$count item${if (count != 1) "s" else ""} restored")
        }
    }

    fun deleteFromVault(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultRepository.deleteFromVault(ids)
            showToast("${ids.size} item${if (ids.size != 1) "s" else ""} permanently deleted")
        }
    }

    fun getVaultFileUri(entity: com.inferno.gallery.data.db.VaultMediaEntity): Uri {
        return vaultRepository.getVaultFileUri(entity)
    }

    private val _isScrollDockVisible = MutableStateFlow(true)
    val isScrollDockVisible: StateFlow<Boolean> = _isScrollDockVisible.asStateFlow()

    fun setScrollDockVisible(visible: Boolean) {
        _isScrollDockVisible.value = visible
    }


    val favoriteIds: StateFlow<Set<String>> = favoritesManager.favoritesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val gridAutoPlay: StateFlow<Boolean> = settingsRepository.gridAutoPlayFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun toggleGridAutoPlay() {
        viewModelScope.launch {
            settingsRepository.toggleGridAutoPlay(gridAutoPlay.value)
        }
    }

    val onboardingCompleted: StateFlow<Boolean> = settingsRepository.onboardingCompletedFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.updateOnboardingCompleted(true)
        }
    }

    val trashCount: StateFlow<Int> = database.mediaDao().observeTrashCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )



    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            favoritesManager.toggleFavorite(id)
        }
    }



    val cloudMedia: StateFlow<List<CloudMediaItem>> = database.telegramBackupDao().observeCloudMedia()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val failedBackups: StateFlow<List<CloudMediaItem>> = database.telegramBackupDao().observeFailedBackups()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun retryAllFailedBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            database.telegramBackupDao().retryAllFailedBackups()
        }
    }

    val favoriteMedia: StateFlow<List<GalleryItem>> = favoritesManager.favoritesFlow.flatMapLatest { favs ->
        if (favs.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else database.mediaDao().observeMediaByIds(favs.mapNotNull { it.toLongOrNull() })
    }.combine(database.telegramBackupDao().observeCloudMedia()) { entities, cloudItems ->
        val cloudMap = cloudItems.associateBy { it.id }
        entities.map { entity ->
            val cloudItem = cloudMap[entity.id]
            val uri = Uri.parse(entity.uriString)
            val (exists, resolved) = resolveItemFields(uri, entity.filePath, cloudItem?.telegramThumbFileId, cloudItem?.telegramFileId)
            GalleryItem(
                id = entity.id.toString(),
                uri = uri,
                bucketName = entity.bucketName,
                dateAdded = entity.dateAdded,
                size = entity.size,
                name = entity.name,
                dateModified = entity.dateModified,
                path = entity.filePath,
                isVideo = entity.isVideo,
                durationMs = entity.durationMs,
                telegramFileId = cloudItem?.telegramFileId,
                telegramThumbFileId = cloudItem?.telegramThumbFileId,
                localExists = exists,
                resolvedUri = resolved
            )
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _detailMedia = MutableStateFlow<List<GalleryItem>>(emptyList())
    val detailMedia: StateFlow<List<GalleryItem>> = _detailMedia.asStateFlow()

    fun loadDetailMedia(mediaId: String, bucketName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (bucketName == BucketNames.SEARCH_TEXT) {
                _detailMedia.value = _ftsSearchResults.value
                return@launch
            }
            if (bucketName == BucketNames.SEARCH_SMART) {
                _detailMedia.value = _smartSearchResults.value
                return@launch
            }
            if (bucketName == BucketNames.FAVORITES) {
                _detailMedia.value = favoriteMedia.value
                return@launch
            }

            // Build the SQLite query for the bucket
            val filterIndex = selectedFilterIndex.value
            val order = sortOrder.value
            
            val qc = buildMediaConditions(bucket = bucketName, filterIndex = filterIndex)
            val queryString = "SELECT cm.*, tb.telegramFileId, tb.telegramThumbFileId, tb.backupStatus FROM core_media cm LEFT JOIN telegram_backups tb ON cm.id = tb.mediaId " +
                buildWhereClause(qc) + buildOrderClause(order)

            val query = androidx.sqlite.db.SimpleSQLiteQuery(queryString, qc.args.toTypedArray())
            val entities = try {
                database.mediaDao().getMediaRaw(query)
            } catch (e: Exception) {
                emptyList()
            }
            val items = entities.map { entity ->
                val uri = Uri.parse(entity.uriString)
                val (exists, resolved) = resolveItemFields(uri, entity.filePath, entity.telegramThumbFileId, entity.telegramFileId)
                GalleryItem(
                    id = entity.id.toString(),
                    uri = uri,
                    bucketName = entity.bucketName,
                    dateAdded = entity.dateAdded,
                    size = entity.size,
                    name = entity.name,
                    dateModified = entity.dateModified,
                    path = entity.filePath,
                    isVideo = entity.isVideo,
                    durationMs = entity.durationMs,
                    telegramFileId = entity.telegramFileId,
                    telegramThumbFileId = entity.telegramThumbFileId,
                    localExists = exists,
                    resolvedUri = resolved
                )
            }
            
            val containsTarget = items.any { it.id == mediaId }
            if (items.isNotEmpty() && containsTarget) {
                _detailMedia.value = items
            } else {
                // Fetch the clicked item as a fallback
                val idLong = mediaId.toLongOrNull()
                val entity = idLong?.let { database.mediaDao().getMediaById(it) }
                if (entity != null) {
                    val cloudItem = database.telegramBackupDao().getBackupForMedia(entity.id)
                    val uri = Uri.parse(entity.uriString)
                    val (exists, resolved) = resolveItemFields(uri, entity.filePath, cloudItem?.telegramThumbFileId, cloudItem?.telegramFileId)
                    val fallbackItem = GalleryItem(
                        id = entity.id.toString(),
                        uri = uri,
                        bucketName = entity.bucketName,
                        dateAdded = entity.dateAdded,
                        size = entity.size,
                        name = entity.name,
                        dateModified = entity.dateModified,
                        path = entity.filePath,
                        isVideo = entity.isVideo,
                        durationMs = entity.durationMs,
                        telegramFileId = cloudItem?.telegramFileId,
                        telegramThumbFileId = cloudItem?.telegramThumbFileId,
                        localExists = exists,
                        resolvedUri = resolved
                    )
                    _detailMedia.value = listOf(fallbackItem)
                } else {
                    _detailMedia.value = emptyList()
                }
            }
        }
    }

    fun renameMedia(
        context: android.content.Context,
        item: GalleryItem,
        newName: String,
        onSecurityException: (android.app.PendingIntent) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = item.uri
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                
                try {
                    val rows = context.contentResolver.update(uri, values, null, null)
                    if (rows > 0) {
                        database.mediaDao().updateMediaName(item.id.toLong(), newName)
                        
                        val currentDetailMedia = _detailMedia.value
                        val updatedList = currentDetailMedia.map {
                            if (it.id == item.id) {
                                it.copy(name = newName)
                            } else {
                                it
                            }
                        }
                        _detailMedia.value = updatedList
                        
                        withContext(Dispatchers.Main) {
                            showToast("Renamed to $newName")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showToast("Failed to rename: 0 rows updated")
                        }
                    }
                } catch (securityException: SecurityException) {
                    val recoverable = securityException as? android.app.RecoverableSecurityException
                    if (recoverable != null) {
                        withContext(Dispatchers.Main) {
                            onSecurityException(recoverable.userAction.actionIntent)
                        }
                    } else {
                        val pendingIntent = android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                        withContext(Dispatchers.Main) {
                            onSecurityException(pendingIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error renaming: ${e.message}")
                }
            }
        }
    }

    val cloudMediaIds: StateFlow<Set<String>> = database.telegramBackupDao().observeCloudMedia().map { list ->
        list.map { it.id.toString() }.toSet()
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    val pendingBackupsCount: StateFlow<Int> = database.telegramBackupDao().observePendingBackupsCount()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val backupProgress: StateFlow<BackupProgress?> = database.telegramBackupDao().observeAllBackups().map { backups ->
        val pendingItems = backups.filter { it.backupStatus == "PENDING" }
        if (pendingItems.isEmpty()) {
            null
        } else {
            val minPendingTimestamp = pendingItems.minOf { it.backupTimestamp }
            val activeCompleted = backups.filter {
                (it.backupStatus == "SUCCESS" || it.backupStatus == "FAILED") && it.backupTimestamp >= minPendingTimestamp
            }
            val successful = activeCompleted.count { it.backupStatus == "SUCCESS" }
            val failed = activeCompleted.count { it.backupStatus == "FAILED" }
            val pendingCount = pendingItems.size
            val total = pendingCount + successful + failed
            val progress = if (total > 0) successful.toFloat() / total.toFloat() else 0f
            BackupProgress(
                total = total,
                pending = pendingCount,
                successful = successful,
                failed = failed,
                progress = progress
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    suspend fun getTelegramFileUrl(fileId: String): String? {
        if (fileId.isBlank()) return null
        val token = settingsRepository.telegramBotTokensFlow.first().firstOrNull() ?: return null
        val chatId = settingsRepository.telegramChatIdFlow.first()
        if (token.isBlank() || chatId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                com.inferno.gallery.data.network.TelegramClient(token, chatId).getFileUrl(fileId)
            }.getOrNull()
        }
    }

    fun downloadCloudMedia(context: android.content.Context, item: GalleryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileId = item.telegramFileId ?: return@launch
                val fileUrl = getTelegramFileUrl(fileId) ?: throw Exception("Failed to resolve URL")
                
                val collection = if (item.isVideo) {
                    android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                    val mimeType = if (item.isVideo) "video/mp4" else "image/jpeg"
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PhotonGallery")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values) ?: throw Exception("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    val token = settingsRepository.telegramBotTokensFlow.first().first()
                    val client = com.inferno.gallery.data.network.TelegramClient(token, "")
                    client.downloadFileStream(fileUrl, outputStream)
                }

                values.clear()
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                withContext(Dispatchers.Main) {
                    showToast("Download complete")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Download failed: ${e.message}")
                }
            }
        }
    }

    val sortOrder: StateFlow<SortOrder> = settingsRepository.sortOrderFlow.map {
        try {
            SortOrder.valueOf(it)
        } catch (e: Exception) {
            SortOrder.NewToOld
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SortOrder.NewToOld
    )

    val albumSortOrder: StateFlow<SortOrder> = settingsRepository.albumSortOrderFlow.map {
        try {
            SortOrder.valueOf(it)
        } catch (e: Exception) {
            SortOrder.NameAsc
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SortOrder.NameAsc
    )

    val viewMode: StateFlow<ViewMode> = settingsRepository.viewModeFlow.map {
        try {
            ViewMode.valueOf(it)
        } catch (e: Exception) {
            ViewMode.Immersive
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewMode.Immersive
    )

    val dockStyle: StateFlow<DockStyle> = settingsRepository.dockStyleFlow.map {
        try {
            DockStyle.valueOf(it)
        } catch (e: Exception) {
            DockStyle.PILL
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DockStyle.PILL
    )

    private val _currentBucket = MutableStateFlow<String?>(null)

    fun setBucket(bucket: String?) {
        _currentBucket.value = bucket
    }

    val selectedFilterIndex: StateFlow<Int> = settingsRepository.selectedFilterIndexFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _gridCellsCount = MutableStateFlow(4)
    val gridCellsCount: StateFlow<Int> = _gridCellsCount.asStateFlow()

    private var saveGridCellsJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            _gridCellsCount.value = settingsRepository.gridCellsCountFlow.first()
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val searchEngine = com.inferno.gallery.data.ai.SmartSearchEngine.getInstance(application)
                if (searchEngine.isModelDownloaded()) {
                    Log.d("GalleryViewModel", "Pre-warming Smart Search Engine...")
                    searchEngine.loadModel()
                    Log.d("GalleryViewModel", "Smart Search Engine pre-warmed successfully.")
                }
            } catch (e: Exception) {
                Log.w("GalleryViewModel", "Failed to pre-warm Smart Search Engine: ${e.message}")
            }
        }
    }

    val thumbnailCornerRadius: StateFlow<Float> = settingsRepository.thumbnailCornerRadiusFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    val cacheThumbnailsEnabled: StateFlow<Boolean> = settingsRepository.cacheThumbnailsEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setThumbnailCornerRadius(radius: Float) {
        viewModelScope.launch {
            settingsRepository.updateThumbnailCornerRadius(radius)
        }
    }

    val pagedMediaRaw: Flow<PagingData<GalleryItem>> = combine(
        _currentBucket, selectedFilterIndex, sortOrder, favoritesManager.favoritesFlow, excludedFolders
    ) { bucket, filterIndex, order, favs, excluded ->
        val qc = buildMediaConditions(
            bucket = bucket,
            filterIndex = filterIndex,
            excluded = excluded,
            favIds = favs,
            ftsIds = ftsSearchResults.value.map { it.id },
            smartIds = smartSearchResults.value.map { it.id }
        )
        val queryString = "SELECT cm.*, tb.telegramFileId, tb.telegramThumbFileId, tb.backupStatus FROM core_media cm LEFT JOIN telegram_backups tb ON cm.id = tb.mediaId " +
            buildWhereClause(qc) + buildOrderClause(order)

        androidx.sqlite.db.SimpleSQLiteQuery(queryString, qc.args.toTypedArray())
    }.flatMapLatest { query ->
        Pager(
            config = PagingConfig(pageSize = 60, enablePlaceholders = true)
        ) {
            database.mediaDao().observeMediaPagingRaw(query)
        }.flow
    }.map { pagingData ->
        pagingData.map { entity ->
            val uri = Uri.parse(entity.uriString)
            val (exists, resolved) = resolveItemFields(uri, entity.filePath, entity.telegramThumbFileId, entity.telegramFileId)
            GalleryItem(
                id = entity.id.toString(),
                uri = uri,
                bucketName = entity.bucketName,
                dateAdded = entity.dateAdded,
                size = entity.size,
                name = entity.name,
                dateModified = entity.dateModified,
                path = entity.filePath,
                isVideo = entity.isVideo,
                durationMs = entity.durationMs,
                telegramFileId = entity.telegramFileId,
                telegramThumbFileId = entity.telegramThumbFileId,
                localExists = exists,
                resolvedUri = resolved
            )
        }
    }.cachedIn(viewModelScope)

    private fun formatGroupHeader(dateAddedSeconds: Long): String {
        val timeMs = dateAddedSeconds * 1000L
        val itemDate = java.time.Instant.ofEpochMilli(timeMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)
        
        return when (itemDate) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                if (itemDate.year == today.year) {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.getDefault())
                    itemDate.format(formatter)
                } else {
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.getDefault())
                    itemDate.format(formatter)
                }
            }
        }
    }

    val pagedMedia: Flow<PagingData<GalleryListItem>> = combine(viewMode, sortOrder) { mode, order ->
        Pair(mode, order)
    }.flatMapLatest { (mode, order) ->
        val isDateSort = order == SortOrder.NewToOld || order == SortOrder.OldToNew
        pagedMediaRaw.map { pagingData ->
            if (mode == ViewMode.Immersive || !isDateSort) {
                // No date headers in Immersive mode or non-date sorts
                pagingData.map { GalleryListItem.Item(it) as GalleryListItem }
            } else {
                pagingData.insertSeparators { before: GalleryItem?, after: GalleryItem? ->
                    if (after == null) return@insertSeparators null
                    
                    val afterTitle = formatGroupHeader(after.dateAdded)
                    
                    if (before == null) {
                        return@insertSeparators GalleryListItem.Header(afterTitle)
                    }
                    
                    val beforeTitle = formatGroupHeader(before.dateAdded)
                    
                    if (beforeTitle != afterTitle) {
                        return@insertSeparators GalleryListItem.Header(afterTitle)
                    } else {
                        return@insertSeparators null
                    }
                }.map { item ->
                    if (item is GalleryItem) GalleryListItem.Item(item) else item as GalleryListItem.Header
                }
            }
        }
    }.cachedIn(viewModelScope)

    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedUris.map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleSelection(uri: String) {
        val current = _selectedUris.value.toMutableSet()
        if (current.contains(uri)) {
            current.remove(uri)
        } else {
            current.add(uri)
        }
        _selectedUris.value = current
    }

    fun addSelection(uri: String) {
        val current = _selectedUris.value.toMutableSet()
        current.add(uri)
        _selectedUris.value = current
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    fun toggleSelectAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val bucket = _currentBucket.value
            val filterIndex = selectedFilterIndex.value
            
            val qc = buildMediaConditions(
                bucket = bucket,
                filterIndex = filterIndex,
                ftsIds = ftsSearchResults.value.map { it.id }
            )
            val queryString = "SELECT cm.uriString FROM core_media cm LEFT JOIN telegram_backups tb ON cm.id = tb.mediaId " +
                buildWhereClause(qc)
            
            try {
                val dbQuery = androidx.sqlite.db.SimpleSQLiteQuery(queryString, qc.args.toTypedArray())
                val allUris = database.mediaDao().getUrisRaw(dbQuery).toSet()
                
                val currentSelected = _selectedUris.value
                val allSelected = allUris.isNotEmpty() && allUris.all { currentSelected.contains(it) }
                
                if (allSelected) {
                    // Deselect all matching items in current view
                    _selectedUris.value = currentSelected - allUris
                } else {
                    // Select all matching items in current view
                    _selectedUris.value = currentSelected + allUris
                }
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "Error toggling select all: ${e.message}", e)
            }
        }
    }

    fun selectRange(startUri: String, endUri: String) {
        // Not supported in Paging3 without memory indexing.
    }

    val allAlbums: StateFlow<List<AlbumBucket>> = combine(
        database.mediaDao().observeBuckets(), 
        albumSortOrder, 
        excludedFolders,
        settingsRepository.showHiddenAlbumsFlow
    ) { buckets, order, excluded, showHidden ->
        val excludedKeywords = setOf(BucketNames.CAMERA, BucketNames.SCREENSHOTS, BucketNames.TRASH, BucketNames.ALL, BucketNames.VIDEOS)
        
        val filtered = buckets.filter { bucket ->
            !excludedKeywords.contains(bucket.bucketName) && 
            !bucket.bucketName.contains(BucketNames.SCREENRECORDINGS, ignoreCase = true) &&
            !bucket.bucketName.contains(BucketNames.SCREEN_RECORDS, ignoreCase = true) &&
            !bucket.bucketName.contains(BucketNames.SCREEN_RECORDS_NO_SPACE, ignoreCase = true) &&
            !bucket.bucketName.contains(BucketNames.SCREEN_RECORD, ignoreCase = true) &&
            !bucket.bucketName.contains(BucketNames.SCREENSHOT, ignoreCase = true) &&
            !excluded.contains(bucket.bucketName) &&
            (showHidden || !bucket.bucketName.startsWith("."))
        }.map { b ->
            AlbumBucket(
                bucketName = b.bucketName,
                coverUri = if (b.coverUriString != null) Uri.parse(b.coverUriString) else Uri.EMPTY,
                itemCount = b.itemCount,
                totalSizeBytes = b.totalSizeBytes,
                maxDate = b.maxDate
            )
        }
        
        val sortedBuckets = when (order) {
            SortOrder.NewToOld -> filtered.sortedByDescending { it.maxDate }
            SortOrder.OldToNew -> filtered.sortedBy { it.maxDate }
            SortOrder.SmallToBig -> filtered.sortedBy { it.totalSizeBytes }
            SortOrder.BigToSmall -> filtered.sortedByDescending { it.totalSizeBytes }
            SortOrder.NameAsc -> filtered.sortedBy { it.bucketName }
        }
        
        sortedBuckets
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All bucket names for the Settings "Excluded Folders" UI (includes all folders)
    val allBucketNames: StateFlow<List<String>> = combine(
        database.mediaDao().observeBuckets(),
        settingsRepository.showHiddenAlbumsFlow
    ) { buckets, showHidden ->
        buckets.map { it.bucketName }
            .filter { it != "Trash" && (showHidden || !it.startsWith(".")) }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val duplicates: StateFlow<List<DuplicateGroup>> = database.mediaDao().observeExactDuplicates()
        .map { entities ->
            entities.map { entity ->
                val uri = Uri.parse(entity.uriString)
                val exists = java.io.File(entity.filePath).exists()
                GalleryItem(
                    id = entity.id.toString(),
                    uri = uri,
                    bucketName = entity.bucketName,
                    dateAdded = entity.dateAdded,
                    size = entity.size,
                    name = entity.name,
                    dateModified = entity.dateModified,
                    path = entity.filePath,
                    isVideo = entity.isVideo,
                    durationMs = entity.durationMs,
                    localExists = exists,
                    resolvedUri = uri,
                    pHash = entity.pHash,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    fileHash = entity.fileHash
                )
            }.filter { it.fileHash != null }
            .groupBy { it.fileHash!! }
            .filter { it.value.size > 1 }
            .map { (_, items) -> DuplicateGroup(items.first().pHash ?: 0L, items) }
            .sortedByDescending { group -> group.items.sumOf { it.size } }
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val similarPhotos: StateFlow<List<DuplicateGroup>> = database.mediaDao().observeAllHashedMedia()
        .map { entities ->
            val items = entities.map { entity ->
                val uri = Uri.parse(entity.uriString)
                val exists = java.io.File(entity.filePath).exists()
                GalleryItem(
                    id = entity.id.toString(),
                    uri = uri,
                    bucketName = entity.bucketName,
                    dateAdded = entity.dateAdded,
                    size = entity.size,
                    name = entity.name,
                    dateModified = entity.dateModified,
                    path = entity.filePath,
                    isVideo = entity.isVideo,
                    durationMs = entity.durationMs,
                    localExists = exists,
                    resolvedUri = uri,
                    pHash = entity.pHash,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    fileHash = entity.fileHash
                )
            }
            
            // Build set of exact duplicate fileHash values to exclude pure-copy groups
            val exactDuplicateHashes = items
                .filter { it.fileHash != null }
                .groupBy { it.fileHash!! }
                .filter { it.value.size > 1 }
                .keys
            
            val groups = mutableListOf<List<GalleryItem>>()
            val visited = BooleanArray(items.size)
            
            for (i in items.indices) {
                if (visited[i]) continue
                val h1 = items[i].pHash ?: continue
                
                val currentGroup = mutableListOf(items[i])
                visited[i] = true
                
                for (j in i + 1 until items.size) {
                    if (visited[j]) continue
                    val h2 = items[j].pHash ?: continue
                    
                    val distance = com.inferno.gallery.utils.HashUtils.hammingDistance(h1, h2)
                    // dHash distance 0-4: visually very similar
                    if (distance <= 4) {
                        currentGroup.add(items[j])
                        visited[j] = true
                    }
                }
                
                if (currentGroup.size > 1) {
                    // Skip groups where ALL items share the same fileHash (those are exact copies, already shown in other tab)
                    val uniqueFileHashes = currentGroup.mapNotNull { it.fileHash }.toSet()
                    val allSameHash = uniqueFileHashes.size == 1 && uniqueFileHashes.first() in exactDuplicateHashes
                    if (!allSameHash) {
                        groups.add(currentGroup)
                    }
                }
            }
            
            groups.map { DuplicateGroup(it.first().pHash ?: 0L, it) }
                .sortedByDescending { group -> group.items.sumOf { it.size } }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Duplicate Scan State ──
    private val _duplicateScanState = MutableStateFlow<DuplicateScanState>(DuplicateScanState.Idle)
    val duplicateScanState: StateFlow<DuplicateScanState> = _duplicateScanState.asStateFlow()

    fun scanForDuplicates() {
        if (_duplicateScanState.value is DuplicateScanState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _duplicateScanState.value = DuplicateScanState.Scanning(0, 0)
            try {
                val allMedia = database.mediaDao().getAllMedia()
                val needsHash = allMedia.filter { it.fileHash == null || (!it.isVideo && it.pHash == null) }
                val total = needsHash.size

                if (total == 0) {
                    _duplicateScanState.value = DuplicateScanState.Done
                    return@launch
                }

                val app = getApplication<Application>()
                val batchSize = 50
                var processed = 0

                for (batch in needsHash.chunked(batchSize)) {
                    val updates = batch.mapNotNull { entity ->
                        var fileHash = entity.fileHash
                        var pHash = entity.pHash

                        val file = if (entity.filePath.isNotEmpty()) java.io.File(entity.filePath) else null

                        // Compute MD5 fileHash
                        if (fileHash == null && file != null && file.exists()) {
                            fileHash = com.inferno.gallery.utils.HashUtils.computeFileHash(file)
                        }

                        // Compute dHash (pHash) for images
                        if (!entity.isVideo && pHash == null) {
                            try {
                                val uri = Uri.parse(entity.uriString)
                                val thumbnail = app.contentResolver.loadThumbnail(
                                    uri, android.util.Size(128, 128), null
                                )
                                pHash = com.inferno.gallery.utils.HashUtils.generatePerceptualHash(thumbnail)
                            } catch (_: Exception) {}
                        }

                        if (fileHash != entity.fileHash || pHash != entity.pHash) {
                            entity.copy(fileHash = fileHash, pHash = pHash)
                        } else null
                    }

                    if (updates.isNotEmpty()) {
                        database.mediaDao().insertAll(updates)
                    }

                    processed += batch.size
                    _duplicateScanState.value = DuplicateScanState.Scanning(processed, total)
                }

                _duplicateScanState.value = DuplicateScanState.Done
            } catch (e: Exception) {
                e.printStackTrace()
                _duplicateScanState.value = DuplicateScanState.Done
            }
        }
    }

    // ── GPS Scan State ──
    sealed class GpsScanState {
        data object Idle : GpsScanState()
        data class Scanning(val processed: Int, val total: Int) : GpsScanState()
        data object Done : GpsScanState()
    }

    private val _gpsScanState = MutableStateFlow<GpsScanState>(GpsScanState.Idle)
    val gpsScanState: StateFlow<GpsScanState> = _gpsScanState.asStateFlow()

    private val _geotaggedMedia = MutableStateFlow<List<GalleryItem>>(emptyList())
    val geotaggedMedia: StateFlow<List<GalleryItem>> = _geotaggedMedia.asStateFlow()

    fun scanGpsMetadata() {
        if (_gpsScanState.value is GpsScanState.Scanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _gpsScanState.value = GpsScanState.Scanning(0, 0)
            try {
                val needsGps = database.mediaDao().getMediaNeedingGps()
                val total = needsGps.size

                if (total > 0) {
                    val batchSize = 100
                    var processed = 0

                    for (batch in needsGps.chunked(batchSize)) {
                        val updates = batch.mapNotNull { entity ->
                            try {
                                val file = if (entity.filePath.isNotEmpty()) java.io.File(entity.filePath) else null
                                if (file != null && file.exists()) {
                                    val exif = androidx.exifinterface.media.ExifInterface(entity.filePath)
                                    val latLong = exif.latLong
                                    if (latLong != null) {
                                        entity.copy(latitude = latLong[0], longitude = latLong[1])
                                    } else null
                                } else null
                            } catch (_: Exception) { null }
                        }

                        if (updates.isNotEmpty()) {
                            database.mediaDao().insertAll(updates)
                        }

                        processed += batch.size
                        _gpsScanState.value = GpsScanState.Scanning(processed, total)
                    }
                }

                // Load geotagged results
                loadGeotaggedMedia()
                _gpsScanState.value = GpsScanState.Done
            } catch (e: Exception) {
                e.printStackTrace()
                _gpsScanState.value = GpsScanState.Done
            }
        }
    }

    fun loadGeotaggedMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = database.mediaDao().getGeotaggedMedia()
                _geotaggedMedia.value = entities.map { entity ->
                    val uri = Uri.parse(entity.uriString)
                    GalleryItem(
                        id = entity.id.toString(),
                        uri = uri,
                        bucketName = entity.bucketName,
                        dateAdded = entity.dateAdded,
                        size = entity.size,
                        name = entity.name,
                        dateModified = entity.dateModified,
                        path = entity.filePath,
                        isVideo = entity.isVideo,
                        durationMs = entity.durationMs,
                        localExists = true,
                        resolvedUri = uri,
                        latitude = entity.latitude,
                        longitude = entity.longitude
                    )
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Failed to load geotagged media: ${e.message}")
            }
        }
    }

    val pinnedAlbums: StateFlow<List<AlbumBucket>> = combine(
        database.mediaDao().observeBuckets(),
        database.mediaDao().observeAllMediaStats(),
        database.mediaDao().observeVideoStats(),
        database.mediaDao().observeTopCoverUris(),
        favoriteMedia
    ) { buckets, allStats, videoStats, topCoverUris, favMedia ->
        val allBucket = AlbumBucket(
            bucketName = "All",
            coverUri = allStats.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = allStats.itemCount,
            totalSizeBytes = allStats.totalSizeBytes,
            maxDate = allStats.maxDate,
            coverUris = topCoverUris.map { Uri.parse(it) }
        )

        val cameraItems = buckets.filter { it.bucketName.equals(BucketNames.CAMERA, ignoreCase = true) }
        val cameraBucket = AlbumBucket(
            bucketName = cameraItems.firstOrNull()?.bucketName ?: BucketNames.CAMERA,
            coverUri = cameraItems.maxByOrNull { it.maxDate }?.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = cameraItems.sumOf { it.itemCount },
            totalSizeBytes = cameraItems.sumOf { it.totalSizeBytes },
            maxDate = cameraItems.maxOfOrNull { it.maxDate } ?: 0L
        )
        
        val videosBucket = AlbumBucket(
            bucketName = BucketNames.VIDEOS,
            coverUri = videoStats.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = videoStats.itemCount,
            totalSizeBytes = videoStats.totalSizeBytes,
            maxDate = videoStats.maxDate
        )

        val favoritesBucket = AlbumBucket(
            bucketName = BucketNames.FAVORITES,
            coverUri = favMedia.firstOrNull()?.uri ?: Uri.EMPTY,
            itemCount = favMedia.size,
            totalSizeBytes = favMedia.sumOf { it.size },
            maxDate = favMedia.maxOfOrNull { it.dateAdded } ?: 0L
        )

        val screenshotsItems = buckets.filter { it.bucketName.contains(BucketNames.SCREENSHOTS, ignoreCase = true) || it.bucketName.contains(BucketNames.SCREENSHOT, ignoreCase = true) }
        val screenshotsBucket = AlbumBucket(
            bucketName = screenshotsItems.firstOrNull()?.bucketName ?: BucketNames.SCREENSHOTS,
            coverUri = screenshotsItems.maxByOrNull { it.maxDate }?.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = screenshotsItems.sumOf { it.itemCount },
            totalSizeBytes = screenshotsItems.sumOf { it.totalSizeBytes },
            maxDate = screenshotsItems.maxOfOrNull { it.maxDate } ?: 0L
        )

        val screenrecordingsItems = buckets.filter { it.bucketName.contains(BucketNames.SCREENRECORDINGS, ignoreCase = true) || it.bucketName.contains(BucketNames.SCREEN_RECORDS, ignoreCase = true) || it.bucketName.contains(BucketNames.SCREEN_RECORDS_NO_SPACE, ignoreCase = true) || it.bucketName.contains(BucketNames.SCREEN_RECORD, ignoreCase = true) }
        val screenrecordingsBucket = AlbumBucket(
            bucketName = screenrecordingsItems.firstOrNull()?.bucketName ?: BucketNames.SCREENRECORDINGS,
            coverUri = screenrecordingsItems.maxByOrNull { it.maxDate }?.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = screenrecordingsItems.sumOf { it.itemCount },
            totalSizeBytes = screenrecordingsItems.sumOf { it.totalSizeBytes },
            maxDate = screenrecordingsItems.maxOfOrNull { it.maxDate } ?: 0L
        )
        
        listOf(
            allBucket,
            cameraBucket,
            videosBucket,
            favoritesBucket,
            screenshotsBucket,
            screenrecordingsBucket
        ).filter { it.bucketName == BucketNames.FAVORITES || it.itemCount > 0 }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User-pinned folders (from Settings, not the hardcoded system buckets)
    val userPinnedFolderNames: StateFlow<Set<String>> = settingsRepository.pinnedFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val userPinnedAlbums: StateFlow<List<AlbumBucket>> = combine(
        allAlbums,
        settingsRepository.pinnedFoldersFlow
    ) { albums, pinnedNames ->
        albums.filter { it.bucketName in pinnedNames }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePinAlbum(bucketName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.togglePinnedFolder(bucketName)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()


    private var searchJob: kotlinx.coroutines.Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _ftsSearchResults.value = emptyList()
            _smartSearchResults.value = emptyList()
            _isSearching.value = false
            searchJob?.cancel()
            return
        }
        // Debounce: wait 300ms after user stops typing, then fire searches
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            performUnifiedSearch(query)
        }
    }

    private val _ftsSearchResults = MutableStateFlow<List<GalleryItem>>(emptyList())
    val ftsSearchResults: StateFlow<List<GalleryItem>> = _ftsSearchResults.asStateFlow()

    private val _smartSearchResults = MutableStateFlow<List<GalleryItem>>(emptyList())
    val smartSearchResults: StateFlow<List<GalleryItem>> = _smartSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** Whether the smart search ONNX model is downloaded and ready for inference. */
    val isSmartSearchReady: StateFlow<Boolean> = kotlinx.coroutines.flow.flow {
        val engine = com.inferno.gallery.data.ai.SmartSearchEngine.getInstance(getApplication())
        emit(engine.isModelDownloaded())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun performUnifiedSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                kotlinx.coroutines.coroutineScope {
                    val ftsDeferred = async(Dispatchers.IO) {
                        try {
                            val ftsEntities = if (query.isNotBlank()) {
                                DatabaseProvider.searchFts(database, query)
                            } else {
                                emptyList()
                            }
                            val ids = ftsEntities.map { it.id }
                            val backups = if (ids.isNotEmpty()) {
                                database.telegramBackupDao().getBackupsForMediaIds(ids)
                            } else {
                                emptyList()
                            }
                            val backupMap = backups.associateBy { it.mediaId }
                            ftsEntities.map { entity ->
                                val backup = backupMap[entity.id]
                                val uri = Uri.parse(entity.uriString)
                                val (exists, resolved) = resolveItemFields(uri, entity.filePath, backup?.telegramThumbFileId, backup?.telegramFileId)
                                GalleryItem(
                                    id = entity.id.toString(),
                                    uri = uri,
                                    bucketName = entity.bucketName,
                                    dateAdded = entity.dateAdded,
                                    size = entity.size,
                                    name = entity.name,
                                    dateModified = entity.dateModified,
                                    path = entity.filePath,
                                    isVideo = entity.isVideo,
                                    durationMs = entity.durationMs,
                                    telegramFileId = backup?.telegramFileId,
                                    telegramThumbFileId = backup?.telegramThumbFileId,
                                    localExists = exists,
                                    resolvedUri = resolved
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GalleryViewModel", "FTS search failed: ${e.message}")
                            emptyList()
                        }
                    }

                    val smartDeferred = async(Dispatchers.Default) {
                        try {
                            val searchEngine = com.inferno.gallery.data.ai.SmartSearchEngine.getInstance(getApplication())
                            if (searchEngine.isModelDownloaded()) {
                                if (!searchEngine.isLoaded()) {
                                    searchEngine.loadModel()
                                }
                                val queryVector = searchEngine.encodeText(query)
                                val allEmbeddings = database.embeddingDao().getAllEmbeddings()
                                val threshold = settingsRepository.smartSearchThresholdFlow.first()

                                android.util.Log.i("GalleryViewModel", "Smart search for: '$query'. Query vector sample: [${queryVector.take(3).joinToString(", ")}], Total DB embeddings: ${allEmbeddings.size}, Threshold: $threshold")

                                val matchedIds = allEmbeddings.mapNotNull { record ->
                                    val score = searchEngine.dotProduct(queryVector, record.embedding)
                                    if (score >= threshold) {
                                        android.util.Log.i("GalleryViewModel", "Match found! ID: ${record.mediaId}, score: $score >= threshold $threshold")
                                        Pair(record.mediaId, score)
                                    } else {
                                        if (score > 0.05f) {
                                            android.util.Log.i("GalleryViewModel", "Low match score: $score for ID: ${record.mediaId}")
                                        }
                                        null
                                    }
                                }.sortedByDescending { it.second }.map { it.first }

                                if (matchedIds.isNotEmpty()) {
                                    val entitiesMap = database.mediaDao().getMediaByIdsList(matchedIds).associateBy { it.id }
                                    val orderedEntities = matchedIds.mapNotNull { entitiesMap[it] }

                                    val backups = database.telegramBackupDao().getBackupsForMediaIds(matchedIds)
                                    val backupMap = backups.associateBy { it.mediaId }

                                    orderedEntities.map { entity ->
                                        val backup = backupMap[entity.id]
                                        val uri = Uri.parse(entity.uriString)
                                        val (exists, resolved) = resolveItemFields(uri, entity.filePath, backup?.telegramThumbFileId, backup?.telegramFileId)
                                        GalleryItem(
                                            id = entity.id.toString(),
                                            uri = uri,
                                            bucketName = entity.bucketName,
                                            dateAdded = entity.dateAdded,
                                            size = entity.size,
                                            name = entity.name,
                                            dateModified = entity.dateModified,
                                            path = entity.filePath,
                                            isVideo = entity.isVideo,
                                            durationMs = entity.durationMs,
                                            telegramFileId = backup?.telegramFileId,
                                            telegramThumbFileId = backup?.telegramThumbFileId,
                                            localExists = exists,
                                            resolvedUri = resolved
                                        )
                                    }
                                } else {
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GalleryViewModel", "Smart search failed: ${e.message}")
                            emptyList()
                        }
                    }

                    val ftsResults = ftsDeferred.await()
                    val smartResults = smartDeferred.await()

                    _ftsSearchResults.value = ftsResults
                    _smartSearchResults.value = smartResults
                }
            } finally {
                _isSearching.value = false
            }
        }
    }



    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.updateSortOrder(order.name)
        }
    }

    fun setAlbumSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.updateAlbumSortOrder(order.name)
        }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            settingsRepository.updateViewMode(mode.name)
        }
    }



    fun setFilter(index: Int) {
        viewModelScope.launch {
            settingsRepository.updateSelectedFilterIndex(index)
        }
    }

    fun setGridCellsCount(count: Int) {
        _gridCellsCount.value = count
        saveGridCellsJob?.cancel()
        saveGridCellsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            settingsRepository.updateGridCellsCount(count)
        }
    }

    private val _uiEvents = kotlinx.coroutines.channels.Channel<UiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun removeMediaOptimistically(uriString: String) {
        viewModelScope.launch {
            // Instantly move to Trash bin so the UI updates
            database.mediaDao().updateBucketByUri(uriString, "Trash")
            // Also remove from the detail pager list immediately
            _detailMedia.value = _detailMedia.value.filter { it.uri.toString() != uriString }
            _uiEvents.send(UiEvent.DeleteSuccess)
        }
    }

    fun moveSelectedMedia(targetBucket: String) {
        val selected = _selectedUris.value.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            try {
                    val resolver = getApplication<android.app.Application>().contentResolver
                    for (uriString in selected) {
                        val uri = Uri.parse(uriString)
                        if (uriString.startsWith("content://")) {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/" + targetBucket)
                            }
                            try {
                                resolver.update(uri, values, null, null)
                                database.mediaDao().updateBucketByUri(uriString, targetBucket)
                            } catch (e: Exception) {
                                android.util.Log.e("GalleryViewModel", "Failed to move media: ${e.message}")
                            }
                        }
                    }
                    clearSelection()
                    withContext(Dispatchers.Main) {
                        showToast("Moved to $targetBucket")
                    }
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "Error moving media: ${e.message}", e)
            }
        }
    }

    fun copySelectedMedia(targetBucket: String) {
        val selected = _selectedUris.value.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                    val resolver = getApplication<android.app.Application>().contentResolver
                    var successCount = 0
                    for (uriString in selected) {
                        val uri = Uri.parse(uriString)
                        if (uriString.startsWith("content://")) {
                            // 1. Get info about original file
                            var displayName: String? = null
                            var mimeType: String? = null
                            resolver.query(uri, arrayOf(
                                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                                android.provider.MediaStore.MediaColumns.MIME_TYPE
                            ), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    displayName = cursor.getString(0)
                                    mimeType = cursor.getString(1)
                                }
                            }
                            
                            if (displayName == null) {
                                displayName = "copied_media_${System.currentTimeMillis()}"
                            }
                            if (mimeType == null) {
                                mimeType = "image/jpeg"
                            }

                            // 2. Insert copy entry in MediaStore
                            val isVideo = mimeType?.startsWith("video/") == true
                            val baseUri = if (isVideo) {
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            } else {
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            }

                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/" + targetBucket)
                                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                            }

                            val newUri = resolver.insert(baseUri, values)
                            if (newUri != null) {
                                try {
                                    // 3. Copy bytes
                                    resolver.openInputStream(uri)?.use { input ->
                                        resolver.openOutputStream(newUri)?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    
                                    // 4. Release pending status
                                    val updateValues = android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                                    }
                                    resolver.update(newUri, updateValues, null, null)
                                    successCount++
                                } catch (e: Exception) {
                                    android.util.Log.e("GalleryViewModel", "Failed copy stream: ${e.message}")
                                    resolver.delete(newUri, null, null)
                                }
                            }
                        }
                    }
                    
                    if (successCount > 0) {
                        // Trigger MediaSyncWorker to update our database and grid UI
                        val syncWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.inferno.gallery.workers.MediaSyncWorker>().build()
                        androidx.work.WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                            "MediaSyncWorker",
                            androidx.work.ExistingWorkPolicy.REPLACE,
                            syncWorkRequest
                        )
                    }

                    clearSelection()
                    withContext(Dispatchers.Main) {
                        showToast("Copied $successCount items to $targetBucket")
                    }
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "Error copying media: ${e.message}", e)
            }
        }
    }

    fun deleteSelectedMediaFromDb(uris: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (uriString in uris) {
                database.mediaDao().deleteByUri(uriString)
            }
        }
    }

    fun createAlbum(albumName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val newFolder = java.io.File(picturesDir, albumName)
                if (newFolder.exists()) {
                    withContext(Dispatchers.Main) {
                        onError("Album directory already exists")
                    }
                    return@launch
                }
                val success = newFolder.mkdirs()
                if (success) {
                    android.media.MediaScannerConnection.scanFile(
                        getApplication(),
                        arrayOf(newFolder.absolutePath),
                        null
                    ) { _, _ -> }
                    
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Could not create directory. Make sure you have storage permissions.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }


    val ocrIndexWorkInfo = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow("OcrIndexWorker")
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val unindexedOcrImagesCount: kotlinx.coroutines.flow.StateFlow<Int> = database.mediaDao().observeUnindexedOcrImageCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalImagesCount: kotlinx.coroutines.flow.StateFlow<Int> = database.mediaDao().observeTotalImageCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val backupStatuses: StateFlow<Map<Long, String>> = database.telegramBackupDao().observeAllBackups().map { backups ->
        backups.associate { it.mediaId to it.backupStatus }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun startOcrIndexing() {
        viewModelScope.launch {
            settingsRepository.updateOcrIndexingEnabled(true)
            val request = OneTimeWorkRequestBuilder<com.inferno.gallery.workers.OcrIndexWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork("OcrIndexWorker", ExistingWorkPolicy.KEEP, request)
        }
    }

    fun stopOcrIndexing() {
        viewModelScope.launch {
            settingsRepository.updateOcrIndexingEnabled(false)
            WorkManager.getInstance(getApplication()).cancelUniqueWork("OcrIndexWorker")
        }
    }

    fun clearOcrIndexAndReindex() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.updateOcrIndexingEnabled(true)
            WorkManager.getInstance(getApplication()).cancelUniqueWork("OcrIndexWorker")
            database.openHelper.writableDatabase.execSQL("DELETE FROM image_fts")
            val allIds = database.mediaDao().getAllMediaIds()
            allIds.chunked(500).forEach { chunk ->
                chunk.forEach { id -> database.mediaDao().updateOcrIndexStatus(id, false) }
            }
            val request = OneTimeWorkRequestBuilder<com.inferno.gallery.workers.OcrIndexWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork("OcrIndexWorker", ExistingWorkPolicy.KEEP, request)
        }
    }

    private val _isCloudRefreshing = MutableStateFlow(false)
    val isCloudRefreshing: StateFlow<Boolean> = _isCloudRefreshing.asStateFlow()

    fun refreshCloudBackups() {
        if (_isCloudRefreshing.value) return
        _isCloudRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupDao = database.telegramBackupDao()
                var backups = backupDao.observeAllBackups().first()
                var successBackups = backups.filter { it.backupStatus == "SUCCESS" }
                
                val botTokens = settingsRepository.telegramBotTokensFlow.first()
                var currentChatId = settingsRepository.telegramChatIdFlow.first()
                
                if (botTokens.isNotEmpty() && currentChatId.isNotBlank()) {
                    val activeToken = botTokens.first()
                    
                    // If no successful backups exist locally, try restoring from Telegram manifest
                    if (successBackups.isEmpty()) {
                        Log.d("GalleryViewModel", "Local backup list is empty. Attempting manifest recovery from Telegram.")
                        val restored = com.inferno.gallery.data.SyncManifestManager.restoreFromManifest(
                            getApplication(),
                            activeToken,
                            currentChatId
                        )
                        if (restored) {
                            backups = backupDao.observeAllBackups().first()
                            successBackups = backups.filter { it.backupStatus == "SUCCESS" }
                        }
                    }
                    
                    if (successBackups.isNotEmpty()) {
                        var client = com.inferno.gallery.data.network.TelegramClient(activeToken, currentChatId)
                        var changesMade = false
                        
                        for (backup in successBackups) {
                            val messageId = backup.telegramMessageId
                            val fileId = backup.telegramFileId
                            
                            try {
                                if (messageId != null) {
                                    val exists = client.checkMessageExists(messageId)
                                    if (!exists) {
                                        Log.d("GalleryViewModel", "Backup message $messageId was deleted from Telegram. Wiping association.")
                                        backupDao.deleteBackup(backup.mediaId)
                                        changesMade = true
                                    }
                                } else if (fileId != null) {
                                    // Fallback for legacy backups
                                    client.getFileUrl(fileId)
                                }
                                // Sleep 250ms to prevent rate limits during verification
                                kotlinx.coroutines.delay(250)
                            } catch (e: Exception) {
                                if (e is com.inferno.gallery.data.network.TelegramMigrationException) {
                                    Log.i("GalleryViewModel", "Group chat migrated during verification! Updating chat ID to ${e.newChatId}")
                                    settingsRepository.updateTelegramChatId(e.newChatId.toString())
                                    currentChatId = e.newChatId.toString()
                                    client = com.inferno.gallery.data.network.TelegramClient(activeToken, currentChatId)
                                    // Retry checking the current item once with new chat ID
                                    try {
                                        if (messageId != null) {
                                            val exists = client.checkMessageExists(messageId)
                                            if (!exists) {
                                                backupDao.deleteBackup(backup.mediaId)
                                                changesMade = true
                                            }
                                        } else if (fileId != null) {
                                            client.getFileUrl(fileId)
                                        }
                                    } catch (retryEx: Exception) {
                                        if (retryEx is com.inferno.gallery.data.network.TelegramApiException) {
                                            if (retryEx.description.contains("file_id is invalid", ignoreCase = true) || 
                                                retryEx.description.contains("file not found", ignoreCase = true)) {
                                                Log.d("GalleryViewModel", "Backup file $fileId was deleted from Telegram. Wiping association.")
                                                backupDao.deleteBackup(backup.mediaId)
                                                changesMade = true
                                            }
                                        }
                                    }
                                } else if (e is com.inferno.gallery.data.network.TelegramApiException) {
                                    // Delete legacy backups only if we get explicit file not found error
                                    if (messageId == null && fileId != null) {
                                        if (e.description.contains("file_id is invalid", ignoreCase = true) || 
                                            e.description.contains("file not found", ignoreCase = true)) {
                                            Log.d("GalleryViewModel", "Backup file $fileId was deleted from Telegram. Wiping association.")
                                            backupDao.deleteBackup(backup.mediaId)
                                            changesMade = true
                                        } else {
                                            Log.w("GalleryViewModel", "API error checking legacy file $fileId: ${e.message}")
                                        }
                                    } else {
                                        Log.w("GalleryViewModel", "API error checking message $messageId: ${e.message}")
                                    }
                                } else if (e is com.inferno.gallery.data.network.RateLimitException) {
                                    // Wait longer if we hit rate limits during check
                                    val waitTime = e.retryAfterSeconds + 2
                                    Log.w("GalleryViewModel", "Rate limit hit during cloud refresh verification. Waiting $waitTime seconds.")
                                    kotlinx.coroutines.delay(waitTime * 1000L)
                                } else {
                                    // Generic network or socket exceptions - DO NOT delete backup
                                    Log.w("GalleryViewModel", "Transient error checking cloud backup: ${e.message}")
                                }
                            }
                        }
                        
                        // If any records were deleted, update the pinned sync manifest
                        if (changesMade) {
                            com.inferno.gallery.data.SyncManifestManager.updateManifest(
                                getApplication(),
                                activeToken,
                                currentChatId
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error verifying cloud backups: ${e.message}", e)
            } finally {
                _isCloudRefreshing.value = false
            }
        }
    }

    fun deleteCloudBackup(mediaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupDao = database.telegramBackupDao()
                val backup = backupDao.getBackupForMedia(mediaId)
                if (backup != null) {
                    val messageId = backup.telegramMessageId
                    val botTokens = settingsRepository.telegramBotTokensFlow.first()
                    val chatId = settingsRepository.telegramChatIdFlow.first()
                    
                    if (botTokens.isNotEmpty() && chatId.isNotBlank()) {
                        val token = botTokens.first()
                        
                        // 1. Delete from Telegram chat if messageId is available
                        if (messageId != null) {
                            try {
                                com.inferno.gallery.data.network.TelegramClient(token, chatId).deleteMessage(messageId)
                                Log.d("GalleryViewModel", "Deleted message $messageId from Telegram.")
                            } catch (e: Exception) {
                                Log.e("GalleryViewModel", "Failed to delete message $messageId from Telegram: ${e.message}")
                            }
                        }
                        
                        // 2. Mark local database association as excluded so it doesn't auto-backup again
                        backupDao.markBackupExcluded(mediaId)
                        
                        // 3. Update pinned manifest
                        com.inferno.gallery.data.SyncManifestManager.updateManifest(
                            getApplication(),
                            token,
                            chatId
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error deleting cloud backup $mediaId: ${e.message}", e)
            }
        }
    }

    fun deleteCloudBackups(mediaIds: List<Long>) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupDao = database.telegramBackupDao()
                val botTokens = settingsRepository.telegramBotTokensFlow.first()
                val chatId = settingsRepository.telegramChatIdFlow.first()
                
                if (botTokens.isNotEmpty() && chatId.isNotBlank()) {
                    val token = botTokens.first()
                    val client = com.inferno.gallery.data.network.TelegramClient(token, chatId)
                    
                    var changesMade = false
                    for (mediaId in mediaIds) {
                        val backup = backupDao.getBackupForMedia(mediaId)
                        if (backup != null) {
                            val messageId = backup.telegramMessageId
                            if (messageId != null) {
                                try {
                                    client.deleteMessage(messageId)
                                    Log.d("GalleryViewModel", "Deleted message $messageId from Telegram.")
                                } catch (e: Exception) {
                                    Log.e("GalleryViewModel", "Failed to delete message $messageId from Telegram: ${e.message}")
                                }
                            }
                            backupDao.deleteBackup(mediaId)
                            changesMade = true
                        }
                    }
                    
                    if (changesMade) {
                        com.inferno.gallery.data.SyncManifestManager.updateManifest(
                            getApplication(),
                            token,
                            chatId
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Error deleting cloud backups: ${e.message}", e)
            }
        }
    }

    fun backupSelectedMedia() {
        val selected = _selectedUris.value.toList()
        if (selected.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val allMediaList = database.mediaDao().getAllMedia()
            val backupDao = database.telegramBackupDao()
            
            // Build a content fingerprint set from already-backed-up items (name+size)
            // This catches duplicates where the same file exists with different mediaIds
            val allBackups = backupDao.observeAllBackups().first()
            val backedUpMediaIds = allBackups
                .filter { it.backupStatus == "SUCCESS" || it.backupStatus == "PENDING" }
                .map { it.mediaId }
                .toSet()
            val allMediaMap = allMediaList.associateBy { it.id }
            val backedUpFingerprints = backedUpMediaIds.mapNotNull { id ->
                allMediaMap[id]?.let { "${it.name}|${it.size}" }
            }.toMutableSet()

            var skippedSize = 0
            var skippedDuplicate = 0
            var skippedVideo = 0
            var queuedCount = 0

            selected.forEach { uriStr ->
                val mediaItem = allMediaList.firstOrNull { it.uriString == uriStr }
                if (mediaItem != null) {
                    if (mediaItem.isVideo) {
                        skippedVideo++
                        return@forEach
                    }
                    if (mediaItem.size > 50 * 1024 * 1024L) {
                        skippedSize++
                        return@forEach
                    }

                    val fingerprint = "${mediaItem.name}|${mediaItem.size}"

                    // Check 1: Same mediaId already backed up
                    val existingBackup = backupDao.getBackupForMedia(mediaItem.id)
                    if (existingBackup != null && existingBackup.backupStatus != "FAILED") {
                        skippedDuplicate++
                        return@forEach
                    }

                    // Check 2: Different mediaId but same content (name+size) already backed up
                    if (fingerprint in backedUpFingerprints) {
                        skippedDuplicate++
                        return@forEach
                    }

                    backupDao.insertOrUpdate(
                        TelegramBackupEntity(
                            mediaId = mediaItem.id,
                            telegramFileId = null,
                            telegramThumbFileId = null,
                            backupStatus = "PENDING",
                            backupTimestamp = System.currentTimeMillis()
                        )
                    )
                    backedUpFingerprints.add(fingerprint)
                    queuedCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                clearSelection()
                val messages = mutableListOf<String>()
                if (skippedVideo > 0) messages.add("$skippedVideo videos skipped")
                if (skippedSize > 0) messages.add("$skippedSize too large (>50MB)")
                if (skippedDuplicate > 0) messages.add("$skippedDuplicate already backed up")
                if (messages.isNotEmpty()) {
                    val skipMsg = "Skipped: ${messages.joinToString(", ")}"
                    showToast(skipMsg)
                }
            }
            
            if (queuedCount > 0) {
                val request = OneTimeWorkRequestBuilder<com.inferno.gallery.workers.TelegramBackupWorker>()
                    .build()
                WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                    "TelegramBackupWorker",
                    ExistingWorkPolicy.KEEP,
                    request
                )
            }
        }
    }

    sealed class UiEvent {
        object DeleteSuccess : UiEvent()
    }

    fun deleteCloudBackupsByUris(uris: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbMedia = database.mediaDao().getAllMedia()
            val mediaIds = uris.mapNotNull { uri ->
                dbMedia.find { it.uriString == uri }?.id
            }
            deleteCloudBackups(mediaIds)
        }
    }

    data class SystemStatus(val isOffline: Boolean, val isBatteryPauseActive: Boolean)

    val telegramConfigured: StateFlow<Boolean> = combine(
        settingsRepository.telegramBotTokensFlow,
        settingsRepository.telegramChatIdFlow,
        settingsRepository.telegramBackupModeFlow,
        settingsRepository.telegramUserbotChatIdFlow
    ) { tokens, chatId, mode, userbotChatId ->
        when (mode) {
            "userbot" -> userbotChatId != 0L
            else -> tokens.isNotEmpty() && chatId.isNotBlank()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun isBatteryTooLow(context: android.content.Context): Boolean {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter) ?: return false
        val level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
        return batteryPct < 35 && !isCharging
    }

    private fun isNetworkConnected(context: android.content.Context): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val systemStatus: StateFlow<SystemStatus> = kotlinx.coroutines.flow.flow {
        while (true) {
            val offline = !isNetworkConnected(getApplication())
            val batteryPause = settingsRepository.telegramAutoBackupBatteryLowPauseFlow.first() && isBatteryTooLow(getApplication())
            emit(SystemStatus(offline, batteryPause))
            kotlinx.coroutines.delay(5000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemStatus(false, false))

}
