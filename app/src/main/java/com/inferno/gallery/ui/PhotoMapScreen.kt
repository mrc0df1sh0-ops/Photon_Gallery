package com.inferno.gallery.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.inferno.gallery.ui.utils.haptickClickable
import com.inferno.gallery.ui.theme.ShapeLarge
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.*

// ── Data Classes ──

data class PhotoCluster(
    val center: GeoPoint,
    val items: List<GalleryItem>,
    val radius: Double // in degrees
)

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoMapScreen(
    galleryViewModel: GalleryViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val gpsScanState by galleryViewModel.gpsScanState.collectAsState()
    val geotaggedMedia by galleryViewModel.geotaggedMedia.collectAsState()

    // Initialize osmdroid config
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().osmdroidBasePath = context.cacheDir
        Configuration.getInstance().osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid")
        // Load existing geotagged media first, then scan for new GPS data
        galleryViewModel.loadGeotaggedMedia()
        galleryViewModel.scanGpsMetadata()
    }

    val isScanning = gpsScanState is GalleryViewModel.GpsScanState.Scanning
    val isDone = gpsScanState is GalleryViewModel.GpsScanState.Done || geotaggedMedia.isNotEmpty()

    // Cluster state
    var selectedCluster by remember { mutableStateOf<PhotoCluster?>(null) }
    var showFullViewer by remember { mutableStateOf(false) }
    var fullViewerStartIndex by remember { mutableIntStateOf(0) }

    // Compute clusters from geotagged media
    val clusters = remember(geotaggedMedia) {
        clusterPhotos(geotaggedMedia, clusterRadiusDeg = 0.01) // ~1km radius
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxSize()) {
        if (geotaggedMedia.isEmpty() && isScanning) {
            // Scanning state
            GpsScanningAnimation(
                scanState = gpsScanState,
                onBackClick = onBackClick
            )
        } else if (geotaggedMedia.isEmpty() && isDone) {
            // No geotagged photos
            NoGeotaggedPhotosState(onBackClick = onBackClick)
        } else {
            // Map with data
            Box(modifier = Modifier.fillMaxSize()) {
                // Map
                var mapView by remember { mutableStateOf<MapView?>(null) }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(5.0)
                            // Disable built-in zoom controls for clean look
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                            mapView = this
                        }
                    },
                    update = { map ->
                        map.overlays.clear()

                        clusters.forEach { cluster ->
                            val count = cluster.items.size
                            val marker = Marker(map)
                            marker.position = cluster.center
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            marker.title = "${count} photo${if (count > 1) "s" else ""}"

                            // Scale marker icon based on cluster size
                            val sizeDp = (36 + minOf(count * 4, 40))
                            val density = context.resources.displayMetrics.density
                            val sizePx = (sizeDp * density).toInt()

                            // Create custom cluster marker drawable
                            val drawable = createClusterDrawable(
                                context = context,
                                count = count,
                                sizePx = sizePx,
                                bgColor = primaryColor.toArgb(),
                                textColor = onPrimaryColor.toArgb()
                            )
                            marker.icon = drawable

                            marker.setOnMarkerClickListener { _, _ ->
                                selectedCluster = cluster
                                true
                            }
                            map.overlays.add(marker)
                        }

                        // Fit map to show all markers
                        if (clusters.isNotEmpty() && selectedCluster == null) {
                            try {
                                val points = clusters.map { it.center }
                                val minLat = points.minOf { it.latitude }
                                val maxLat = points.maxOf { it.latitude }
                                val minLon = points.minOf { it.longitude }
                                val maxLon = points.maxOf { it.longitude }
                                val padding = 0.02
                                map.post {
                                    map.zoomToBoundingBox(
                                        BoundingBox(
                                            maxLat + padding, maxLon + padding,
                                            minLat - padding, minLon - padding
                                        ),
                                        true, 80
                                    )
                                }
                            } catch (_: Exception) {}
                        }

                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top bar overlay
                TopAppBar(
                    title = {
                        Text(
                            "Photo Map",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = surfaceColor.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.statusBarsPadding()
                )

                // Stats chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 64.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${geotaggedMedia.size} photos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Loading indicator while scanning in background
                if (isScanning) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 64.dp, start = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.inferno.gallery.ui.components.ShapeMorphLoadingIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contained = false
                            )
                            val state = gpsScanState as? GalleryViewModel.GpsScanState.Scanning
                            Text(
                                "Scanning ${state?.processed ?: 0}/${state?.total ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Re-center FAB
                FloatingActionButton(
                    onClick = {
                        mapView?.let { map ->
                            if (clusters.isNotEmpty()) {
                                val points = clusters.map { it.center }
                                val minLat = points.minOf { it.latitude }
                                val maxLat = points.maxOf { it.latitude }
                                val minLon = points.minOf { it.longitude }
                                val maxLon = points.maxOf { it.longitude }
                                val padding = 0.02
                                map.zoomToBoundingBox(
                                    BoundingBox(
                                        maxLat + padding, maxLon + padding,
                                        minLat - padding, minLon - padding
                                    ),
                                    true, 80
                                )
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = if (selectedCluster != null) 280.dp else 24.dp
                        )
                        .navigationBarsPadding()
                        .size(48.dp)
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Re-center", modifier = Modifier.size(22.dp))
                }

                // Bottom sheet for selected cluster
                AnimatedVisibility(
                    visible = selectedCluster != null,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMedium)) { it },
                    exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    selectedCluster?.let { cluster ->
                        ClusterPhotoSheet(
                            cluster = cluster,
                            onDismiss = { selectedCluster = null },
                            onPhotoClick = { index ->
                                fullViewerStartIndex = index
                                showFullViewer = true
                            }
                        )
                    }
                }
            }
        }

        // Full screen viewer overlay
        if (showFullViewer && selectedCluster != null) {
            FullScreenMapViewer(
                items = selectedCluster!!.items,
                startIndex = fullViewerStartIndex,
                onDismiss = { showFullViewer = false }
            )
        }
    }
}

// ── Cluster Photo Sheet ──

@Composable
private fun ClusterPhotoSheet(
    cluster: PhotoCluster,
    onDismiss: () -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${cluster.items.size} photo${if (cluster.items.size > 1) "s" else ""} here",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Tap to view full screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Photo strip
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cluster.items.size) { index ->
                    val item = cluster.items[index]
                    val context = LocalContext.current
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(160.dp)
                            .haptickClickable { onPhotoClick(index) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.resolvedUri)
                                .crossfade(150)
                                .size(400, 400)
                                .precision(Precision.EXACT)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ── Full Screen Viewer ──

@Composable
private fun FullScreenMapViewer(
    items: List<GalleryItem>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.resolvedUri)
                    .crossfade(150)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top overlay with close + counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(48.dp)) // Balance the row
        }
    }
}

// ── GPS Scanning Animation ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpsScanningAnimation(
    scanState: GalleryViewModel.GpsScanState,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Map") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        val state = scanState as? GalleryViewModel.GpsScanState.Scanning
        val total = state?.total ?: 0
        val processed = state?.processed ?: 0
        val progress = if (total > 0) processed.toFloat() / total else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "gpsProgress"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "gpsScan")
        val morphProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "morph"
        )
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
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
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val surfaceVariant = MaterialTheme.colorScheme.surfaceContainerHighest

        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(breathScale)
                        .drawBehind {
                            val sizePx = size.minDimension
                            val strokeWidth = 4.dp.toPx()
                            val cornerRadius = sizePx * (0.25f + 0.25f * morphProgress)

                            rotate(rotation) {
                                drawRoundRect(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            primaryColor,
                                            tertiaryColor,
                                            primaryColor.copy(alpha = 0.3f),
                                            tertiaryColor,
                                            primaryColor
                                        )
                                    ),
                                    cornerRadius = CornerRadius(cornerRadius),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }

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
                    "Scanning GPS data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                if (total > 0) {
                    Text(
                        "$processed / $total photos",
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
}

// ── No Photos State ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoGeotaggedPhotosState(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Map") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    "No location data",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    "None of your photos have GPS coordinates embedded. Photos taken with location enabled will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Clustering ──

private fun clusterPhotos(
    photos: List<GalleryItem>,
    clusterRadiusDeg: Double
): List<PhotoCluster> {
    if (photos.isEmpty()) return emptyList()

    val geoPhotos = photos.filter { it.latitude != null && it.longitude != null }
    if (geoPhotos.isEmpty()) return emptyList()

    val assigned = BooleanArray(geoPhotos.size)
    val clusters = mutableListOf<PhotoCluster>()

    for (i in geoPhotos.indices) {
        if (assigned[i]) continue

        val centerLat = geoPhotos[i].latitude!!
        val centerLon = geoPhotos[i].longitude!!
        val clusterItems = mutableListOf(geoPhotos[i])
        assigned[i] = true

        for (j in i + 1 until geoPhotos.size) {
            if (assigned[j]) continue
            val lat = geoPhotos[j].latitude!!
            val lon = geoPhotos[j].longitude!!
            val dist = sqrt((centerLat - lat).pow(2) + (centerLon - lon).pow(2))
            if (dist <= clusterRadiusDeg) {
                clusterItems.add(geoPhotos[j])
                assigned[j] = true
            }
        }

        val avgLat = clusterItems.mapNotNull { it.latitude }.average()
        val avgLon = clusterItems.mapNotNull { it.longitude }.average()

        clusters.add(
            PhotoCluster(
                center = GeoPoint(avgLat, avgLon),
                items = clusterItems.sortedByDescending { it.dateAdded },
                radius = clusterRadiusDeg
            )
        )
    }

    return clusters.sortedByDescending { it.items.size }
}

// ── Custom Cluster Marker Drawable ──

private fun createClusterDrawable(
    context: android.content.Context,
    count: Int,
    sizePx: Int,
    bgColor: Int,
    textColor: Int
): android.graphics.drawable.Drawable {
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Draw circle background with shadow effect
    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = android.graphics.Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, 0x44000000)
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, bgPaint)

    // Draw border
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 5f, borderPaint)

    // Draw count text
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sizePx * 0.38f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = sizePx / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(count.toString(), sizePx / 2f, textY, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
