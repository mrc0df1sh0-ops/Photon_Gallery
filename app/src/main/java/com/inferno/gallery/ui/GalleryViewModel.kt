package com.inferno.gallery.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inferno.gallery.data.LocalMediaRepository

import com.inferno.gallery.data.SettingsRepository
import com.inferno.gallery.data.DockStyle
import com.inferno.gallery.data.FavoritesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val durationMs: Long? = null
)

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

data class AlbumBucket(
    val bucketName: String,
    val coverUri: Uri,
    val itemCount: Int,
    val totalSizeBytes: Long = 0L,
    val maxDate: Long = 0L
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalMediaRepository(application.contentResolver)
    private val settingsRepository = SettingsRepository(application)
    private val favoritesManager = FavoritesManager(application)

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



    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            favoritesManager.toggleFavorite(id)
        }
    }

    val allMedia: StateFlow<List<GalleryItem>> = repository.observeImages(null).map { mediaData ->
        mediaData.mapIndexed { index, data ->
            GalleryItem(
                id = index.toString(),
                uri = data.uri,
                bucketName = data.bucketName,
                dateAdded = data.dateAdded,
                size = data.size,
                name = data.name,
                dateModified = data.dateModified,
                path = data.path,
                isVideo = data.isVideo,
                durationMs = data.durationMs
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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

    val gridCellsCount: StateFlow<Int> = settingsRepository.gridCellsCountFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 4
    )

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

    val images: StateFlow<List<GalleryItem>> = combine(
        allMedia, sortOrder, _currentBucket, selectedFilterIndex
    ) { raw, order, bucket, filterIndex ->
        val folderName = when (filterIndex) {
            1 -> "Camera"
            2 -> "Screenshots"
            else -> null
        }
        val filtered = if (bucket == "All") {
            raw
        } else if (bucket == "Videos") {
            raw.filter { it.isVideo }
        } else if (bucket != null) {
            raw.filter { it.bucketName == bucket }
        } else if (folderName != null) {
            raw.filter { it.bucketName == folderName }
        } else {
            raw
        }

        when (order) {
            SortOrder.NewToOld -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.OldToNew -> filtered.sortedBy { it.dateAdded }
            SortOrder.SmallToBig -> filtered.sortedBy { it.size }
            SortOrder.BigToSmall -> filtered.sortedByDescending { it.size }
            SortOrder.NameAsc -> filtered.sortedBy { it.name }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupedImages: StateFlow<Map<String, List<GalleryItem>>> = images.map { sortedList ->
        val fullFormat = SimpleDateFormat("EEEE, dd-MM-yyyy", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val oneWeekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        
        sortedList.groupBy { item ->
            val timeMs = item.dateAdded * 1000L
            if (timeMs < oneWeekAgo) {
                dateFormat.format(Date(timeMs))
            } else {
                fullFormat.format(Date(timeMs))
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

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
        _selectedUris.value = images.value.map { it.uri.toString() }.toSet()
    }

    fun selectRange(startUri: String, endUri: String) {
        val list = images.value
        val startIndex = list.indexOfFirst { it.uri.toString() == startUri }
        val endIndex = list.indexOfFirst { it.uri.toString() == endUri }
        if (startIndex != -1 && endIndex != -1) {
            val min = minOf(startIndex, endIndex)
            val max = maxOf(startIndex, endIndex)
            val range = list.subList(min, max + 1).map { it.uri.toString() }
            val current = _selectedUris.value.toMutableSet()
            current.addAll(range)
            _selectedUris.value = current
        }
    }

    val allAlbums: StateFlow<List<AlbumBucket>> = combine(allMedia, albumSortOrder) { mediaList, order ->
        val excludedBuckets = setOf("Camera", "Screenshots")
        
        val buckets = mediaList.groupBy { it.bucketName }
            .filterKeys { !excludedBuckets.contains(it) && !it.contains("Screenrecordings", ignoreCase = true) }
            .map { (bucketName, items) ->
            val totalSize = items.sumOf { it.size }
            val maxDate = items.maxOfOrNull { it.dateAdded } ?: 0L
            AlbumBucket(
                bucketName = bucketName,
                coverUri = items.first().uri,
                itemCount = items.size,
                totalSizeBytes = totalSize,
                maxDate = maxDate
            )
        }
        
        val sortedBuckets = when (order) {
            SortOrder.NewToOld -> buckets.sortedByDescending { it.maxDate }
            SortOrder.OldToNew -> buckets.sortedBy { it.maxDate }
            SortOrder.SmallToBig -> buckets.sortedBy { it.totalSizeBytes }
            SortOrder.BigToSmall -> buckets.sortedByDescending { it.totalSizeBytes }
            SortOrder.NameAsc -> buckets.sortedBy { it.bucketName }
        }
        
        sortedBuckets
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedAlbums: StateFlow<List<AlbumBucket>> = allMedia.map { mediaList ->
        val allBucket = AlbumBucket(
            bucketName = "All",
            coverUri = mediaList.firstOrNull()?.uri ?: Uri.EMPTY,
            itemCount = mediaList.size,
            totalSizeBytes = mediaList.sumOf { it.size },
            maxDate = mediaList.maxOfOrNull { it.dateAdded } ?: 0L
        )
        
        val videos = mediaList.filter { it.isVideo }
        val videosBucket = AlbumBucket(
            bucketName = "Videos",
            coverUri = videos.firstOrNull()?.uri ?: Uri.EMPTY,
            itemCount = videos.size,
            totalSizeBytes = videos.sumOf { it.size },
            maxDate = videos.maxOfOrNull { it.dateAdded } ?: 0L
        )

        val cameraItems = mediaList.filter { it.bucketName == "Camera" }
        val cameraBucket = AlbumBucket(
            bucketName = "Camera",
            coverUri = cameraItems.firstOrNull()?.uri ?: Uri.EMPTY,
            itemCount = cameraItems.size,
            totalSizeBytes = cameraItems.sumOf { it.size },
            maxDate = cameraItems.maxOfOrNull { it.dateAdded } ?: 0L
        )

        val screenshotsItems = mediaList.filter { it.bucketName == "Screenshots" }
        val screenshotsBucket = AlbumBucket(
            bucketName = "Screenshots",
            coverUri = screenshotsItems.firstOrNull()?.uri ?: Uri.EMPTY,
            itemCount = screenshotsItems.size,
            totalSizeBytes = screenshotsItems.sumOf { it.size },
            maxDate = screenshotsItems.maxOfOrNull { it.dateAdded } ?: 0L
        )

        val screenrecordingsItems = mediaList.filter { it.bucketName.contains("Screenrecordings", ignoreCase = true) }
        val screenrecordingsBucket = AlbumBucket(
            bucketName = "Screenrecordings",
            coverUri = screenrecordingsItems.firstOrNull()?.uri ?: Uri.EMPTY,
            itemCount = screenrecordingsItems.size,
            totalSizeBytes = screenrecordingsItems.sumOf { it.size },
            maxDate = screenrecordingsItems.maxOfOrNull { it.dateAdded } ?: 0L
        )

        listOf(allBucket, cameraBucket, videosBucket, screenshotsBucket, screenrecordingsBucket)
            .filter { it.itemCount > 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<GalleryItem>> = combine(
        images, _searchQuery
    ) { items, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            items.filter {
                it.bucketName.contains(query, ignoreCase = true) ||
                it.uri.toString().contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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
        viewModelScope.launch {
            settingsRepository.updateGridCellsCount(count)
        }
    }

    private val _uiEvents = kotlinx.coroutines.channels.Channel<UiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun removeMediaOptimistically(uriString: String) {
        viewModelScope.launch {
            _uiEvents.send(UiEvent.DeleteSuccess)
        }
    }

    sealed class UiEvent {
        object DeleteSuccess : UiEvent()
    }

}
