package com.inferno.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.carousel.CarouselItemScope
import com.inferno.gallery.ui.components.WavyProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade

import coil3.size.Precision
import coil3.size.Size
import com.inferno.gallery.ui.theme.AppShapes
import com.inferno.gallery.ui.theme.ShapeLarge
import com.inferno.gallery.ui.theme.ShapeExtraLarge
import com.inferno.gallery.ui.theme.MotionTokens
import com.inferno.gallery.ui.theme.ShapeLargeIncreased3
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import coil3.gif.repeatCount
import coil3.video.videoFrameMillis
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.inferno.gallery.ui.utils.tick
import com.inferno.gallery.ui.utils.thud
@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onAlbumClick: (String) -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToDuplicateCleaner: () -> Unit = {},
    onNavigateToPhotoMap: () -> Unit = {}
) {
    val albums by viewModel.allAlbums.collectAsState()
    val pinnedAlbums by viewModel.pinnedAlbums.collectAsState()
    val userPinnedAlbums by viewModel.userPinnedAlbums.collectAsState()
    val userPinnedNames by viewModel.userPinnedFolderNames.collectAsState()
    val albumSortOrder by viewModel.albumSortOrder.collectAsState()
    val favoriteItems by viewModel.favoriteMedia.collectAsState()
    val gridAutoPlay by viewModel.gridAutoPlay.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()
    val vaultItemCount by viewModel.vaultItemCount.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Merge system pinned + user pinned, cap at 8 visible
    val systemPinned = remember(pinnedAlbums, trashCount) {
        val list = pinnedAlbums.toMutableList()
        val screenshotIndex = list.indexOfFirst { it.bucketName.contains("Screenshots", ignoreCase = true) || it.bucketName.contains("Screenshot", ignoreCase = true) }
        val trashBucket = AlbumBucket(
            bucketName = "Trash",
            coverUri = Uri.EMPTY,
            itemCount = trashCount
        )
        if (screenshotIndex != -1) {
            list.add(screenshotIndex + 1, trashBucket)
        } else {
            list.add(trashBucket)
        }
        list
    }

    val allPinnedCards = remember(systemPinned, userPinnedAlbums) {
        val filteredSystem = systemPinned.filter {
            it.bucketName != "Trash" && it.bucketName != "Favorites"
        }
        (filteredSystem + userPinnedAlbums).distinctBy { it.bucketName }
    }

    val maxVisible = 8
    val visiblePinned = allPinnedCards.take(maxVisible)
    val overflowPinned = allPinnedCards.drop(maxVisible)
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Long-press state for album cards
    var longPressAlbum by remember { mutableStateOf<String?>(null) }

    var showSortMenu by remember { mutableStateOf(false) }

    val lazyGridState = rememberLazyGridState()

    // -- Pull-down to reveal Private Space --
    var showPrivateSpaceCard by remember { mutableStateOf(false) }
    var pullAccumulator by remember { mutableStateOf(0f) }
    val pullThreshold = 150f // pixels of overscroll needed

    // Auto-hide after 3 seconds
    LaunchedEffect(showPrivateSpaceCard) {
        if (showPrivateSpaceCard) {
            kotlinx.coroutines.delay(3000)
            showPrivateSpaceCard = false
        }
    }

    val pullToRevealConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Only care about downward pull (negative y = scroll up, positive y = pull down)
                if (available.y > 0 && lazyGridState.firstVisibleItemIndex == 0 && lazyGridState.firstVisibleItemScrollOffset == 0) {
                    pullAccumulator += available.y
                    if (pullAccumulator >= pullThreshold && !showPrivateSpaceCard) {
                        showPrivateSpaceCard = true
                        pullAccumulator = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    return Offset.Zero // don't consume, let grid handle it
                } else {
                    pullAccumulator = 0f
                }
                return Offset.Zero
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(12),
        state = lazyGridState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .nestedScroll(pullToRevealConnection)
    ) {
        // -- Private Space Card (pull-down to reveal) --
        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedVisibility(
                visible = showPrivateSpaceCard,
                enter = expandVertically(
                    animationSpec = MotionTokens.snappySpring()
                ) + fadeIn(MotionTokens.snappySpring()),
                exit = shrinkVertically(
                    animationSpec = MotionTokens.snappySpring()
                ) + fadeOut(MotionTokens.snappySpring())
            ) {
                Surface(
                    onClick = {
                        val activity = context as? androidx.fragment.app.FragmentActivity
                        if (activity != null) {
                            viewModel.vaultAuthManager.authenticate(
                                activity = activity,
                                onSuccess = {
                                    showPrivateSpaceCard = false
                                    onNavigateToVault()
                                },
                                onFailure = {}
                            )
                        }
                    },
                    shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                            shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Private Space",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                if (vaultItemCount > 0) "$vaultItemCount hidden items" else "Tap to access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        // -- Favorites + Trash side-by-side cards --
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Favorites card
                Surface(
                    onClick = { onAlbumClick("Favorites") },
                    shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .height(70.dp)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                            shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (favoriteItems.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(favoriteItems.first().uri)
                                    .size(400, 400)
                                    .precision(Precision.EXACT)
                                    .crossfade(false)
                                    .apply {
                                        if (!gridAutoPlay) {
                                            repeatCount(0)
                                            videoFrameMillis(0)
                                        }
                                    }
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.6f)
                                            ),
                                            startY = 20f
                                        )
                                    )
                            )
                        } else {
                            // Empty state: soft gradient with icon
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = if (favoriteItems.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Favorites",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (favoriteItems.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${favoriteItems.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (favoriteItems.isNotEmpty()) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Trash card
                Surface(
                    onClick = { onAlbumClick("Trash") },
                    shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .height(70.dp)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                            shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Empty state: soft gradient with icon
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.errorContainer.copy(alpha = 0.2f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.error.copy(alpha = 0.18f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Trash",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (trashCount > 0) "$trashCount items" else "Empty",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // -- Pinned albums (system + user-pinned, max 8 visible) --
        if (visiblePinned.isNotEmpty()) {
            items(
                items = visiblePinned,
                key = { "pinned_${it.bucketName}" },
                span = { GridItemSpan(4) }
            ) { bucket ->
                val displayBucketName = when (bucket.bucketName) {
                    "Screenrecordings", "Screenrecords", "ScreenRecord" -> "Screen recordings"
                    else -> bucket.bucketName
                }
                val isUserPinned = bucket.bucketName in userPinnedNames
                if (isUserPinned) {
                    Box {
                        var showUnpinMenu by remember { mutableStateOf(false) }
                        AlbumCard(
                            bucket = bucket.copy(bucketName = displayBucketName),
                            gridAutoPlay = gridAutoPlay,
                            onClick = { onAlbumClick(bucket.bucketName) },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showUnpinMenu = true
                            }
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = showUnpinMenu,
                            onDismissRequest = { showUnpinMenu = false },
                            shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Unpin Album") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showUnpinMenu = false
                                    viewModel.togglePinAlbum(bucket.bucketName)
                                }
                            )
                        }
                    }
                } else {
                    AlbumCard(
                        bucket = bucket.copy(bucketName = displayBucketName),
                        gridAutoPlay = gridAutoPlay,
                        onClick = { onAlbumClick(bucket.bucketName) }
                    )
                }
            }
        }

        // -- Overflow pinned albums (>8) dropdown --
        if (overflowPinned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box {
                    androidx.compose.material3.TextButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "+${overflowPinned.size} more pinned",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        overflowPinned.forEach { bucket ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(bucket.bucketName) },
                                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                trailingIcon = { Text("${bucket.itemCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {
                                    showOverflowMenu = false
                                    onAlbumClick(bucket.bucketName)
                                }
                            )
                        }
                    }
                }
            }
        }

        // -- More albums (excluding user-pinned to avoid duplication) --
        val unpinnedAlbums = albums.filter { it.bucketName != "Favorites" && it.bucketName !in userPinnedNames }

        if (unpinnedAlbums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "More albums",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Box {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSortMenu = true
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = ShapeLargeIncreased3 as androidx.compose.foundation.shape.RoundedCornerShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Newest to Oldest") },
                                trailingIcon = {
                                    androidx.compose.material3.RadioButton(
                                        selected = albumSortOrder == SortOrder.NewToOld,
                                        onClick = null
                                    )
                                },
                                onClick = { viewModel.setAlbumSortOrder(SortOrder.NewToOld); showSortMenu = false }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Oldest to Newest") },
                                trailingIcon = {
                                    androidx.compose.material3.RadioButton(
                                        selected = albumSortOrder == SortOrder.OldToNew,
                                        onClick = null
                                    )
                                },
                                onClick = { viewModel.setAlbumSortOrder(SortOrder.OldToNew); showSortMenu = false }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Largest to Smallest") },
                                trailingIcon = {
                                    androidx.compose.material3.RadioButton(
                                        selected = albumSortOrder == SortOrder.BigToSmall,
                                        onClick = null
                                    )
                                },
                                onClick = { viewModel.setAlbumSortOrder(SortOrder.BigToSmall); showSortMenu = false }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Smallest to Largest") },
                                trailingIcon = {
                                    androidx.compose.material3.RadioButton(
                                        selected = albumSortOrder == SortOrder.SmallToBig,
                                        onClick = null
                                    )
                                },
                                onClick = { viewModel.setAlbumSortOrder(SortOrder.SmallToBig); showSortMenu = false }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("A to Z") },
                                trailingIcon = {
                                    androidx.compose.material3.RadioButton(
                                        selected = albumSortOrder == SortOrder.NameAsc,
                                        onClick = null
                                    )
                                },
                                onClick = { viewModel.setAlbumSortOrder(SortOrder.NameAsc); showSortMenu = false }
                            )
                        }
                    }
                }
            }

            items(
                items = unpinnedAlbums,
                key = { "folder_${it.bucketName}" },
                span = { GridItemSpan(3) }
            ) { bucket ->
                Box {
                    var showPinMenu by remember { mutableStateOf(false) }
                    AlbumCard(
                        bucket = bucket,
                        gridAutoPlay = gridAutoPlay,
                        onClick = { onAlbumClick(bucket.bucketName) },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showPinMenu = true
                        }
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = showPinMenu,
                        onDismissRequest = { showPinMenu = false },
                        shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        val isPinned = bucket.bucketName in userPinnedNames
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (isPinned) "Unpin Album" else "Pin Album") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showPinMenu = false
                                viewModel.togglePinAlbum(bucket.bucketName)
                            }
                        )
                    }
                }
            }
        }

        // -- Tools: Clean + Map side-by-side --
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clean card (Duplicate Cleaner)
                Surface(
                    onClick = onNavigateToDuplicateCleaner,
                    shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .height(70.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Clean",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Clean",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Map card (Photo Map)
                Surface(
                    onClick = onNavigateToPhotoMap,
                    shape = ShapeLarge as androidx.compose.foundation.shape.RoundedCornerShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .height(70.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Map,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Map,
                                contentDescription = "Map",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Map",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun Modifier.expressiveClick(onClick: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = if (isPressed) MotionTokens.snappySpring() else MotionTokens.bouncySpring(),
        label = "expressiveClickScale"
    )
    return this
        .scale(scale)
        .pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongPress = if (onLongPress != null) {{
                    haptic.thud()
                    onLongPress()
                }} else null
            )
        }
}

@Composable
fun CollageCover(
    uris: List<Uri>,
    modifier: Modifier = Modifier,
    gridAutoPlay: Boolean = true
) {
    val context = LocalContext.current
    val requests = remember(uris, gridAutoPlay) {
        uris.map { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(150, 150)
                .precision(Precision.EXACT)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .apply {
                    if (!gridAutoPlay) {
                        repeatCount(0)
                        videoFrameMillis(0)
                    }
                }
                .build()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AsyncImage(
                    model = requests.getOrNull(0),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                )
                AsyncImage(
                    model = requests.getOrNull(1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                )
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AsyncImage(
                    model = requests.getOrNull(2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                )
                AsyncImage(
                    model = requests.getOrNull(3),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    bucket: AlbumBucket,
    modifier: Modifier = Modifier,
    gridAutoPlay: Boolean = true,
    onClick: () -> Unit = {},
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val request = remember<ImageRequest>(bucket.coverUri, gridAutoPlay) {
        ImageRequest.Builder(context)
            .data(bucket.coverUri)
            .size(Size(300, 300))
            .precision(Precision.EXACT)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (!gridAutoPlay) {
                    repeatCount(0)
                    videoFrameMillis(0)
                }
            }
            .build()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .expressiveClick(onClick, onLongPress),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (bucket.coverUris.size == 4) {
                    CollageCover(
                        uris = bucket.coverUris,
                        modifier = Modifier.fillMaxSize(),
                        gridAutoPlay = gridAutoPlay
                    )
                } else if (bucket.coverUri == Uri.EMPTY) {
                    val icon = if (bucket.bucketName == "Favorites") {
                        Icons.Rounded.FavoriteBorder
                    } else {
                        Icons.Rounded.Folder
                    }
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = bucket.bucketName,
                        tint = if (bucket.bucketName == "Favorites") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    AsyncImage(
                        model = request,
                        contentDescription = bucket.bucketName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = bucket.bucketName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = java.text.NumberFormat.getInstance(java.util.Locale.US).format(bucket.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun TrashCard(
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .expressiveClick(onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.errorContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Recycle Bin",
                    tint = com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.error,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Recycle Bin",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = java.text.NumberFormat.getInstance(java.util.Locale.US).format(itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
