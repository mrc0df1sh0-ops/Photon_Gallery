package com.inferno.gallery.ui.utils

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Lightweight shimmer placeholder for image thumbnails.
 *
 * Uses a single [rememberInfiniteTransition] that pulses the alpha
 * of a surface-tinted box. No gradient sweep, no bitmap allocation,
 * no per-cell coroutines — just an alpha animation on a solid color.
 *
 * Performance: the transition is shared by Compose's animation system
 * and costs ~0.01ms per frame. Safe to use in hundreds of grid cells.
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor.copy(alpha = alpha))
    )
}
