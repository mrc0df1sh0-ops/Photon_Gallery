package com.inferno.gallery.ui.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws soft gradient fades at the top and/or bottom edges of a
 * scrollable container. The fade only appears when there is content
 * to scroll in that direction.
 *
 * Implementation uses [drawWithContent] which composites on the GPU
 * with zero allocations per frame — no bitmap creation, no shaders
 * rebuilt, just a static gradient drawn over the content.
 *
 * @param fadeLength Height of each fade gradient.
 * @param color The color to fade into (should match the screen background).
 */
fun Modifier.verticalFadingEdge(
    scrollState: LazyGridState,
    fadeLength: Dp = 16.dp,
    color: Color = Color.Black,
): Modifier = this.drawWithContent {
    drawContent()

    val fadePx = fadeLength.toPx()

    // Top fade — only when scrolled down
    if (scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 0) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color, Color.Transparent),
                startY = 0f,
                endY = fadePx,
            ),
            size = size.copy(height = fadePx),
        )
    }

    // Bottom fade — only when not at the end
    val layoutInfo = scrollState.layoutInfo
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
    val isAtBottom = lastVisible != null &&
        lastVisible.index == layoutInfo.totalItemsCount - 1 &&
        lastVisible.offset.y + lastVisible.size.height <= layoutInfo.viewportEndOffset

    if (!isAtBottom && layoutInfo.totalItemsCount > 0) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, color),
                startY = size.height - fadePx,
                endY = size.height,
            ),
            size = size.copy(height = fadePx),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fadePx),
        )
    }
}
