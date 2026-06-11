package com.inferno.gallery.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inferno.gallery.data.db.DatabaseProvider
import com.inferno.gallery.data.db.TelegramBackupEntity
import com.inferno.gallery.data.db.CloudMediaItem
import com.inferno.gallery.data.LocalMediaRepository
import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.DockStyle
import com.inferno.gallery.data.FavoritesManager
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
    val telegramThumbFileId: String? = null
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

enum class SearchMode { SMART, FTS }

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalMediaRepository(application.contentResolver)
    private val settingsRepository = SettingsRepository(application)
    private val favoritesManager = FavoritesManager(application)
    private val database = DatabaseProvider.getDatabase(application)


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

    val favoriteMedia: StateFlow<List<GalleryItem>> = favoritesManager.favoritesFlow.flatMapLatest { favs ->
        if (favs.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else database.mediaDao().observeMediaByIds(favs.mapNotNull { it.toLongOrNull() })
    }.combine(database.telegramBackupDao().observeCloudMedia()) { entities, cloudItems ->
        val cloudMap = cloudItems.associateBy { it.id }
        entities.map { entity ->
            val cloudItem = cloudMap[entity.id]
            GalleryItem(
                id = entity.id.toString(),
                uri = Uri.parse(entity.uriString),
                bucketName = entity.bucketName,
                dateAdded = entity.dateAdded,
                size = entity.size,
                name = entity.name,
                dateModified = entity.dateModified,
                path = entity.filePath,
                isVideo = entity.isVideo,
                durationMs = entity.durationMs,
                telegramFileId = cloudItem?.telegramFileId,
                telegramThumbFileId = cloudItem?.telegramThumbFileId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _detailMedia = MutableStateFlow<List<GalleryItem>>(emptyList())
    val detailMedia: StateFlow<List<GalleryItem>> = _detailMedia.asStateFlow()

    fun setDetailItem(mediaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = mediaId.toLongOrNull() ?: return@launch
            val entity = database.mediaDao().getMediaById(id) ?: return@launch
            val cloudItem = database.telegramBackupDao().getBackupForMedia(id)
            val item = GalleryItem(
                id = entity.id.toString(),
                uri = Uri.parse(entity.uriString),
                bucketName = entity.bucketName,
                dateAdded = entity.dateAdded,
                size = entity.size,
                name = entity.name,
                dateModified = entity.dateModified,
                path = entity.filePath,
                isVideo = entity.isVideo,
                durationMs = entity.durationMs,
                telegramFileId = cloudItem?.telegramFileId,
                telegramThumbFileId = cloudItem?.telegramThumbFileId
            )
            _detailMedia.value = listOf(item)
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                }

                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                    val mimeType = if (item.isVideo) "video/mp4" else "image/jpeg"
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PhotonGallery")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(collection, values) ?: throw Exception("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    val token = settingsRepository.telegramBotTokensFlow.first().first()
                    val client = com.inferno.gallery.data.network.TelegramClient(token, "")
                    client.downloadFileStream(fileUrl, outputStream)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Download complete", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
    }

    val thumbnailCornerRadius: StateFlow<Float> = settingsRepository.thumbnailCornerRadiusFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    fun setThumbnailCornerRadius(radius: Float) {
        viewModelScope.launch {
            settingsRepository.updateThumbnailCornerRadius(radius)
        }
    }

    val pagedMediaRaw: Flow<PagingData<GalleryItem>> = combine(
        _currentBucket, selectedFilterIndex, sortOrder, cloudMediaIds
    ) { bucket, filterIndex, order, cloudIds ->
        var queryString = "SELECT * FROM core_media "
        val args = mutableListOf<Any>()
        val conditions = mutableListOf<String>()

        val folderName = when (filterIndex) {
            1 -> "Camera"
            2 -> "Screenshots"
            else -> null
        }
        if (bucket == "search_text") {
            val ids = ftsSearchResults.value.map { it.id }
            if (ids.isEmpty()) {
                conditions.add("1 = 0")
            } else {
                conditions.add("id IN (${ids.joinToString(",")})")
                conditions.add("bucketName != 'Trash'")
            }
        } else {
            if (bucket == "All") {
                conditions.add("bucketName != 'Trash'")
            } else if (bucket == "telegram_cloud") {
                val ids = cloudIds
                if (ids.isEmpty()) {
                    conditions.add("1 = 0")
                } else {
                    conditions.add("id IN (${ids.joinToString(",")})")
                }
            } else if (bucket == "Videos") {
                conditions.add("isVideo = 1")
                conditions.add("bucketName != 'Trash'")
            } else if (bucket != null) {
                conditions.add("bucketName = ?")
                args.add(bucket)
            } else if (folderName != null) {
                conditions.add("bucketName = ?")
                args.add(folderName)
            } else {
                conditions.add("bucketName != 'Trash'")
            }
        }

        if (conditions.isNotEmpty()) {
            queryString += "WHERE " + conditions.joinToString(" AND ") + " "
        }

        when (order) {
            SortOrder.NewToOld -> queryString += "ORDER BY dateAdded DESC"
            SortOrder.OldToNew -> queryString += "ORDER BY dateAdded ASC"
            SortOrder.SmallToBig -> queryString += "ORDER BY size ASC"
            SortOrder.BigToSmall -> queryString += "ORDER BY size DESC"
            SortOrder.NameAsc -> queryString += "ORDER BY name ASC"
        }

        androidx.sqlite.db.SimpleSQLiteQuery(queryString, args.toTypedArray())
    }.flatMapLatest { query ->
        Pager(
            config = PagingConfig(pageSize = 60, enablePlaceholders = true)
        ) {
            database.mediaDao().observeMediaPagingRaw(query)
        }.flow
    }.combine(database.telegramBackupDao().observeCloudMedia()) { pagingData, cloudItems ->
        val cloudMap = cloudItems.associateBy { it.id }
        pagingData.map { entity ->
            val cloudItem = cloudMap[entity.id]
            GalleryItem(
                id = entity.id.toString(),
                uri = Uri.parse(entity.uriString),
                bucketName = entity.bucketName,
                dateAdded = entity.dateAdded,
                size = entity.size,
                name = entity.name,
                dateModified = entity.dateModified,
                path = entity.filePath,
                isVideo = entity.isVideo,
                durationMs = entity.durationMs,
                telegramFileId = cloudItem?.telegramFileId,
                telegramThumbFileId = cloudItem?.telegramThumbFileId
            )
        }
    }.cachedIn(viewModelScope)

    val pagedMedia: Flow<PagingData<GalleryListItem>> = pagedMediaRaw.map { pagingData ->
        pagingData.insertSeparators { before: GalleryItem?, after: GalleryItem? ->
            if (after == null) return@insertSeparators null
            
            val fullFormat = SimpleDateFormat("EEEE, dd-MM-yyyy", Locale.getDefault())
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            
            val afterTimeMs = after.dateAdded * 1000L
            val afterTitle = if (afterTimeMs < oneWeekAgo) dateFormat.format(Date(afterTimeMs)) else fullFormat.format(Date(afterTimeMs))
            
            if (before == null) {
                return@insertSeparators GalleryListItem.Header(afterTitle)
            }
            
            val beforeTimeMs = before.dateAdded * 1000L
            val beforeTitle = if (beforeTimeMs < oneWeekAgo) dateFormat.format(Date(beforeTimeMs)) else fullFormat.format(Date(beforeTimeMs))
            
            if (beforeTitle != afterTitle) {
                return@insertSeparators GalleryListItem.Header(afterTitle)
            } else {
                return@insertSeparators null
            }
        }.map { item ->
            if (item is GalleryItem) GalleryListItem.Item(item) else item as GalleryListItem.Header
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

    fun selectAll() {
        // Not supported in Paging3 without a direct DB query.
    }

    fun selectRange(startUri: String, endUri: String) {
        // Not supported in Paging3 without memory indexing.
    }

    val allAlbums: StateFlow<List<AlbumBucket>> = combine(database.mediaDao().observeBuckets(), albumSortOrder) { buckets, order ->
        val excludedKeywords = setOf("Camera", "Screenshots", "Trash", "All", "Videos")
        
        val filtered = buckets.filter { bucket ->
            !excludedKeywords.contains(bucket.bucketName) && 
            !bucket.bucketName.contains("Screenrecordings", ignoreCase = true) &&
            !bucket.bucketName.contains("Screen records", ignoreCase = true) &&
            !bucket.bucketName.contains("Screenrecords", ignoreCase = true) &&
            !bucket.bucketName.contains("ScreenRecord", ignoreCase = true) &&
            !bucket.bucketName.contains("Screenshot", ignoreCase = true)
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

    val pinnedAlbums: StateFlow<List<AlbumBucket>> = combine(
        database.mediaDao().observeBuckets(),
        database.mediaDao().observeAllMedia()
    ) { buckets, allMedia ->
        val validMedia = allMedia.filter { it.bucketName != "Trash" }
        
        val allBucket = AlbumBucket(
            bucketName = "All",
            coverUri = validMedia.firstOrNull()?.uriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = validMedia.size,
            totalSizeBytes = validMedia.sumOf { it.size },
            maxDate = validMedia.maxOfOrNull { it.dateAdded } ?: 0L,
            coverUris = validMedia.take(4).map { Uri.parse(it.uriString) }
        )

        val cameraItems = buckets.filter { it.bucketName.equals("Camera", ignoreCase = true) }
        val cameraBucket = AlbumBucket(
            bucketName = cameraItems.firstOrNull()?.bucketName ?: "Camera",
            coverUri = cameraItems.maxByOrNull { it.maxDate }?.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = cameraItems.sumOf { it.itemCount },
            totalSizeBytes = cameraItems.sumOf { it.totalSizeBytes },
            maxDate = cameraItems.maxOfOrNull { it.maxDate } ?: 0L
        )
        
        val videos = validMedia.filter { it.isVideo }
        val videosBucket = AlbumBucket(
            bucketName = "Videos",
            coverUri = videos.firstOrNull()?.uriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = videos.size,
            totalSizeBytes = videos.sumOf { it.size },
            maxDate = videos.maxOfOrNull { it.dateAdded } ?: 0L
        )

        val screenshotsItems = buckets.filter { it.bucketName.contains("Screenshots", ignoreCase = true) || it.bucketName.contains("Screenshot", ignoreCase = true) }
        val screenshotsBucket = AlbumBucket(
            bucketName = screenshotsItems.firstOrNull()?.bucketName ?: "Screenshots",
            coverUri = screenshotsItems.maxByOrNull { it.maxDate }?.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = screenshotsItems.sumOf { it.itemCount },
            totalSizeBytes = screenshotsItems.sumOf { it.totalSizeBytes },
            maxDate = screenshotsItems.maxOfOrNull { it.maxDate } ?: 0L
        )

        val screenrecordingsItems = buckets.filter { it.bucketName.contains("Screenrecordings", ignoreCase = true) || it.bucketName.contains("Screen records", ignoreCase = true) || it.bucketName.contains("Screenrecords", ignoreCase = true) || it.bucketName.contains("ScreenRecord", ignoreCase = true) }
        val screenrecordingsBucket = AlbumBucket(
            bucketName = screenrecordingsItems.firstOrNull()?.bucketName ?: "Screenrecordings",
            coverUri = screenrecordingsItems.maxByOrNull { it.maxDate }?.coverUriString?.let { Uri.parse(it) } ?: Uri.EMPTY,
            itemCount = screenrecordingsItems.sumOf { it.itemCount },
            totalSizeBytes = screenrecordingsItems.sumOf { it.totalSizeBytes },
            maxDate = screenrecordingsItems.maxOfOrNull { it.maxDate } ?: 0L
        )
        
        listOf(allBucket, cameraBucket, videosBucket, screenshotsBucket, screenrecordingsBucket).filter { it.itemCount > 0 }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()


    private var searchJob: kotlinx.coroutines.Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _ftsSearchResults.value = emptyList()
            _isSearching.value = false
            searchJob?.cancel()
            return
        }
        // Debounce: wait 500ms after user stops typing, then fire searches
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            performUnifiedSearch(query)
        }
    }

    private val _ftsSearchResults = MutableStateFlow<List<GalleryItem>>(emptyList())
    val ftsSearchResults: StateFlow<List<GalleryItem>> = _ftsSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private fun performUnifiedSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                // Run FTS text-in-image search
                val ftsResults = withContext(Dispatchers.IO) {
                    try {
                        val ftsEntities = if (query.isNotBlank()) {
                            DatabaseProvider.searchFts(database, query)
                        } else {
                            emptyList()
                        }
                        ftsEntities.map { entity ->
                            GalleryItem(
                                id = entity.id.toString(),
                                uri = Uri.parse(entity.uriString),
                                bucketName = entity.bucketName,
                                dateAdded = entity.dateAdded,
                                size = entity.size,
                                name = entity.name,
                                dateModified = entity.dateModified,
                                path = entity.filePath,
                                isVideo = entity.isVideo,
                                durationMs = entity.durationMs
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GalleryViewModel", "FTS search failed: ${e.message}")
                        emptyList()
                    }
                }

                _ftsSearchResults.value = ftsResults
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
            _uiEvents.send(UiEvent.DeleteSuccess)
        }
    }

    fun moveSelectedMedia(targetBucket: String) {
        val selected = _selectedUris.value.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
                        android.widget.Toast.makeText(getApplication(), "Moved to $targetBucket", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "Move not supported on this Android version", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GalleryViewModel", "Error moving media: ${e.message}", e)
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
                        
                        // 2. Delete local database association
                        backupDao.deleteBackup(mediaId)
                        
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
            
            selected.forEach { uriStr ->
                val mediaItem = allMediaList.firstOrNull { it.uriString == uriStr }
                if (mediaItem != null) {
                    backupDao.insertOrUpdate(
                        TelegramBackupEntity(
                            mediaId = mediaItem.id,
                            telegramFileId = null,
                            telegramThumbFileId = null,
                            backupStatus = "PENDING",
                            backupTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            
            withContext(Dispatchers.Main) {
                clearSelection()
            }
            
            val request = OneTimeWorkRequestBuilder<com.inferno.gallery.workers.TelegramBackupWorker>()
                .build()
            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "TelegramBackupWorker",
                ExistingWorkPolicy.KEEP,
                request
            )
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

}
