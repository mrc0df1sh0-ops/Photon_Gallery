package com.inferno.gallery.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * M3 Expressive Shape-Morphing Loading Indicator.
 *
 * A filled shape that continuously morphs between organic forms
 * (pentagon → blob → scallop → soft diamond) while rotating.
 *
 * Two variants:
 * - **Standalone** (`contained = false`): Just the morphing shape.
 * - **Contained** (`contained = true`): Morphing shape inside a circular track.
 *
 * @param modifier Modifier for sizing (default 48.dp).
 * @param color Fill color of the morphing shape.
 * @param trackColor Color of the circular container track (only when contained = true).
 * @param contained Whether to draw a circular track behind the shape.
 */
@Composable
fun ShapeMorphLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    contained: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shapeMorph")

    // ── Rotation: continuous 360° loop ──
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // ── Shape morph progress: cycles 0→1 over full morph duration ──
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphProgress"
    )

    // ── Scale pulse: subtle breathing effect ──
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scalePulse"
    )

    // Pre-compute shape definitions (4 shapes to morph between)
    val shapes = remember { buildShapeDefinitions() }

    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseRadius = (minOf(size.width, size.height) / 2f) * scalePulse

            // ── Contained: draw circular track ──
            if (contained) {
                val trackRadius = minOf(size.width, size.height) / 2f
                drawCircle(
                    color = trackColor,
                    radius = trackRadius,
                    style = Stroke(width = trackRadius * 0.14f)
                )
            }

            // ── Determine which two shapes to interpolate ──
            val totalShapes = shapes.size
            val cyclePos = morphProgress * totalShapes
            val fromIndex = cyclePos.toInt() % totalShapes
            val toIndex = (fromIndex + 1) % totalShapes
            val t = cyclePos - cyclePos.toInt() // 0..1 between the two shapes

            // Smooth easing for morph interpolation
            val easedT = smoothStep(t)

            val fromShape = shapes[fromIndex]
            val toShape = shapes[toIndex]

            // ── Build morphed path ──
            val shapeRadius = if (contained) baseRadius * 0.52f else baseRadius * 0.72f
            val path = Path()
            val steps = 120

            rotate(rotation, pivot = center) {
                for (i in 0..steps) {
                    val angle = (i.toFloat() / steps) * 2f * PI.toFloat()
                    val r1 = fromShape.radiusAt(angle) * shapeRadius
                    val r2 = toShape.radiusAt(angle) * shapeRadius
                    val r = r1 + (r2 - r1) * easedT

                    val x = cx + r * cos(angle)
                    val y = cy + r * sin(angle)

                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                drawPath(path = path, color = color, style = Fill)
            }
        }
    }
}

/**
 * Small variant for inline contexts.
 */
@Composable
fun ShapeMorphLoadingIndicatorSmall(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    ShapeMorphLoadingIndicator(
        modifier = modifier.size(24.dp),
        color = color,
        contained = false
    )
}

// ── Shape Definitions ──

/**
 * A polar shape defined as r(θ). Returns a normalized radius (around 1.0)
 * for any angle θ in radians.
 */
private class PolarShape(
    /** Number of symmetry folds */
    val folds: Int,
    /** Amplitude of the radial variation (0 = circle) */
    val amplitude: Float,
    /** Phase offset in radians */
    val phase: Float = 0f,
    /** Second harmonic amplitude for more complex shapes */
    val harmonic2Amp: Float = 0f,
    /** Second harmonic folds */
    val harmonic2Folds: Int = 0
) {
    fun radiusAt(angle: Float): Float {
        var r = 1f + amplitude * cos(folds * angle + phase)
        if (harmonic2Amp != 0f) {
            r += harmonic2Amp * cos(harmonic2Folds * angle + phase * 0.7f)
        }
        return r
    }
}

/**
 * Build the 4 target shapes that the indicator morphs between.
 * Each is inspired by the M3 Expressive loading indicator reference:
 * pentagon, organic blob, scallop/flower, soft diamond.
 */
private fun buildShapeDefinitions(): List<PolarShape> = listOf(
    // Shape 1: Pentagon — 5-fold symmetry, subtle amplitude
    PolarShape(folds = 5, amplitude = 0.14f, phase = 0f),

    // Shape 2: Organic blob — 3-fold asymmetry, larger amplitude
    PolarShape(
        folds = 3, amplitude = 0.18f, phase = 0.5f,
        harmonic2Amp = 0.06f, harmonic2Folds = 5
    ),

    // Shape 3: Scallop/flower — 8-fold, concave edges
    PolarShape(folds = 8, amplitude = -0.10f, phase = 0.3f),

    // Shape 4: Soft diamond — 4-fold, moderate amplitude
    PolarShape(
        folds = 4, amplitude = 0.16f, phase = 0.8f,
        harmonic2Amp = 0.04f, harmonic2Folds = 8
    )
)

/** Hermite smooth-step for easing: 3t² − 2t³ */
private fun smoothStep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}
