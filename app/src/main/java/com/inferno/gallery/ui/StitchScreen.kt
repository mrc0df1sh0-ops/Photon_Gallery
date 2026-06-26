package com.inferno.gallery.ui

import com.inferno.gallery.ui.theme.ShapeMedium

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.inferno.gallery.data.db.DatabaseProvider

import com.inferno.gallery.workers.MediaSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Data models ───────────────────────────────────────────────────────────────

/**
 * A single image in the stitch strip. [id] is a stable unique key for
 * Compose keying; it encodes both the URI and the insertion nanotime so
 * that two identical URIs added at different times remain distinct items.
 */
data class StitchItem(
    val uri: Uri,
    val id: String = "${uri}_${System.nanoTime()}"
)

/** Direction in which images are joined. */
enum class StitchOrientation { VERTICAL, HORIZONTAL }

/**
 * How each image is scaled to the shared dimension.
 *
 * [FILL] - image stretched to fill the shared dimension (no letter-boxing).
 * [FIT]  - image scaled uniformly; narrow images will be letter-boxed with
 *           the background color on both sides.
 */
enum class StitchScaleMode { FILL, FIT }

/** Horizontal alignment of images narrower than the canvas (vertical mode only). */
enum class StitchAlignment { START, CENTER, END }

// ── StitchScreen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StitchScreen(
    initialUris: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ── Core editable state ─────────────────────────────────────────────────
    var stitchItems by remember {
        mutableStateOf(initialUris.map { StitchItem(Uri.parse(it)) })
    }
    var orientation  by remember { mutableStateOf(StitchOrientation.VERTICAL) }
    var spacingDp    by remember { mutableFloatStateOf(4f) }
    var alignment    by remember { mutableStateOf(StitchAlignment.CENTER) }
    var scaleMode    by remember { mutableStateOf(StitchScaleMode.FILL) }
    var bgColor      by remember { mutableStateOf(Color.Black) }

    // ── UI state ─────────────────────────────────────────────────────────────
    var isSaving          by remember { mutableStateOf(false) }
    var showGalleryPicker by remember { mutableStateOf(false) }
    var showSaveSheet     by remember { mutableStateOf(false) }

    // ── Gallery media for the add-more picker ─────────────────────────────
    val database      = remember { DatabaseProvider.getDatabase(context) }
    val allMediaList  by remember { database.mediaDao().observeAllMedia() }
        .collectAsState(initial = emptyList())
    val imageMedia    = remember(allMediaList) {
        allMediaList.filter { !it.isVideo && it.bucketName != "Trash" }
    }

    BackHandler {
        when {
            showGalleryPicker -> showGalleryPicker = false
            showSaveSheet     -> showSaveSheet = false
            else              -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier       = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(72.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(
                                "Stitch",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text  = "${stitchItems.size} image${if (stitchItems.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledIconButton(
                            onClick  = { if (!isSaving) showSaveSheet = true },
                            enabled  = !isSaving && stitchItems.size >= 2,
                            colors   = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor   = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = "Save")
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // ── Live Preview ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        // Saving progress — WavyProgressIndicator per M3E rule
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            androidx.compose.material3.ContainedLoadingIndicator(
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text  = "Stitching…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        StitchPreview(
                            items       = stitchItems,
                            orientation = orientation,
                            spacingDp   = spacingDp,
                            bgColor     = bgColor
                        )
                    }
                }

                // ── Reorderable thumbnail strip ───────────────────────────────
                ReorderableThumbnailStrip(
                    items     = stitchItems,
                    onReorder = { from, to ->
                        stitchItems = stitchItems.toMutableList().also {
                            it.add(to, it.removeAt(from))
                        }
                    },
                    onRemove  = { index ->
                        if (stitchItems.size > 2) {
                            stitchItems = stitchItems.toMutableList().also { it.removeAt(index) }
                        } else {
                            Toast.makeText(context, "Need at least 2 images", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAddMore = { showGalleryPicker = true }
                )

                // ── Controls panel ────────────────────────────────────────────
                StitchControlsPanel(
                    orientation        = orientation,
                    spacingDp          = spacingDp,
                    alignment          = alignment,
                    scaleMode          = scaleMode,
                    bgColor            = bgColor,
                    onOrientationChange = { orientation = it },
                    onSpacingChange     = { spacingDp = it },
                    onAlignmentChange   = { alignment = it },
                    onScaleModeChange   = { scaleMode = it },
                    onBgColorChange     = { bgColor = it }
                )
            }
        }

        // ── Save options bottom sheet ─────────────────────────────────────────
        if (showSaveSheet) {
            SaveOptionsSheet(
                onDismiss = { showSaveSheet = false },
                onSave    = { format ->
                    showSaveSheet = false
                    isSaving = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            saveStitchedImage(
                                context    = context,
                                items      = stitchItems,
                                orientation = orientation,
                                spacingPx   = spacingDp * context.resources.displayMetrics.density,
                                alignment   = alignment,
                                scaleMode   = scaleMode,
                                bgColor     = bgColor,
                                format      = format
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                                isSaving = false
                                onBack()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Error saving: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                isSaving = false
                            }
                        }
                    }
                }
            )
        }

        // ── Add-more gallery picker overlay ───────────────────────────────────
        AnimatedVisibility(
            visible = showGalleryPicker,
            enter   = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                      slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it },
            exit    = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                      slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it }
        ) {
            // LocalGalleryChooser is defined in CollageScreen.kt (same package, public)
            LocalGalleryChooser(
                mediaList    = imageMedia,
                maxSelection = 30,
                onDismiss    = { showGalleryPicker = false },
                onConfirm    = { chosenUris ->
                    stitchItems = stitchItems + chosenUris.map { StitchItem(it) }
                    showGalleryPicker = false
                }
            )
        }
    }
}

// ── StitchPreview ─────────────────────────────────────────────────────────────

/**
 * Scrollable live preview of the stitched strip.
 *
 * Vertical mode: images stacked in a Column (full-width), vertically scrollable.
 * Horizontal mode: images in a Row (full-height), horizontally scrollable.
 * The background color and spacing are reflected in real time.
 */
@Composable
private fun StitchPreview(
    items: List<StitchItem>,
    orientation: StitchOrientation,
    spacingDp: Float,
    bgColor: Color
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (orientation == StitchOrientation.VERTICAL) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
            ) {
                items.forEach { item ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(900, 1800)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale       = ContentScale.FillWidth,
                        modifier           = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
            ) {
                items.forEach { item ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(1800, 900)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        contentScale       = ContentScale.FillHeight,
                        modifier           = Modifier.fillMaxHeight()
                    )
                }
            }
        }
    }
}

// ── ReorderableThumbnailStrip ─────────────────────────────────────────────────

/**
 * A horizontally-scrollable strip of thumbnails that supports:
 * - Long-press drag-to-reorder with spring-animated lift
 * - Haptic feedback on pick-up and on each slot-change during drag
 * - Tap × badge to remove an image
 * - Tap + button to open the add-more picker
 *
 * Drag implementation:
 * Each item records its centre-X in [itemCentersX] via [onGloballyPositioned].
 * While dragging, the dragged item translates by [dragOffsetX]. The [targetIndex]
 * is computed by finding which recorded centre-X is closest to the dragged item's
 * current centre-X. On drag end, the list is mutated once via [onReorder].
 */
@Composable
private fun ReorderableThumbnailStrip(
    items: List<StitchItem>,
    onReorder: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onAddMore: () -> Unit
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX   by remember { mutableFloatStateOf(0f) }
    var targetIndex   by remember { mutableStateOf(0) }

    // Stores the root-coordinate centre-X of each thumbnail slot
    val itemCentersX = remember { mutableStateMapOf<Int, Float>() }

    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isDragging = draggingIndex == index

                // Spring-animated lift effect
                val liftElevation by animateDpAsState(
                    targetValue    = if (isDragging) 16.dp else 0.dp,
                    animationSpec  = spring(stiffness = Spring.StiffnessHigh),
                    label          = "stitchThumbElevation"
                )
                val liftScale by animateFloatAsState(
                    targetValue   = if (isDragging) 1.15f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessHigh
                    ),
                    label         = "stitchThumbScale"
                )
                // Remove badge spring — fades+scales out while item is being dragged
                val removeBadgeAlpha by animateFloatAsState(
                    targetValue   = if (isDragging) 0f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label         = "removeBadgeAlpha"
                )
                val removeBadgeScale by animateFloatAsState(
                    targetValue   = if (isDragging) 0.6f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessHigh
                    ),
                    label         = "removeBadgeScale"
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        // Record centre-X for drag-target calculation
                        .onGloballyPositioned { coords ->
                            itemCentersX[index] =
                                coords.positionInRoot().x + coords.size.width / 2f
                        }
                        // Apply spring-animated lift transform
                        .graphicsLayer {
                            scaleX       = liftScale
                            scaleY       = liftScale
                            translationX = if (isDragging) dragOffsetX else 0f
                            shadowElevation = liftElevation.toPx()
                        }
                        .zIndex(if (isDragging) 10f else 0f)
                        .shadow(liftElevation, ShapeMedium)
                        .clip(ShapeMedium)
                        .border(
                            width  = if (isDragging) 2.dp else 0.dp,
                            color  = if (isDragging) MaterialTheme.colorScheme.primary
                                     else Color.Transparent,
                            shape  = ShapeMedium
                        )
                        // Long-press → drag-to-reorder
                        .pointerInput(index, items.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingIndex = index
                                    dragOffsetX   = 0f
                                    targetIndex   = index
                                },
                                onDrag = { _, delta ->
                                    dragOffsetX += delta.x
                                    val currentCentreX =
                                        (itemCentersX[index] ?: 0f) + dragOffsetX
                                    val newTarget = itemCentersX.entries
                                        .minByOrNull { abs(it.value - currentCentreX) }
                                        ?.key ?: index
                                    if (newTarget != targetIndex) {
                                        // Haptic click on each slot change
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        targetIndex = newTarget
                                    }
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    draggingIndex = null
                                    dragOffsetX   = 0f
                                    if (from != null) {
                                        val to = targetIndex.coerceIn(0, items.size - 1)
                                        if (from != to) onReorder(from, to)
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetX   = 0f
                                }
                            )
                        }
                ) {
                    // Thumbnail image
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(144, 144)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Image ${index + 1}",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )

                    // Order number badge (top-left)
                    Surface(
                        modifier = Modifier
                            .padding(start = 4.dp, top = 4.dp)
                            .size(18.dp)
                            .align(Alignment.TopStart),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text       = "${index + 1}",
                                fontSize   = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Remove (×) badge — spring-animates out while dragging.
                    // Uses graphicsLayer instead of AnimatedVisibility to avoid
                    // RowScope implicit-receiver ambiguity in nested Box lambdas.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp, top = 4.dp)
                            .size(18.dp)
                            .graphicsLayer {
                                alpha  = removeBadgeAlpha
                                scaleX = removeBadgeScale
                                scaleY = removeBadgeScale
                            }
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                            )
                            .clickable(enabled = !isDragging) { onRemove(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.Close,
                            contentDescription = "Remove",
                            tint               = MaterialTheme.colorScheme.onErrorContainer,
                            modifier           = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // ── Add more button ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(ShapeMedium)
                    .border(
                        width  = 1.5.dp,
                        color  = MaterialTheme.colorScheme.outlineVariant,
                        shape  = ShapeMedium
                    )
                    .clickable { onAddMore() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add images",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text  = "Add",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ── StitchControlsPanel ───────────────────────────────────────────────────────

/**
 * Three-row control panel below the thumbnail strip.
 *
 * Row 1 — Direction (Vertical/Horizontal) + Scale (Fill/Fit).
 * Row 2 — Gap spacing slider (0–40 dp).
 * Row 3 — Alignment toggle (only shown in vertical mode, spring in/out) +
 *          background colour picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StitchControlsPanel(
    orientation: StitchOrientation,
    spacingDp: Float,
    alignment: StitchAlignment,
    scaleMode: StitchScaleMode,
    bgColor: Color,
    onOrientationChange: (StitchOrientation) -> Unit,
    onSpacingChange: (Float) -> Unit,
    onAlignmentChange: (StitchAlignment) -> Unit,
    onScaleModeChange: (StitchScaleMode) -> Unit,
    onBgColorChange: (Color) -> Unit
) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Row 1: Direction + Scale ─────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Direction
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = "Direction",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = orientation == StitchOrientation.VERTICAL,
                            onClick  = { onOrientationChange(StitchOrientation.VERTICAL) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon     = {}
                        ) {
                            Icon(
                                Icons.Rounded.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Vertical", style = MaterialTheme.typography.labelSmall)
                        }
                        SegmentedButton(
                            selected = orientation == StitchOrientation.HORIZONTAL,
                            onClick  = { onOrientationChange(StitchOrientation.HORIZONTAL) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon     = {}
                        ) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Horizontal", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Scale mode
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = "Scale",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = scaleMode == StitchScaleMode.FILL,
                            onClick  = { onScaleModeChange(StitchScaleMode.FILL) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon     = {}
                        ) { Text("Fill", style = MaterialTheme.typography.labelSmall) }
                        SegmentedButton(
                            selected = scaleMode == StitchScaleMode.FIT,
                            onClick  = { onScaleModeChange(StitchScaleMode.FIT) },
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon     = {}
                        ) { Text("Fit", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            // ── Row 2: Gap spacing slider ────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text     = "Gap",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 28.dp)
                )
                Slider(
                    value         = spacingDp,
                    onValueChange = onSpacingChange,
                    valueRange    = 0f..40f,
                    modifier      = Modifier.weight(1f)
                )
                Text(
                    text     = "${spacingDp.roundToInt()}dp",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier  = Modifier.widthIn(min = 36.dp)
                )
            }

            // ── Row 3: Alignment (vertical only) + Background color ──────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Bottom
            ) {
                // Alignment toggle — spring-animates in/out when switching orientation
                AnimatedVisibility(
                    visible  = orientation == StitchOrientation.VERTICAL,
                    enter    = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                               scaleIn(spring(stiffness = Spring.StiffnessMedium), initialScale = 0.85f),
                    exit     = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                               scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.85f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column {
                        Text(
                            text     = "Align",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = alignment == StitchAlignment.START,
                                onClick  = { onAlignmentChange(StitchAlignment.START) },
                                shape    = SegmentedButtonDefaults.itemShape(0, 3),
                                icon     = {}
                            ) { Text("Left", style = MaterialTheme.typography.labelSmall) }
                            SegmentedButton(
                                selected = alignment == StitchAlignment.CENTER,
                                onClick  = { onAlignmentChange(StitchAlignment.CENTER) },
                                shape    = SegmentedButtonDefaults.itemShape(1, 3),
                                icon     = {}
                            ) { Text("Center", style = MaterialTheme.typography.labelSmall) }
                            SegmentedButton(
                                selected = alignment == StitchAlignment.END,
                                onClick  = { onAlignmentChange(StitchAlignment.END) },
                                shape    = SegmentedButtonDefaults.itemShape(2, 3),
                                icon     = {}
                            ) { Text("Right", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }

                // Background colour picker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text     = "Background",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    val presetColors = listOf(
                        Color.Black,
                        Color.White,
                        Color(0xFF1A1A2E),
                        Color(0xFF2D2D2D),
                        Color(0xFFF5F5F0),
                        Color(0xFFE8D5C4),
                        Color(0xFF0D1B2A),
                        Color(0xFF1B4332)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presetColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width  = if (bgColor == color) 2.5.dp else 1.dp,
                                        color  = if (bgColor == color)
                                                     MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.outlineVariant,
                                        shape  = CircleShape
                                    )
                                    .clickable { onBgColorChange(color) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── SaveOptionsSheet ──────────────────────────────────────────────────────────

/**
 * Bottom sheet presenting three save formats: JPEG, PNG, PDF.
 * Each format card shows a label, description, and estimated use-case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveOptionsSheet(
    onDismiss: () -> Unit,
    onSave: (format: String) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = androidx.compose.material3.SheetValue.Expanded)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text     = "Save Stitched Image",
                style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text  = "Choose a format to export your stitched image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SaveFormatCard(
                label       = "JPEG",
                description = "Best compatibility — perfect for sharing & posting",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick     = { onSave("JPEG") }
            )
            SaveFormatCard(
                label       = "PNG",
                description = "Lossless quality — best for screenshots & text",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick     = { onSave("PNG") }
            )
            SaveFormatCard(
                label       = "PDF",
                description = "Single-page PDF — ideal for documents & printing",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor   = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick     = { onSave("PDF") }
            )
        }
    }
}

@Composable
private fun SaveFormatCard(
    label: String,
    description: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape   = MaterialTheme.shapes.large,
        color   = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier  = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.SaveAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Render + Save pipeline ────────────────────────────────────────────────────

/**
 * Renders the final high-resolution stitched bitmap and writes it to
 * the MediaStore (JPEG/PNG → Pictures, PDF → Documents).
 *
 * Memory strategy:
 * - Each source image is decoded at the required slot size using
 *   [decodeSampledBitmapForStitch] (inSampleSize downsampling), so we
 *   never hold the full-resolution source in memory.
 * - Each decoded + scaled bitmap is recycled immediately after being
 *   drawn onto the canvas output.
 * - A try/finally block guarantees recycling even if an exception occurs.
 *
 * Output resolution:
 * - Vertical: target canvas width = 2160 px; each image is scaled to
 *   that width, heights computed proportionally.
 * - Horizontal: target canvas height = 2160 px; widths proportional.
 *
 * Must be called from a coroutine on [Dispatchers.IO].
 */
private suspend fun saveStitchedImage(
    context: Context,
    items: List<StitchItem>,
    orientation: StitchOrientation,
    spacingPx: Float,
    alignment: StitchAlignment,
    scaleMode: StitchScaleMode,
    bgColor: Color,
    format: String
) = withContext(Dispatchers.IO) {

    val TARGET = 2160 // shared dimension in pixels
    val gap    = spacingPx.roundToInt().coerceAtLeast(0)

    // ── Step 1: Decode and scale each bitmap to the target slot size ─────────
    val decoded = mutableListOf<Bitmap>()
    try {
        for (item in items) {
            val raw = decodeSampledBitmapForStitch(
                context   = context,
                uri       = item.uri,
                reqWidth  = if (orientation == StitchOrientation.VERTICAL) TARGET else TARGET * 4,
                reqHeight = if (orientation == StitchOrientation.VERTICAL) TARGET * 4 else TARGET
            ) ?: continue

            val scaled: Bitmap = if (orientation == StitchOrientation.VERTICAL) {
                // Scale so width == TARGET
                if (scaleMode == StitchScaleMode.FILL) {
                    val scaledH = (TARGET.toFloat() / raw.width * raw.height).roundToInt()
                        .coerceAtLeast(1)
                    Bitmap.createScaledBitmap(raw, TARGET, scaledH, true)
                } else {
                    // FIT: scale uniformly; image may be narrower than TARGET
                    val ratio = TARGET.toFloat() / raw.width.toFloat()
                    val scaledW = TARGET
                    val scaledH = (raw.height * ratio).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(raw, scaledW, scaledH, true)
                }
            } else {
                // Horizontal: scale so height == TARGET
                if (scaleMode == StitchScaleMode.FILL) {
                    val scaledW = (TARGET.toFloat() / raw.height * raw.width).roundToInt()
                        .coerceAtLeast(1)
                    Bitmap.createScaledBitmap(raw, scaledW, TARGET, true)
                } else {
                    val ratio = TARGET.toFloat() / raw.height.toFloat()
                    val scaledH = TARGET
                    val scaledW = (raw.width * ratio).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(raw, scaledW, scaledH, true)
                }
            }
            if (scaled !== raw) raw.recycle()
            decoded.add(scaled)
        }

        if (decoded.isEmpty()) throw Exception("No images could be decoded")

        // ── Step 2: Calculate canvas size ─────────────────────────────────────
        val canvasW: Int
        val canvasH: Int
        if (orientation == StitchOrientation.VERTICAL) {
            canvasW = decoded.maxOf { it.width }
            canvasH = decoded.sumOf { it.height } + gap * (decoded.size - 1)
        } else {
            canvasH = decoded.maxOf { it.height }
            canvasW = decoded.sumOf { it.width } + gap * (decoded.size - 1)
        }

        // ── Step 3: Composite onto canvas ─────────────────────────────────────
        val outputBmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas    = android.graphics.Canvas(outputBmp)
        canvas.drawColor(bgColor.toArgb())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        var cursor = 0
        for (bmp in decoded) {
            if (orientation == StitchOrientation.VERTICAL) {
                val x = when (alignment) {
                    StitchAlignment.START  -> 0f
                    StitchAlignment.CENTER -> (canvasW - bmp.width) / 2f
                    StitchAlignment.END    -> (canvasW - bmp.width).toFloat()
                }
                canvas.drawBitmap(bmp, x, cursor.toFloat(), paint)
                cursor += bmp.height + gap
            } else {
                val y = (canvasH - bmp.height) / 2f
                canvas.drawBitmap(bmp, cursor.toFloat(), y, paint)
                cursor += bmp.width + gap
            }
            bmp.recycle() // free source memory immediately after drawing
        }
        decoded.clear()

        // ── Step 4: Write to device storage ──────────────────────────────────
        val ts = System.currentTimeMillis()
        when (format) {
            "JPEG" -> {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "stitch_$ts.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE,    "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/PhotonGallery_Stitched")
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Failed to create MediaStore entry")
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    if (!outputBmp.compress(Bitmap.CompressFormat.JPEG, 95, stream))
                        throw Exception("JPEG compression failed")
                } ?: throw Exception("Failed to open output stream")
            }
            "PNG" -> {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "stitch_$ts.png")
                    put(MediaStore.MediaColumns.MIME_TYPE,    "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/PhotonGallery_Stitched")
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Failed to create MediaStore entry")
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    if (!outputBmp.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        throw Exception("PNG compression failed")
                } ?: throw Exception("Failed to open output stream")
            }
            "PDF" -> {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "stitch_$ts.pdf")
                    put(MediaStore.MediaColumns.MIME_TYPE,    "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOCUMENTS + "/PhotonGallery_Stitched")
                }
                val fileUri = context.contentResolver
                    .insert(MediaStore.Files.getContentUri("external"), values)
                    ?: throw Exception("Failed to create PDF MediaStore entry")
                val pdfDoc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(canvasW, canvasH, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                page.canvas.drawBitmap(outputBmp, 0f, 0f, null)
                pdfDoc.finishPage(page)
                context.contentResolver.openOutputStream(fileUri)?.use { pdfDoc.writeTo(it) }
                pdfDoc.close()
            }
        }
        outputBmp.recycle()

        // ── Step 5: Trigger media sync so the image appears in gallery ─────────
        WorkManager.getInstance(context).enqueueUniqueWork(
            "MediaSyncWorker",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MediaSyncWorker>().build()
        )

    } finally {
        // Safety net: recycle any bitmaps not yet recycled (e.g. if exception occurred mid-loop)
        decoded.forEach { if (!it.isRecycled) it.recycle() }
    }
}

// ── Bitmap decode helper ──────────────────────────────────────────────────────

/**
 * Decodes a bitmap from [uri] with downsampling ([inSampleSize]) so that the
 * resulting bitmap is at most [reqWidth] × [reqHeight]. Uses the same two-pass
 * BitmapFactory strategy as CollageScreen to minimise memory use.
 */
private fun decodeSampledBitmapForStitch(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

    var sample = 1
    if (opts.outHeight > reqHeight || opts.outWidth > reqWidth) {
        val halfH = opts.outHeight / 2
        val halfW = opts.outWidth  / 2
        while (halfH / sample >= reqHeight && halfW / sample >= reqWidth) sample *= 2
    }
    opts.inSampleSize      = sample
    opts.inJustDecodeBounds = false
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
} catch (e: Exception) {
    null
}
