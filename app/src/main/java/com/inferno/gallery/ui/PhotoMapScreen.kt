package com.inferno.gallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.compose.AsyncImage
import coil3.request.*
import coil3.size.Precision
import com.inferno.gallery.ui.utils.haptickClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

// Helper to format date headers
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

// Helper to convert drawable to bitmap safely
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }
    
    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } else {
        Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    }
    
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoMapScreen(
    galleryViewModel: GalleryViewModel = viewModel(),
    onPhotoClick: (String, String?, String?) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    val clusters = remember(geotaggedMedia) {
        clusterPhotos(geotaggedMedia, clusterRadiusDeg = 0.01) // ~1km radius
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Box(modifier = Modifier.fillMaxSize()) {
        if (geotaggedMedia.isEmpty() && isScanning) {
            // Scanning state (imported from MapScanningState.kt)
            GpsScanningAnimation(
                scanState = gpsScanState,
                onBackClick = onBackClick
            )
        } else if (geotaggedMedia.isEmpty() && isDone) {
            // No geotagged photos (imported from MapScanningState.kt)
            NoGeotaggedPhotosState(onBackClick = onBackClick)
        } else {
            // Map with persistent image sheet
            var mapView by remember { mutableStateOf<MapView?>(null) }
            var focusedMarker by remember { mutableStateOf<Marker?>(null) }
            
            // Sheet height animation
            var sheetExpanded by remember { mutableStateOf(false) }
            val heightFraction by animateFloatAsState(
                targetValue = if (sheetExpanded) 0.50f else 0.30f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "sheetHeight"
            )

            // Group geotagged items by date
            val groupedMedia = remember(geotaggedMedia) {
                val list = mutableListOf<GalleryListItem>()
                var lastHeader: String? = null
                geotaggedMedia.forEach { item ->
                    val header = formatGroupHeader(item.dateAdded)
                    if (header != lastHeader) {
                        list.add(GalleryListItem.Header(header))
                        lastHeader = header
                    }
                    list.add(GalleryListItem.Item(item))
                }
                list
            }

            // Grid state to track scrolling
            val gridState = rememberLazyGridState()

            // Track the first visible image of the scrolled viewport
            val firstVisibleImage by remember(groupedMedia) {
                derivedStateOf {
                    if (groupedMedia.isEmpty()) return@derivedStateOf null
                    val startIndex = gridState.firstVisibleItemIndex
                    if (startIndex in groupedMedia.indices) {
                        for (i in startIndex until groupedMedia.size) {
                            val item = groupedMedia[i]
                            if (item is GalleryListItem.Item) {
                                return@derivedStateOf item.galleryItem
                            }
                        }
                    }
                    null
                }
            }

            // Scroll synchronization & Wavy Marker loading
            LaunchedEffect(firstVisibleImage, mapView) {
                val item = firstVisibleImage
                val map = mapView
                if (item != null && map != null) {
                    val lat = item.latitude
                    val lon = item.longitude
                    if (lat != null && lon != null) {
                        val geoPoint = GeoPoint(lat, lon)
                        
                        // Centering the map rapidly
                        map.controller.animateTo(geoPoint)

                        // Load thumbnail asynchronously using Coil
                        val loadedBitmap = withContext<Bitmap?>(Dispatchers.IO) {
                            try {
                                val loader = coil3.SingletonImageLoader.get(context)
                                val request = ImageRequest.Builder(context)
                                    .data(item.resolvedUri)
                                    .size(160, 160)
                                    .precision(Precision.EXACT)
                                    .allowHardware(false)
                                    .build()
                                val result = loader.execute(request)
                                val image = (result as? coil3.request.SuccessResult)?.image
                                when (image) {
                                    is BitmapImage -> image.bitmap
                                    is DrawableImage -> drawableToBitmap(image.drawable)
                                    else -> null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val wavyDrawable = if (loadedBitmap != null) {
                            createWavyDrawable(context, loadedBitmap)
                        } else {
                            createDefaultWavyDrawable(context)
                        }

                        var marker = focusedMarker
                        if (marker == null) {
                            marker = Marker(map).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            focusedMarker = marker
                        }
                        
                        marker.position = geoPoint
                        marker.icon = wavyDrawable
                        marker.title = item.name

                        map.overlays.remove(marker)
                        map.overlays.add(marker)
                        map.invalidate()
                    }
                } else {
                    focusedMarker?.let { marker ->
                        map?.overlays?.remove(marker)
                        focusedMarker = null
                        map?.invalidate()
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Map View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f - heightFraction)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                                mapView = this
                            }
                        },
                        update = { map ->
                            map.overlays.clear()

                            // Render clusters/other markers
                            clusters.forEach { cluster ->
                                val count = cluster.items.size
                                val marker = Marker(map)
                                marker.position = cluster.center
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                marker.title = "${count} photo${if (count > 1) "s" else ""}"

                                val sizeDp = (36 + minOf(count * 4, 40))
                                val density = context.resources.displayMetrics.density
                                val sizePx = (sizeDp * density).toInt()

                                val drawable = createClusterDrawable(
                                    context = context,
                                    count = count,
                                    sizePx = sizePx,
                                    bgColor = primaryColor.toArgb(),
                                    textColor = onPrimaryColor.toArgb()
                                )
                                marker.icon = drawable

                                marker.setOnMarkerClickListener { _, _ ->
                                    map.controller.animateTo(cluster.center)
                                    // Smoothly scroll list to the first image of this cluster
                                    val targetId = cluster.items.firstOrNull()?.id
                                    val index = groupedMedia.indexOfFirst {
                                        (it as? GalleryListItem.Item)?.galleryItem?.id == targetId
                                    }
                                    if (index >= 0) {
                                        coroutineScope.launch {
                                            gridState.animateScrollToItem(index)
                                        }
                                    }
                                    true
                                }
                                map.overlays.add(marker)
                            }

                            // Keep focusedMarker rendered on top
                            focusedMarker?.let {
                                map.overlays.add(it)
                            }

                            // Zoom out to bound initial clusters once when loaded
                            if (clusters.isNotEmpty() && focusedMarker == null) {
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

                    // Top Bar back overlay with transparent material styling
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            tonalElevation = 4.dp
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = "Photo Map",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
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
                            .padding(end = 16.dp, bottom = 16.dp)
                            .size(48.dp)
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Re-center", modifier = Modifier.size(22.dp))
                    }
                }

                // Bottom Image Grid Sheet
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(heightFraction)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Drag Handle / Header area
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        if (dragAmount < -5) {
                                            sheetExpanded = true
                                        } else if (dragAmount > 5) {
                                            sheetExpanded = false
                                        }
                                    }
                                }
                                .clickable { sheetExpanded = !sheetExpanded }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                        }

                        // Info title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GPS Photos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${geotaggedMedia.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Scrollable grid
                        Box(modifier = Modifier.weight(1f)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                state = gridState,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    count = groupedMedia.size,
                                    key = { index ->
                                        val item = groupedMedia[index]
                                        when (item) {
                                            is GalleryListItem.Header -> "header_${item.title}"
                                            is GalleryListItem.Item -> item.galleryItem.id
                                        }
                                    },
                                    span = { index ->
                                        val item = groupedMedia[index]
                                        if (item is GalleryListItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                                    }
                                ) { index ->
                                    val listItem = groupedMedia[index]
                                    when (listItem) {
                                        is GalleryListItem.Header -> {
                                            Text(
                                                text = listItem.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 12.dp)
                                            )
                                        }
                                        is GalleryListItem.Item -> {
                                            val item = listItem.galleryItem
                                            val cellContext = LocalContext.current
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .haptickClickable {
                                                        onPhotoClick(item.id, "geotagged", null)
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(cellContext)
                                                        .data(item.resolvedUri)
                                                        .crossfade(150)
                                                        .size(256, 256)
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
                    }
                }
            }
        }
    }
}

// ── Custom Wavy Marker Drawing ──

private fun createWavyDrawable(
    context: android.content.Context,
    bitmap: Bitmap
): Drawable {
    val sizePx = 150
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = Path()

    val centerX = sizePx / 2f
    val centerY = sizePx / 2f
    val r0 = sizePx / 2f - 10f
    val amplitude = r0 * 0.08f
    val waveCount = 10f
    val numPoints = 120

    for (i in 0 until numPoints) {
        val angle = i * (2 * Math.PI / numPoints)
        val r = r0 + amplitude * sin(waveCount * angle)
        val x = (centerX + r * cos(angle)).toFloat()
        val y = (centerY + r * sin(angle)).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    val shadowPath = Path(path).apply {
        offset(0f, 4f)
    }
    canvas.drawPath(shadowPath, shadowPaint)

    // Clip & Draw Image
    canvas.save()
    canvas.clipPath(path)
    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
    val destRect = Rect(0, 0, sizePx, sizePx)
    canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    canvas.restore()

    // Border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawPath(path, borderPaint)

    return BitmapDrawable(context.resources, output)
}

private fun createDefaultWavyDrawable(context: android.content.Context): Drawable {
    val sizePx = 120
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val path = Path()
    val centerX = sizePx / 2f
    val centerY = sizePx / 2f
    val r0 = sizePx / 2f - 8f
    val amplitude = r0 * 0.08f
    val waveCount = 10f
    val numPoints = 120

    for (i in 0 until numPoints) {
        val angle = i * (2 * Math.PI / numPoints)
        val r = r0 + amplitude * sin(waveCount * angle)
        val x = (centerX + r * cos(angle)).toFloat()
        val y = (centerY + r * sin(angle)).toFloat()
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.RED
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, fillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawPath(path, borderPaint)

    return BitmapDrawable(context.resources, output)
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
): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw circle background with shadow effect
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 2f, 0x44000000)
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, bgPaint)

    // Draw border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 5f, borderPaint)

    // Draw count text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sizePx * 0.38f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = sizePx / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(count.toString(), sizePx / 2f, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
