package com.laterbox.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object LaterboxMotion {

    // Durations
    const val durationShort: Int = 120
    const val durationMedium: Int = 220
    const val durationLong: Int = 320

    // Easing Curves
    val standardEasing: Easing = FastOutSlowInEasing
    val decelerateEasing: Easing = LinearOutSlowInEasing
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    fun <T> tweenSpec(
        durationMillis: Int = durationMedium,
        easing: Easing = standardEasing,
        reducedMotion: Boolean = false
    ): TweenSpec<T> {
        val effectiveDuration = if (reducedMotion) 0 else durationMillis
        return tween(durationMillis = effectiveDuration, easing = easing)
    }

    fun <T> springSpec(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessMediumLow,
        reducedMotion: Boolean = false
    ): SpringSpec<T> {
        return if (reducedMotion) {
            spring(dampingRatio = dampingRatio, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = dampingRatio, stiffness = stiffness)
        }
    }
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0.0f
        } catch (_: Exception) {
            false
        }
    }
}
