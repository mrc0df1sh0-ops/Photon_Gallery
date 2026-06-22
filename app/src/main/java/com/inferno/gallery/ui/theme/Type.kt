@file:OptIn(ExperimentalTextApi::class)

package com.inferno.gallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.inferno.gallery.R

// ─────────────────────────────────────────────────────────────────────────────
//  Variable Font: Google Sans Flex
//
//  Google Sans Flex exposes several variation axes that we leverage for the
//  M3 Expressive Emphasized Typography system:
//
//    • wght  (Weight)  — 100..900    — controls stroke thickness
//    • wdth  (Width)   — 25..151     — controls horizontal proportion
//    • opsz  (Optical Size) — 8..144 — optimises glyph shapes for size
//
//  By dynamically adjusting these axes per type-scale role we create an
//  Emphasized hierarchy: display/headline roles use wider, heavier settings
//  while body/label roles stay narrow and light, producing a clear visual
//  contrast without changing the font family.
// ─────────────────────────────────────────────────────────────────────────────

// ── Display / Headline — expanded width, heavy weight ──────────────────────
private fun displayFontFamily(weight: Int = 700, width: Float = 120f, opticalSize: Float = 48f) =
    FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    )

// ── Title — semi-expanded width, medium-bold weight ────────────────────────
private fun titleFontFamily(weight: Int = 600, width: Float = 110f, opticalSize: Float = 24f) =
    FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    )

// ── Body — normal width, regular weight ────────────────────────────────────
private fun bodyFontFamily(weight: Int = 400, width: Float = 100f, opticalSize: Float = 14f) =
    FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    )

// ── Label — slightly condensed width, medium weight ────────────────────────
private fun labelFontFamily(weight: Int = 500, width: Float = 95f, opticalSize: Float = 11f) =
    FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    )

// ─────────────────────────────────────────────────────────────────────────────
//  M3 Expressive Typography Scale — Emphasized
//
//  Toned down from the original scale: display & headline sizes reduced for
//  a less overwhelming page presence while retaining the variable-font
//  character (weight + width shifts). Body & label kept compact.
// ─────────────────────────────────────────────────────────────────────────────
val AppTypography = Typography(
    // ── Display ─────────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = displayFontFamily(weight = 650, width = 118f, opticalSize = 48f),
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily(weight = 600, width = 112f, opticalSize = 36f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily(weight = 550, width = 108f, opticalSize = 28f),
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),

    // ── Headline ────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = displayFontFamily(weight = 600, width = 112f, opticalSize = 28f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily(weight = 550, width = 108f, opticalSize = 24f),
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = titleFontFamily(weight = 550, width = 108f, opticalSize = 20f),
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),

    // ── Title ───────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = titleFontFamily(weight = 600, width = 112f, opticalSize = 22f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = titleFontFamily(weight = 500, width = 108f, opticalSize = 16f),
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = titleFontFamily(weight = 450, width = 105f, opticalSize = 14f),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),

    // ── Body ────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = bodyFontFamily(weight = 400, width = 98f, opticalSize = 16f),
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFontFamily(weight = 350, width = 95f, opticalSize = 14f),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFontFamily(weight = 350, width = 95f, opticalSize = 12f),
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),

    // ── Label ───────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = labelFontFamily(weight = 400, width = 98f, opticalSize = 14f),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = labelFontFamily(weight = 350, width = 95f, opticalSize = 12f),
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = labelFontFamily(weight = 350, width = 95f, opticalSize = 11f),
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)