package com.inferno.gallery.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Photon Gallery motion tokens.
 *
 * Custom easing curves and spring specs that give the app an organic,
 * premium feel. Each curve is hand-tuned for its purpose — spatial
 * movement, opacity fades, scale changes, etc.
 */
object MotionTokens {

    // ── Easing curves ──────────────────────────────────────────────────

    /**
     * Primary spatial easing — used for slides, position changes, and
     * layout animations. The 1.03 end point produces a subtle overshoot
     * that makes motion feel lively rather than mechanical.
     */
    val EmphasizedEasing = CubicBezierEasing(0.48f, 0.19f, 0.05f, 1.03f)

    /**
     * Tuned for opacity transitions. Softer attack than [EmphasizedEasing]
     * so fades don't feel abrupt.
     */
    val FadeEasing = CubicBezierEasing(0.42f, 0.0f, 0.16f, 1.0f)

    /**
     * Smooth deceleration for scale/size changes. Gentle start with a
     * strong settle — elements don't "pop" or jank at the end.
     */
    val ScaleEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1.0f)


    // ── Spring specs ───────────────────────────────────────────────────

    /**
     * Bouncy spring for playful interactions — thumbnail press feedback,
     * selection animations, icon reactions. Low damping gives a visible
     * overshoot and settle.
     */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Snappy spring for UI chrome — toolbar reveals, dock animations,
     * chip toggles. High stiffness with no bounce for crisp transitions.
     */
    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /**
     * Gentle spring for content-area transitions — list/grid morphs,
     * content crossfades, layout size changes. Smooth and unobtrusive.
     */
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )
}
