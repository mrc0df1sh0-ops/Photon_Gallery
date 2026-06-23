package com.inferno.gallery.ui.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

/**
 * Photon Gallery haptic feedback utilities.
 *
 * Uses Android View-level haptic constants for reliable tactile
 * feedback across devices. Compose's [HapticFeedbackType.TextHandleMove]
 * is imperceptible on many phones, so we bypass it.
 */

// ── View-level haptic helpers ───────────────────────────────────────

/** Light tick — perceptible click for regular taps, toggles, small actions. */
fun View.tick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** Firm thud — strong feedback for long-press, destructive actions. */
fun View.thud() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

// ── Compose-level wrappers (for use where View isn't readily available) ──

/** Light tick via Compose [HapticFeedback] — falls back to LongPress-lite. */
fun HapticFeedback.tick() =
    performHapticFeedback(HapticFeedbackType.LongPress)

/** Firm thud via Compose [HapticFeedback]. */
fun HapticFeedback.thud() =
    performHapticFeedback(HapticFeedbackType.LongPress)

// ── Haptic click modifiers ──────────────────────────────────────────

/**
 * Drop-in replacement for [Modifier.clickable] that triggers a light
 * haptic tick on every tap via the View system.
 */
fun Modifier.haptickClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    this.clickable(enabled = enabled) {
        view.tick()
        onClick()
    }
}

/**
 * Drop-in replacement for [Modifier.combinedClickable] with haptic
 * feedback on both tap (light tick) and long-press (firm thud).
 */
fun Modifier.haptickCombinedClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    this.combinedClickable(
        enabled = enabled,
        onLongClick = if (onLongClick != null) {
            {
                view.thud()
                onLongClick()
            }
        } else null,
        onClick = {
            view.tick()
            onClick()
        }
    )
}

