package com.inferno.gallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Photon Gallery — Color Harmonization.
 *
 * Blends fixed accent colors (error red, success green, info blue)
 * toward the dynamic primary color so every accent feels cohesive
 * with the current wallpaper-based theme.
 *
 * Uses [ColorUtils.blendARGB] from AndroidX core for perceptual blending.
 */

// ── Core harmonize function ─────────────────────────────────────────

/**
 * Blends this color toward [target] by [fraction] (0.0–1.0).
 * At 0.0 the original color is returned unchanged.
 * At 1.0 the target color is returned.
 * Default fraction 0.18 gives a subtle tint without losing the
 * original color's identity.
 */
fun Color.harmonizeWith(target: Color, fraction: Float = 0.18f): Color {
    val blended = ColorUtils.blendARGB(this.toArgb(), target.toArgb(), fraction)
    return Color(blended)
}

// ── Harmonized palette ──────────────────────────────────────────────

/**
 * A set of accent colors harmonized with the current theme's primary.
 * Access via [LocalHarmonizedColors] or the [harmonizedColors] helper.
 */
@Immutable
data class HarmonizedColors(
    /** Error/destructive — red tinted toward primary. */
    val error: Color,
    /** Error container — lighter red tinted toward primary. */
    val errorContainer: Color,
    /** On-error container text. */
    val onErrorContainer: Color,
    /** Success/favorite — green tinted toward primary. */
    val success: Color,
    /** Success container — lighter green tinted toward primary. */
    val successContainer: Color,
    /** Info/cloud — blue tinted toward primary. */
    val info: Color,
    /** Info container — lighter blue tinted toward primary. */
    val infoContainer: Color,
)

val LocalHarmonizedColors = staticCompositionLocalOf {
    HarmonizedColors(
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        success = Color(0xFF2E7D32),
        successContainer = Color(0xFFC8E6C9),
        info = Color(0xFF1565C0),
        infoContainer = Color(0xFFBBDEFB),
    )
}

/**
 * Builds a [HarmonizedColors] palette based on the current
 * [MaterialTheme.colorScheme]'s primary color.
 *
 * Call this inside your theme wrapper to provide harmonized colors
 * to the entire composable tree.
 */
@Composable
fun harmonizedColors(): HarmonizedColors {
    val primary = MaterialTheme.colorScheme.primary

    // Start from the theme's own error tokens (already dynamic on 12+)
    val themeError = MaterialTheme.colorScheme.error
    val themeErrorContainer = MaterialTheme.colorScheme.errorContainer
    val themeOnErrorContainer = MaterialTheme.colorScheme.onErrorContainer

    return HarmonizedColors(
        error = themeError.harmonizeWith(primary, 0.12f),
        errorContainer = themeErrorContainer.harmonizeWith(primary, 0.10f),
        onErrorContainer = themeOnErrorContainer.harmonizeWith(primary, 0.08f),
        success = Color(0xFF2E7D32).harmonizeWith(primary, 0.20f),
        successContainer = Color(0xFFC8E6C9).harmonizeWith(primary, 0.15f),
        info = Color(0xFF1565C0).harmonizeWith(primary, 0.20f),
        infoContainer = Color(0xFFBBDEFB).harmonizeWith(primary, 0.15f),
    )
}
