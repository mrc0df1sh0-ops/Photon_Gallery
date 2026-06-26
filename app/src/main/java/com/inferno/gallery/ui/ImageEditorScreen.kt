package com.inferno.gallery.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inferno.gallery.ui.utils.tick
import com.inferno.gallery.ui.utils.thud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ── Data Models ──────────────────────────────────────────────────────────────

enum class EditorTool(val label: String, val icon: ImageVector) {
    TUNE("Tune", Icons.Rounded.Tune),
    CROP("Crop", Icons.Rounded.Crop),
    FILTERS("Filters", Icons.Rounded.Palette),
    VIGNETTE("Vignette", Icons.Rounded.Vignette)
}

data class TuneValues(
    val brightness: Float = 0f,    // -100..100
    val contrast: Float = 0f,      // -100..100
    val saturation: Float = 0f,    // -100..100
    val warmth: Float = 0f,        // -100..100
    val shadows: Float = 0f,       // -100..100
    val highlights: Float = 0f     // -100..100
)

data class CropState(
    val rotation: Int = 0,           // 0, 90, 180, 270
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val cropRect: Rect? = null,      // Normalized 0..1
    val aspectLabel: String = "Free"
)

data class FilterPreset(
    val name: String,
    val matrix: ColorMatrix
)

data class EditorState(
    val tune: TuneValues = TuneValues(),
    val crop: CropState = CropState(),
    val filterIndex: Int = 0,
    val vignetteStrength: Float = 0f
)

// ── Filter Presets ───────────────────────────────────────────────────────────

private fun buildFilterPresets(): List<FilterPreset> {
    val presets = mutableListOf<FilterPreset>()

    // Original
    presets.add(FilterPreset("Original", ColorMatrix()))

    // Vivid
    presets.add(FilterPreset("Vivid", ColorMatrix().apply {
        val s = ColorMatrix()
        s.setSaturation(1.5f)
        postConcat(s)
        val c = buildContrastMatrix(0.15f)
        postConcat(c)
    }))

    // Warm
    presets.add(FilterPreset("Warm", ColorMatrix(floatArrayOf(
        1.2f, 0f, 0f, 0f, 10f,
        0f, 1.05f, 0f, 0f, 5f,
        0f, 0f, 0.9f, 0f, -10f,
        0f, 0f, 0f, 1f, 0f
    ))))

    // Cool
    presets.add(FilterPreset("Cool", ColorMatrix(floatArrayOf(
        0.9f, 0f, 0f, 0f, -10f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 1.2f, 0f, 15f,
        0f, 0f, 0f, 1f, 0f
    ))))

    // B&W
    presets.add(FilterPreset("B&W", ColorMatrix().apply { setSaturation(0f) }))

    // Sepia
    presets.add(FilterPreset("Sepia", ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))))

    // Vintage
    presets.add(FilterPreset("Vintage", ColorMatrix(floatArrayOf(
        0.9f, 0.5f, 0.1f, 0f, 20f,
        0.3f, 0.8f, 0.1f, 0f, 10f,
        0.2f, 0.3f, 0.5f, 0f, 30f,
        0f, 0f, 0f, 1f, 0f
    ))))

    // Dramatic
    presets.add(FilterPreset("Dramatic", ColorMatrix().apply {
        setSaturation(0.6f)
        val c = buildContrastMatrix(0.3f)
        postConcat(c)
    }))

    // Fade
    presets.add(FilterPreset("Fade", ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 30f,
        0f, 1f, 0f, 0f, 30f,
        0f, 0f, 1f, 0f, 30f,
        0f, 0f, 0f, 0.9f, 0f
    )).apply {
        val s = ColorMatrix()
        s.setSaturation(0.7f)
        postConcat(s)
    }))

    // Film
    presets.add(FilterPreset("Film", ColorMatrix(floatArrayOf(
        1.1f, 0.05f, 0.0f, 0f, 5f,
        0.0f, 1.05f, 0.05f, 0f, 0f,
        0.05f, 0.0f, 1.1f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )).apply {
        val c = buildContrastMatrix(0.1f)
        postConcat(c)
    }))

    return presets
}

private fun buildContrastMatrix(contrast: Float): ColorMatrix {
    val scale = 1f + contrast
    val offset = (-0.5f * scale + 0.5f) * 255f
    return ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, offset,
        0f, scale, 0f, 0f, offset,
        0f, 0f, scale, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    ))
}

// ── Combined ColorMatrix builder ─────────────────────────────────────────────

private fun buildCombinedMatrix(tune: TuneValues, filterMatrix: ColorMatrix): ColorMatrix {
    val result = ColorMatrix()

    // 1. Brightness
    if (tune.brightness != 0f) {
        val b = tune.brightness * 1.5f
        result.postConcat(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, b,
            0f, 1f, 0f, 0f, b,
            0f, 0f, 1f, 0f, b,
            0f, 0f, 0f, 1f, 0f
        )))
    }

    // 2. Contrast
    if (tune.contrast != 0f) {
        val c = tune.contrast / 100f
        result.postConcat(buildContrastMatrix(c))
    }

    // 3. Saturation
    if (tune.saturation != 0f) {
        val s = ColorMatrix()
        s.setSaturation(1f + tune.saturation / 100f)
        result.postConcat(s)
    }

    // 4. Warmth (shift R/B)
    if (tune.warmth != 0f) {
        val w = tune.warmth * 0.8f
        result.postConcat(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, w,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, -w,
            0f, 0f, 0f, 1f, 0f
        )))
    }

    // 5. Shadows (lift dark tones)
    if (tune.shadows != 0f) {
        val sh = tune.shadows * 0.5f
        result.postConcat(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, sh.coerceAtLeast(0f),
            0f, 1f, 0f, 0f, sh.coerceAtLeast(0f),
            0f, 0f, 1f, 0f, sh.coerceAtLeast(0f),
            0f, 0f, 0f, 1f, 0f
        )))
    }

    // 6. Highlights
    if (tune.highlights != 0f) {
        val hl = tune.highlights * 0.3f
        val scale = 1f + hl / 255f
        result.postConcat(ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, 0f,
            0f, scale, 0f, 0f, 0f,
            0f, 0f, scale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
    }

    // 7. Filter preset on top
    result.postConcat(filterMatrix)

    return result
}

// ── Main Editor Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageEditorScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    // ── Load original bitmap ─────────────────────────────────────────────
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUri) {
        originalBitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // ── Editor state + undo/redo ─────────────────────────────────────────
    var currentState by remember { mutableStateOf(EditorState()) }
    val undoStack = remember { mutableStateListOf<EditorState>() }
    val redoStack = remember { mutableStateListOf<EditorState>() }

    fun pushState(newState: EditorState) {
        undoStack.add(currentState)
        redoStack.clear()
        currentState = newState
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(currentState)
            currentState = undoStack.removeLast()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(currentState)
            currentState = redoStack.removeLast()
        }
    }

    // ── Tool state ───────────────────────────────────────────────────────
    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveOptions by remember { mutableStateOf(false) }

    // ── Filter presets ───────────────────────────────────────────────────
    val filterPresets = remember { buildFilterPresets() }

    // ── Combined color matrix ────────────────────────────────────────────
    val combinedMatrix = remember(currentState.tune, currentState.filterIndex) {
        val filterMatrix = filterPresets.getOrNull(currentState.filterIndex)?.matrix ?: ColorMatrix()
        buildCombinedMatrix(currentState.tune, filterMatrix)
    }

    val combinedColorFilter = remember(combinedMatrix) {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(combinedMatrix.array)
        )
    }

    // ── Save function ────────────────────────────────────────────────────
    fun saveImage(overwrite: Boolean) {
        val bmp = originalBitmap ?: return
        isSaving = true
        coroutineScope.launch(Dispatchers.Default) {
            try {
                // Apply crop
                val crop = currentState.crop
                var workBitmap = bmp

                // Apply rotation/flip
                if (crop.rotation != 0 || crop.flipH || crop.flipV) {
                    val matrix = android.graphics.Matrix()
                    if (crop.flipH) matrix.preScale(-1f, 1f)
                    if (crop.flipV) matrix.preScale(1f, -1f)
                    if (crop.rotation != 0) matrix.postRotate(crop.rotation.toFloat())
                    workBitmap = Bitmap.createBitmap(workBitmap, 0, 0, workBitmap.width, workBitmap.height, matrix, true)
                }

                // Apply crop rect
                val cropRect = crop.cropRect
                if (cropRect != null) {
                    val x = (cropRect.left * workBitmap.width).toInt().coerceIn(0, workBitmap.width - 1)
                    val y = (cropRect.top * workBitmap.height).toInt().coerceIn(0, workBitmap.height - 1)
                    val w = ((cropRect.right - cropRect.left) * workBitmap.width).toInt().coerceIn(1, workBitmap.width - x)
                    val h = ((cropRect.bottom - cropRect.top) * workBitmap.height).toInt().coerceIn(1, workBitmap.height - y)
                    workBitmap = Bitmap.createBitmap(workBitmap, x, y, w, h)
                }

                // Apply color matrix (tune + filter)
                val resultBitmap = Bitmap.createBitmap(workBitmap.width, workBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resultBitmap)
                val paint = Paint().apply {
                    colorFilter = ColorMatrixColorFilter(combinedMatrix)
                }
                canvas.drawBitmap(workBitmap, 0f, 0f, paint)

                // Apply vignette
                if (currentState.vignetteStrength > 0f) {
                    val cx = resultBitmap.width / 2f
                    val cy = resultBitmap.height / 2f
                    val radius = kotlin.math.max(cx, cy) * 1.2f
                    val alpha = (currentState.vignetteStrength / 100f * 200).toInt().coerceIn(0, 200)
                    val vignettePaint = Paint().apply {
                        shader = RadialGradient(
                            cx, cy, radius,
                            intArrayOf(0x00000000, 0x00000000, android.graphics.Color.argb(alpha, 0, 0, 0)),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, resultBitmap.width.toFloat(), resultBitmap.height.toFloat(), vignettePaint)
                }

                // Write to MediaStore
                withContext(Dispatchers.IO) {
                    if (overwrite) {
                        // Overwrite original
                        context.contentResolver.openOutputStream(imageUri, "w")?.use { out ->
                            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                    } else {
                        // Save as new copy
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "Photon_Edit_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photon Gallery")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val newUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (newUri != null) {
                            context.contentResolver.openOutputStream(newUri)?.use { out ->
                                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            context.contentResolver.update(newUri, values, null, null)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    view.thud()
                    Toast.makeText(context, if (overwrite) "Saved" else "Saved as copy", Toast.LENGTH_SHORT).show()
                    isSaving = false
                    onSaved()
                    onBack()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                    isSaving = false
                }
            }
        }
    }

    BackHandler {
        if (activeTool != null) {
            activeTool = null
        } else {
            onBack()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────

    if (originalBitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            com.inferno.gallery.ui.components.WavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
        return
    }

    val bmp = originalBitmap!!
    val imageAspect = bmp.width.toFloat() / bmp.height.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Image Preview Area ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 72.dp), // space for top toolbar
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

                if (activeTool == EditorTool.CROP) {
                    // Crop mode: show image with crop overlay
                    CropPreview(
                        imageBitmap = imageBitmap,
                        imageAspect = imageAspect,
                        cropState = currentState.crop,
                        colorFilter = combinedColorFilter,
                        onCropRectChanged = { newRect ->
                            currentState = currentState.copy(crop = currentState.crop.copy(cropRect = newRect))
                        }
                    )
                } else {
                    // Normal mode: show image with color filter + vignette overlay
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            bitmap = imageBitmap,
                            contentDescription = "Preview",
                            contentScale = ContentScale.Fit,
                            colorFilter = combinedColorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = currentState.crop.rotation.toFloat()
                                    scaleX = if (currentState.crop.flipH) -1f else 1f
                                    scaleY = if (currentState.crop.flipV) -1f else 1f
                                }
                                .then(
                                    if (currentState.vignetteStrength > 0f) {
                                        Modifier.drawWithContent {
                                            drawContent()
                                            val alpha = currentState.vignetteStrength / 100f * 0.8f
                                            drawRect(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = alpha)
                                                    ),
                                                    center = Offset(size.width / 2, size.height / 2),
                                                    radius = maxOf(size.width, size.height) * 0.6f
                                                )
                                            )
                                        }
                                    } else Modifier
                                )
                        )
                    }
                }

                // Saving overlay
                if (isSaving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.inferno.gallery.ui.components.WavyProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // ── Bottom Panel ─────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    // Active tool panel
                    AnimatedContent(
                        targetState = activeTool,
                        transitionSpec = {
                            (slideInVertically { it / 3 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 3 } + fadeOut())
                        },
                        label = "toolPanel"
                    ) { tool ->
                        when (tool) {
                            EditorTool.TUNE -> TunePanel(
                                values = currentState.tune,
                                onValuesChanged = { newValues ->
                                    pushState(currentState.copy(tune = newValues))
                                }
                            )
                            EditorTool.CROP -> CropToolPanel(
                                cropState = currentState.crop,
                                onCropStateChanged = { newCrop ->
                                    pushState(currentState.copy(crop = newCrop))
                                }
                            )
                            EditorTool.FILTERS -> FiltersPanel(
                                presets = filterPresets,
                                selectedIndex = currentState.filterIndex,
                                originalBitmap = bmp,
                                onFilterSelected = { index ->
                                    pushState(currentState.copy(filterIndex = index))
                                }
                            )
                            EditorTool.VIGNETTE -> VignettePanel(
                                strength = currentState.vignetteStrength,
                                onStrengthChanged = { s ->
                                    pushState(currentState.copy(vignetteStrength = s))
                                }
                            )
                            null -> Spacer(modifier = Modifier.height(0.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Tool Selector Row ─────────────────────────────────
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(EditorTool.entries.toList()) { _, tool ->
                            val isSelected = activeTool == tool
                            EditorToolChip(
                                label = tool.label,
                                icon = tool.icon,
                                isSelected = isSelected,
                                onClick = {
                                    view.tick()
                                    activeTool = if (activeTool == tool) null else tool
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Top Floating Toolbar ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = androidx.compose.material3.FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier.height(48.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }

            HorizontalFloatingToolbar(
                expanded = true,
                colors = androidx.compose.material3.FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier.height(48.dp)
            ) {
                IconButton(
                    onClick = { undo() },
                    enabled = undoStack.isNotEmpty()
                ) {
                    Icon(
                        @Suppress("DEPRECATION") Icons.Rounded.Undo, contentDescription = "Undo",
                        tint = if (undoStack.isNotEmpty()) androidx.compose.material3.LocalContentColor.current
                        else androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.3f)
                    )
                }
                IconButton(
                    onClick = { redo() },
                    enabled = redoStack.isNotEmpty()
                ) {
                    Icon(
                        @Suppress("DEPRECATION") Icons.Rounded.Redo, contentDescription = "Redo",
                        tint = if (redoStack.isNotEmpty()) androidx.compose.material3.LocalContentColor.current
                        else androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.3f)
                    )
                }

                Box {
                    IconButton(onClick = {
                        showSaveOptions = !showSaveOptions
                    }) {
                        Icon(Icons.Rounded.Check, contentDescription = "Save")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showSaveOptions,
                        onDismissRequest = { showSaveOptions = false },
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Save") },
                            leadingIcon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                            onClick = {
                                showSaveOptions = false
                                saveImage(overwrite = true)
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Save as copy") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            onClick = {
                                showSaveOptions = false
                                saveImage(overwrite = false)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Tool Chip ────────────────────────────────────────────────────────────────

@Composable
private fun EditorToolChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHighest

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Tune Panel ───────────────────────────────────────────────────────────────

@Composable
private fun TunePanel(
    values: TuneValues,
    onValuesChanged: (TuneValues) -> Unit
) {
    data class TuneParam(val label: String, val icon: ImageVector, val value: Float, val setter: (Float) -> TuneValues)
    val params = listOf(
        TuneParam("Brightness", Icons.Rounded.LightMode, values.brightness) { values.copy(brightness = it) },
        TuneParam("Contrast", Icons.Rounded.Contrast, values.contrast) { values.copy(contrast = it) },
        TuneParam("Saturation", Icons.Rounded.Palette, values.saturation) { values.copy(saturation = it) },
        TuneParam("Warmth", Icons.Rounded.WbSunny, values.warmth) { values.copy(warmth = it) },
    )

    var selectedParam by remember { mutableIntStateOf(0) }
    val param = params[selectedParam]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Parameter selector chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(params) { index, p ->
                val isSelected = selectedParam == index
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { selectedParam = index }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(p.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(p.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        if (p.value != 0f) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${if (p.value > 0) "+" else ""}${p.value.roundToInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${if (param.value > 0) "+" else ""}${param.value.roundToInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )
            Slider(
                value = param.value,
                onValueChange = { onValuesChanged(param.setter(it)) },
                valueRange = -100f..100f,
                modifier = Modifier.weight(1f)
            )
            // Reset button
            IconButton(
                onClick = { onValuesChanged(param.setter(0f)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh, contentDescription = "Reset",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Crop Tool Panel ──────────────────────────────────────────────────────────

@Composable
private fun CropToolPanel(
    cropState: CropState,
    onCropStateChanged: (CropState) -> Unit
) {
    val view = LocalView.current
    val aspects = listOf("Free", "1:1", "4:3", "3:4", "16:9", "9:16")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Aspect ratio row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(aspects) { _, label ->
                val isSelected = cropState.aspectLabel == label
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            view.tick()
                            onCropStateChanged(cropState.copy(aspectLabel = label))
                        }
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    view.tick()
                    onCropStateChanged(cropState.copy(rotation = (cropState.rotation + 90) % 360))
                }) {
                    Icon(@Suppress("DEPRECATION") Icons.Rounded.RotateRight, contentDescription = "Rotate")
                }
                Text("Rotate", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    view.tick()
                    onCropStateChanged(cropState.copy(flipH = !cropState.flipH))
                }) {
                    Icon(Icons.Rounded.Flip, contentDescription = "Flip H")
                }
                Text("Flip H", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    view.tick()
                    onCropStateChanged(cropState.copy(flipV = !cropState.flipV))
                }) {
                    Icon(
                        Icons.Rounded.Flip,
                        contentDescription = "Flip V",
                        modifier = Modifier.graphicsLayer { rotationZ = 90f }
                    )
                }
                Text("Flip V", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    view.tick()
                    onCropStateChanged(CropState())
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset")
                }
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Crop Preview ─────────────────────────────────────────────────────────────

@Composable
private fun CropPreview(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    imageAspect: Float,
    cropState: CropState,
    colorFilter: androidx.compose.ui.graphics.ColorFilter?,
    onCropRectChanged: (Rect) -> Unit
) {
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var cropRect by remember(cropState.cropRect) {
        mutableStateOf(cropState.cropRect ?: Rect(0f, 0f, 1f, 1f))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        val (imgW, imgH) = if (imageAspect > maxW / maxH) {
            maxW to maxW / imageAspect
        } else {
            maxH * imageAspect to maxH
        }

        val imgLeft = (maxW - imgW) / 2f
        val imgTop = (maxH - imgH) / 2f

        LaunchedEffect(imgW, imgH, imgLeft, imgTop) {
            imageRect = Rect(imgLeft, imgTop, imgLeft + imgW, imgTop + imgH)
        }

        val density = androidx.compose.ui.platform.LocalDensity.current.density

        // Draw image
        androidx.compose.foundation.Image(
            bitmap = imageBitmap,
            contentDescription = "Crop preview",
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = Modifier
                .size(
                    width = (imgW / density).dp,
                    height = (imgH / density).dp
                )
                .graphicsLayer {
                    rotationZ = cropState.rotation.toFloat()
                    scaleX = if (cropState.flipH) -1f else 1f
                    scaleY = if (cropState.flipV) -1f else 1f
                }
        )

        // Crop overlay
        if (imageRect != Rect.Zero) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = imageRect.left + cropRect.left * imageRect.width
                val top = imageRect.top + cropRect.top * imageRect.height
                val right = imageRect.left + cropRect.right * imageRect.width
                val bottom = imageRect.top + cropRect.bottom * imageRect.height

                // Dim area outside crop
                drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(size.width, top))
                drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, bottom), Size(size.width, size.height - bottom))
                drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, top), Size(left, bottom - top))
                drawRect(Color.Black.copy(alpha = 0.5f), Offset(right, top), Size(size.width - right, bottom - top))

                // Crop border
                drawRect(
                    Color.White,
                    Offset(left, top),
                    Size(right - left, bottom - top),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                )

                // Rule of thirds lines
                val thirdW = (right - left) / 3f
                val thirdH = (bottom - top) / 3f
                for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(left + thirdW * i, top), Offset(left + thirdW * i, bottom), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(left, top + thirdH * i), Offset(right, top + thirdH * i), 1.dp.toPx())
                }

                // Corner handles
                val handleLen = 24.dp.toPx()
                val handleWidth = 3.dp.toPx()
                val corners = listOf(
                    Offset(left, top), Offset(right, top),
                    Offset(left, bottom), Offset(right, bottom)
                )
                corners.forEachIndexed { idx, corner ->
                    val hDir = if (idx % 2 == 0) 1f else -1f
                    val vDir = if (idx < 2) 1f else -1f
                    drawLine(Color.White, corner, Offset(corner.x + handleLen * hDir, corner.y), handleWidth)
                    drawLine(Color.White, corner, Offset(corner.x, corner.y + handleLen * vDir), handleWidth)
                }
            }

            // Drag to move crop rect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageRect) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / imageRect.width
                            val dy = dragAmount.y / imageRect.height
                            val newRect = Rect(
                                left = (cropRect.left + dx).coerceIn(0f, cropRect.right - 0.1f),
                                top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - 0.1f),
                                right = (cropRect.right + dx).coerceIn(cropRect.left + 0.1f, 1f),
                                bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + 0.1f, 1f)
                            )
                            // Only move if the entire rect stays in bounds
                            if (newRect.left >= 0f && newRect.top >= 0f && newRect.right <= 1f && newRect.bottom <= 1f) {
                                cropRect = newRect
                                onCropRectChanged(newRect)
                            }
                        }
                    }
            )
        }
    }
}

// ── Filters Panel ────────────────────────────────────────────────────────────

@Composable
private fun FiltersPanel(
    presets: List<FilterPreset>,
    selectedIndex: Int,
    originalBitmap: Bitmap,
    onFilterSelected: (Int) -> Unit
) {
    val view = LocalView.current
    // Generate small thumbnails for each filter
    val thumbnails = remember(originalBitmap) {
        val thumbSize = 120
        val aspect = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val tw = if (aspect >= 1f) thumbSize else (thumbSize * aspect).toInt()
        val th = if (aspect >= 1f) (thumbSize / aspect).toInt() else thumbSize
        val smallBmp = Bitmap.createScaledBitmap(originalBitmap, tw, th, true)

        presets.map { preset ->
            val result = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(preset.matrix)
            }
            canvas.drawBitmap(smallBmp, 0f, 0f, paint)
            result.asImageBitmap()
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(presets) { index, preset ->
            val isSelected = selectedIndex == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    view.tick()
                    onFilterSelected(index)
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isSelected) 2.5.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    thumbnails.getOrNull(index)?.let { thumb ->
                        androidx.compose.foundation.Image(
                            bitmap = thumb,
                            contentDescription = preset.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    preset.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Vignette Panel ───────────────────────────────────────────────────────────

@Composable
private fun VignettePanel(
    strength: Float,
    onStrengthChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Vignette,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Vignette",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${strength.roundToInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = strength,
            onValueChange = onStrengthChanged,
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
