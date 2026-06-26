package com.inferno.gallery.ui

import android.net.Uri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inferno.gallery.ui.components.WavyProgressIndicator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CloudScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPhotoClick: (mediaId: String, bucketName: String?, query: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onNavigateToSettings: () -> Unit = {}
) {
    val cloudMedia by viewModel.cloudMedia.collectAsState()
    val pendingCount by viewModel.pendingBackupsCount.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val thumbnailCornerRadius by viewModel.thumbnailCornerRadius.collectAsState()
    val gridAutoPlay by viewModel.gridAutoPlay.collectAsState()
    val isRefreshing by viewModel.isCloudRefreshing.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val telegramConfigured by viewModel.telegramConfigured.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val failedBackups by viewModel.failedBackups.collectAsState()
    val gridState = rememberLazyGridState()
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    var showTipDialog by remember { mutableStateOf(false) }

    val galleryItems = remember(cloudMedia) {
        cloudMedia.map { item ->
            val uri = Uri.parse(item.uriString)
            val exists = java.io.File(item.filePath).exists()
            val resolved = when {
                item.telegramThumbFileId != null && !exists -> Uri.parse("telegram://${item.telegramThumbFileId}")
                item.telegramFileId != null && !exists -> Uri.parse("telegram://${item.telegramFileId}")
                else -> uri
            }
            GalleryItem(
                id = item.id.toString(),
                uri = uri,
                bucketName = item.bucketName,
                dateAdded = item.dateAdded,
                size = item.size,
                name = item.name,
                dateModified = item.dateModified,
                path = item.filePath,
                isVideo = item.isVideo,
                durationMs = item.durationMs,
                telegramFileId = item.telegramFileId,
                telegramThumbFileId = item.telegramThumbFileId,
                localExists = exists,
                resolvedUri = resolved
            )
        }
    }

    val totalSizeBytes = remember(cloudMedia) {
        cloudMedia.sumOf { it.size }
    }

    if (!telegramConfigured) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = "Cloud Upload",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Telegram Cloud Sync",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Securely backup your photos and videos to a private Telegram channel. Enjoy unlimited free storage with automatic metadata stripping for privacy.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToSettings,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Configure Telegram Bot")
                    }
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 6.dp,
            end = 6.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(gridState) {
                var initialItemUri: String? = null
                var dragStarted = false
                var startOffset = Offset.Zero

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        startOffset = offset
                        dragStarted = false
                        val x = offset.x.toInt()
                        val y = offset.y.toInt()

                        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                            x in itemInfo.offset.x..(itemInfo.offset.x + itemInfo.size.width) &&
                            y in itemInfo.offset.y..(itemInfo.offset.y + itemInfo.size.height)
                        }
                        item?.let {
                            val id = it.key as? String
                            val matchedItem = galleryItems.find { item -> item.id == id }
                            val uri = matchedItem?.uri?.toString()
                            if (uri != null) {
                                viewModel.toggleSelection(uri)
                                initialItemUri = uri
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val distance = (change.position - startOffset).getDistance()
                        if (distance > 40f) {
                            dragStarted = true
                        }

                        if (dragStarted) {
                            val x = change.position.x.toInt()
                            val y = change.position.y.toInt()

                            val inset = 30
                            val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                x in (itemInfo.offset.x + inset)..(itemInfo.offset.x + itemInfo.size.width - inset) &&
                                y in (itemInfo.offset.y + inset)..(itemInfo.offset.y + itemInfo.size.height - inset)
                            }
                            item?.let {
                                val id = it.key as? String
                                val matchedItem = galleryItems.find { item -> item.id == id }
                                val uri = matchedItem?.uri?.toString()
                                if (uri != null && uri != initialItemUri) {
                                    viewModel.addSelection(uri)
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // ── Stats Dashboard Card (Spans all 3 columns) ───────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Telegram Cloud Storage",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            if (isRefreshing) {
                                WavyProgressIndicator(
                                    modifier = Modifier.size(width = 36.dp, height = 24.dp),
                                    strokeWidth = 2.dp,
                                    amplitude = 3.dp,
                                    frequency = 1.5f,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                IconButton(
                                    onClick = { viewModel.refreshCloudBackups() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Synced Items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${galleryItems.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Storage Used",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = formatSize(totalSizeBytes),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        // ── Storage Breakdown Segmented Bar ──────────────────────────
                        val storageBreakdown = remember(cloudMedia) {
                            var photoBytes = 0L
                            var videoBytes = 0L
                            var photoCount = 0
                            var videoCount = 0
                            for (item in cloudMedia) {
                                if (item.isVideo) {
                                    videoBytes += item.size
                                    videoCount++
                                } else {
                                    photoBytes += item.size
                                    photoCount++
                                }
                            }
                            val total = photoBytes + videoBytes
                            val photoRatio = if (total > 0) photoBytes.toFloat() / total else 0f
                            val videoRatio = if (total > 0) videoBytes.toFloat() / total else 0f

                            object {
                                val photos = photoBytes
                                val videos = videoBytes
                                val photosCount = photoCount
                                val videosCount = videoCount
                                val totalBytes = total
                                val photoRatio = photoRatio
                                val videoRatio = videoRatio
                            }
                        }

                        val animatedPhotoRatio by animateFloatAsState(
                            targetValue = storageBreakdown.photoRatio,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "photoRatio"
                        )
                        val animatedVideoRatio by animateFloatAsState(
                            targetValue = storageBreakdown.videoRatio,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "videoRatio"
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        ) {
                            if (storageBreakdown.totalBytes > 0L) {
                                if (animatedPhotoRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(animatedPhotoRatio)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                if (animatedVideoRatio > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(animatedVideoRatio)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "Photos: ${formatSize(storageBreakdown.photos)} (${storageBreakdown.photosCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                                Text(
                                    text = "Videos: ${formatSize(storageBreakdown.videos)} (${storageBreakdown.videosCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(2.dp))

                        val currentBackupProgress = backupProgress
                        if (currentBackupProgress != null) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = currentBackupProgress.progress,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "backupProgressPercent"
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Backing up batch...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${(currentBackupProgress.progress * 100).toInt()}% (${currentBackupProgress.successful}/${currentBackupProgress.total})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        Text(
                                            text = "${currentBackupProgress.pending} pending",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    if (currentBackupProgress.successful > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = "${currentBackupProgress.successful} uploaded",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (currentBackupProgress.failed > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ) {
                                            Text(
                                                text = "${currentBackupProgress.failed} failed",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (systemStatus.isOffline) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ) {
                                            Text(
                                                text = "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = "Up to date",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                if (systemStatus.isOffline) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Text(
                                            text = "Offline",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                } else if (systemStatus.isBatteryPauseActive) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ) {
                                        Text(
                                            text = "Low Battery (paused)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gap/Spacer item
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Warning/Disclaimer and Tip Chips
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .clickable { showDisclaimerDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Disclaimer",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Disclaimer",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                        .clickable { showTipDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Security Tip",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Security Tip",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // ── Failed Backups Section ─────────────────────────────────────────
        if (failedBackups.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${failedBackups.size} backup${if (failedBackups.size != 1) "s" else ""} failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            FilledTonalButton(
                                onClick = { viewModel.retryAllFailedBackups() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry All", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        // Show first few failed file names
                        val displayItems = failedBackups.take(3)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            displayItems.forEach { item ->
                                Text(
                                    text = "• ${item.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                            if (failedBackups.size > 3) {
                                Text(
                                    text = "…and ${failedBackups.size - 3} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Photo Grid or Empty State ──────────────────────────────────────────
        if (galleryItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No media backed up to Telegram yet.\nSelect photos in the Gallery and tap 'Backup to Cloud'.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = galleryItems,
                key = { it.id }
            ) { item ->
                GalleryGridItem(
                    item = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = { _ ->
                        if (isSelectionMode) {
                            viewModel.toggleSelection(item.uri.toString())
                        } else {
                            onPhotoClick(item.id, "telegram_cloud", null)
                        }
                    },
                    isSelected = selectedUris.contains(item.uri.toString()),
                    gridAutoPlay = gridAutoPlay,
                    gridCellsCount = 4,
                    thumbnailCornerRadius = thumbnailCornerRadius
                )
            }
        }
    }

    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Disclaimer") },
            text = { Text("Do not delete backup database or manifest files from your Telegram chat. Deleting them will erase your cloud synchronization index, item counts, and storage usage statistics.") },
            confirmButton = {
                TextButton(onClick = { showDisclaimerDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    if (showTipDialog) {
        AlertDialog(
            onDismissRequest = { showTipDialog = false },
            icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text("Security Tip") },
            text = { Text("Enable Two-Factor Authentication (2FA) for your Telegram account to ensure maximum security and prevent unauthorized access to your cloud backups.") },
            confirmButton = {
                TextButton(onClick = { showTipDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
