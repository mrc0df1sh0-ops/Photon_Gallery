package com.inferno.gallery.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.inferno.gallery.ui.utils.haptickClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import com.inferno.gallery.ui.GalleryViewModel
import com.inferno.gallery.ui.GalleryItem

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

    // Trigger scan on first open
    LaunchedEffect(Unit) {
        galleryViewModel.scanForDuplicates()
    }

    // Map of Group key -> Set of selected item IDs for deletion
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

    fun groupKey(group: DuplicateGroup): String {
        return group.items.map { it.id }.sorted().joinToString(",")
    }

    // Auto-select on tab change or when results update
    LaunchedEffect(currentList, selectedTabIndex) {
        if (selectedForDeletion.isEmpty() && currentList.isNotEmpty()) {
            val autoSelected = mutableMapOf<String, Set<String>>()
            currentList.forEach { group ->
                val key = groupKey(group)
                val sorted = group.items.sortedByDescending { it.dateAdded }
                if (sorted.size > 1) {
                    val toDelete = sorted.drop(1).map { it.id }.toSet()
                    autoSelected[key] = toDelete
                }
            }
            selectedForDeletion = autoSelected
        }
    }

    val totalSelectedItems = selectedForDeletion.values.sumOf { it.size }
    val totalSelectedBytes = currentList.flatMap { it.items }
        .filter { item -> 
            selectedForDeletion.values.any { ids -> ids.contains(item.id) }
        }
        .sumOf { it.size }

    fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    val isScanning = scanState is DuplicateScanState.Scanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (totalSelectedItems > 0 && !isScanning) {
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
                                val itemsToDelete = currentList.flatMap { it.items }.filter { idsToDelete.contains(it.id) }
                                try {
                                    val uris = itemsToDelete.map { it.uri }
                                    val trashIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                                    trashLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(trashIntent.intentSender).build()
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.inferno.gallery.ui.theme.LocalHarmonizedColors.current.error
                            )
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
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
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
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
                            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (selectedTabIndex == 0) "No exact copies found" else "No near-identical photos found",
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
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
                                            if (current.contains(item.id)) {
                                                current.remove(item.id)
                                            } else {
                                                current.add(item.id)
                                            }
                                            selectedForDeletion = selectedForDeletion.toMutableMap().apply {
                                                put(key, current)
                                            }
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

@Composable
private fun ScanningAnimation(
    processed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanAnim")
    
    // Shape morph: animate corner radius between circle and rounded square
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph"
    )
    
    // Rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing scale
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
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
            // Shape morphing indicator
            val shapeSize = 72.dp
            Box(
                modifier = Modifier
                    .size(shapeSize)
                    .scale(breathScale)
                    .drawBehind {
                        val sizePx = size.minDimension
                        val strokeWidth = 4.dp.toPx()
                        
                        // Morph between circle (50%) and rounded square (25%)
                        val cornerRadius = sizePx * (0.25f + 0.25f * morphProgress)
                        
                        // Background shape
                        rotate(rotation) {
                            drawRoundRect(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        primaryColor,
                                        secondaryColor,
                                        primaryColor.copy(alpha = 0.3f),
                                        secondaryColor,
                                        primaryColor
                                    )
                                ),
                                cornerRadius = CornerRadius(cornerRadius),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        
                        // Inner filled shape (subtle)
                        rotate(rotation * 0.5f) {
                            drawRoundRect(
                                color = surfaceVariant.copy(alpha = 0.5f),
                                cornerRadius = CornerRadius(cornerRadius * 0.8f),
                                size = Size(sizePx * 0.6f, sizePx * 0.6f),
                                topLeft = Offset(sizePx * 0.2f, sizePx * 0.2f)
                            )
                        }
                    }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Scanning for duplicates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun DuplicateGroupItem(
    group: DuplicateGroup,
    isSimilar: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (GalleryItem) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                if (isSimilar) "Near Identical" else "Exact Copies",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    "${group.items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(group.items, key = { it.id }) { item ->
                val isSelected = selectedIds.contains(item.id)
                val context = LocalContext.current
                
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 0.88f else 1f,
                    animationSpec = spring(
                        dampingRatio = if (isSelected) Spring.DampingRatioNoBouncy else 0.55f,
                        stiffness = if (isSelected) Spring.StiffnessHigh else Spring.StiffnessMedium
                    ),
                    label = "duplicateItemScale"
                )
                
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(12.dp))
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
                    
                    Text(
                        "${item.size / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topEnd = 8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
