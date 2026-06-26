@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.inferno.gallery.ui

import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.inferno.gallery.ui.utils.haptickClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCleanerScreen(
    galleryViewModel: GalleryViewModel,
    onBackClick: () -> Unit
) {
    val duplicates by galleryViewModel.duplicates.collectAsState()
    val similarPhotos by galleryViewModel.similarPhotos.collectAsState()
    val scanState by galleryViewModel.duplicateScanState.collectAsState()
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Exact Copies", "Near Identical")

    val currentList = if (selectedTabIndex == 0) duplicates else similarPhotos

    LaunchedEffect(Unit) { galleryViewModel.scanForDuplicates() }

    var selectedForDeletion by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val idsToDelete = selectedForDeletion.values.flatten()
            val itemsToDelete = currentList.flatMap { it.items }.filter { idsToDelete.contains(it.id) }
            galleryViewModel.deleteSelectedMediaFromDb(itemsToDelete.map { it.uri.toString() })
            selectedForDeletion = emptyMap()
        }
    }

    fun groupKey(group: DuplicateGroup): String =
        group.items.map { it.id }.sorted().joinToString(",")

    // Auto-select: keep largest, delete the rest
    LaunchedEffect(currentList, selectedTabIndex) {
        if (selectedForDeletion.isEmpty() && currentList.isNotEmpty()) {
            val autoSelected = mutableMapOf<String, Set<String>>()
            currentList.forEach { group ->
                val key = groupKey(group)
                val sorted = group.items.sortedByDescending { it.size }
                if (sorted.size > 1) {
                    autoSelected[key] = sorted.drop(1).map { it.id }.toSet()
                }
            }
            selectedForDeletion = autoSelected
        }
    }

    val totalSelectedItems = selectedForDeletion.values.sumOf { it.size }
    val totalSelectedBytes = currentList.flatMap { it.items }
        .filter { item -> selectedForDeletion.values.any { ids -> ids.contains(item.id) } }
        .sumOf { it.size }

    val totalWastedBytes = currentList.flatMap { group ->
        val sorted = group.items.sortedByDescending { it.size }
        sorted.drop(1)
    }.sumOf { it.size }

    val isScanning = scanState is DuplicateScanState.Scanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = totalSelectedItems > 0 && !isScanning,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "$totalSelectedItems selected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Reclaim ${formatSize(totalSelectedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val idsToDelete = selectedForDeletion.values.flatten()
                                val itemsToDelete = currentList.flatMap { it.items }
                                    .filter { idsToDelete.contains(it.id) }
                                try {
                                    val uris = itemsToDelete.map { it.uri }
                                    val trashIntent = MediaStore.createTrashRequest(
                                        context.contentResolver, uris, true
                                    )
                                    trashLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest
                                            .Builder(trashIntent.intentSender).build()
                                    )
                                } catch (e: Exception) { e.printStackTrace() }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.error
                            )
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            AnimatedContent(
                targetState = isScanning,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "scanContent"
            ) { scanning ->
                if (scanning) {
                    val state = scanState as? DuplicateScanState.Scanning
                    ScanningAnimation(
                        processed = state?.processed ?: 0,
                        total = state?.total ?: 0,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── Stats Header Card ──
                        if (currentList.isNotEmpty()) {
                            StatsHeaderCard(
                                groupCount = currentList.size,
                                wastedBytes = totalWastedBytes,
                                reclaimBytes = totalSelectedBytes
                            )
                        }

                        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = {
                                        selectedTabIndex = index
                                        selectedForDeletion = emptyMap()
                                    },
                                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                                )
                            }
                        }

                        if (currentList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (selectedTabIndex == 0) "No exact copies found"
                                        else "No near-identical photos found",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Your gallery is clean.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                items(currentList, key = { groupKey(it) }) { group ->
                                    val key = groupKey(group)
                                    DuplicateGroupItem(
                                        group = group,
                                        isSimilar = selectedTabIndex == 1,
                                        selectedIds = selectedForDeletion[key] ?: emptySet(),
                                        onToggleSelect = { item ->
                                            val current = selectedForDeletion[key]?.toMutableSet() ?: mutableSetOf()
                                            if (current.contains(item.id)) current.remove(item.id)
                                            else current.add(item.id)
                                            selectedForDeletion = selectedForDeletion.toMutableMap()
                                                .apply { put(key, current) }
                                        },
                                        onKeepOne = {
                                            // Keep the largest, delete the rest
                                            val keeper = group.items.maxByOrNull { it.size }
                                            val toDelete = group.items
                                                .filter { it.id != keeper?.id }
                                                .map { it.id }.toSet()
                                            selectedForDeletion = selectedForDeletion.toMutableMap()
                                                .apply { put(key, toDelete) }
                                        },
                                        onSelectAll = {
                                            selectedForDeletion = selectedForDeletion.toMutableMap()
                                                .apply { put(key, group.items.map { it.id }.toSet()) }
                                        },
                                        onDeselect = {
                                            selectedForDeletion = selectedForDeletion.toMutableMap()
                                                .apply { put(key, emptySet()) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Stats Header Card ──

@Composable
private fun StatsHeaderCard(
    groupCount: Int,
    wastedBytes: Long,
    reclaimBytes: Long
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val animatedCount by animateIntAsState(
        targetValue = groupCount,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "groupCount"
    )
    val animatedWasted by animateIntAsState(
        targetValue = (wastedBytes / (1024 * 1024)).toInt().coerceAtLeast(0),
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "wastedMb"
    )
    val animatedReclaim by animateIntAsState(
        targetValue = (reclaimBytes / (1024 * 1024)).toInt().coerceAtLeast(0),
        animationSpec = tween(1300, easing = FastOutSlowInEasing),
        label = "reclaimMb"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(primaryColor, tertiaryColor)))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(value = "$animatedCount", label = "groups")
            StatDivider()
            StatCell(value = "${animatedWasted} MB", label = "wasted")
            StatDivider()
            StatCell(value = "${animatedReclaim} MB", label = "to reclaim")
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(36.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.3f))
    )
}

// ── Group Item ──

@Composable
fun DuplicateGroupItem(
    group: DuplicateGroup,
    isSimilar: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (GalleryItem) -> Unit,
    onKeepOne: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit
) {
    val keeperId = group.items.maxByOrNull { it.size }?.id

    var compareExpanded by remember { mutableStateOf(false) }

    Column {
        // Group header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                if (isSimilar) "Near Identical" else "Exact Copies",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            // Confidence badge for similar
            if (isSimilar) {
                val badgeColor = when (group.confidence) {
                    "High" -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val badgeText = when (group.confidence) {
                    "High" -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor,
                    contentColor = badgeText
                ) {
                    Text(
                        "${group.confidence} similarity",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            // Count badge
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    "${group.items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // ── Action Chips ──
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            item {
                SuggestionChip(
                    onClick = onKeepOne,
                    label = { Text("Keep Best", style = MaterialTheme.typography.labelMedium) },
                    icon = {
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                SuggestionChip(
                    onClick = onSelectAll,
                    label = { Text("Select All", style = MaterialTheme.typography.labelMedium) },
                    icon = {
                        Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                SuggestionChip(
                    onClick = onDeselect,
                    label = { Text("Deselect", style = MaterialTheme.typography.labelMedium) },
                    icon = {
                        Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            if (group.items.size >= 2) {
                item {
                    FilterChip(
                        selected = compareExpanded,
                        onClick = { compareExpanded = !compareExpanded },
                        label = { Text("Compare", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }

        // ── Compare Viewer (expandable) ──
        AnimatedVisibility(
            visible = compareExpanded && group.items.size >= 2,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CompareDiffViewer(
                left = group.items[0],
                right = group.items[1],
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(bottom = 10.dp)
            )
        }

        // ── Thumbnail Strip ──
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.items, key = { it.id }) { item ->
                val isSelected = selectedIds.contains(item.id)
                val isKeeper = item.id == keeperId
                val context = LocalContext.current

                val cardScale by animateFloatAsState(
                    targetValue = if (isSelected) 0.88f else 1f,
                    animationSpec = spring(
                        dampingRatio = if (isSelected) Spring.DampingRatioNoBouncy else 0.55f,
                        stiffness = if (isSelected) Spring.StiffnessHigh else Spring.StiffnessMedium
                    ),
                    label = "dupItemScale"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isKeeper)
                        Color(0xFFFFD700) // gold
                    else if (isSelected)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else
                        Color.Transparent,
                    label = "borderColor"
                )

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(cardScale)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isKeeper || isSelected) 2.5.dp else 0.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .haptickClickable { onToggleSelect(item) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.resolvedUri)
                            .crossfade(true)
                            .size(320)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // File size label
                    Text(
                        formatSize(item.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topEnd = 8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )

                    // Crown badge (keeper)
                    if (isKeeper) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Best quality",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    // Delete overlay for selected non-keepers
                    if (isSelected && !isKeeper) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Marked for deletion",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Side-by-side Compare Diff Viewer ──

@Composable
private fun CompareDiffViewer(
    left: GalleryItem,
    right: GalleryItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dividerFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(modifier = modifier) {
        val totalWidth = maxWidth

        // Right image (full width behind)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(right.resolvedUri)
                .crossfade(true)
                .size(640)
                .build(),
            contentDescription = "Right image",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Left image clipped to dividerFraction
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(totalWidth * dividerFraction)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(left.resolvedUri)
                    .crossfade(true)
                    .size(640)
                    .build(),
                contentDescription = "Left image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(totalWidth).fillMaxHeight()
            )
        }

        // Draggable divider handle
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = totalWidth * dividerFraction - 18.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        val totalWidthPx = with(density) { totalWidth.toPx() }
                        val delta = dragAmount / totalWidthPx
                        dividerFraction = (dividerFraction + delta).coerceIn(0.05f, 0.95f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                contentDescription = "Drag to compare",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        // Labels
        Text(
            text = "← ${formatSize(left.size)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Text(
            text = "${formatSize(right.size)} →",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ── Scanning Animation ──

@Composable
private fun ScanningAnimation(
    processed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanAnim")

    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        ), label = "morph"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        ), label = "breathe"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceContainerHighest

    val progress = if (total > 0) processed.toFloat() / total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.ContainedLoadingIndicator(
                modifier = Modifier.size(72.dp)
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Scanning for duplicates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (total > 0) {
                Text(
                    "$processed / $total files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else {
                Text(
                    "Preparing…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Utilities ──

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.size - 1)
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
