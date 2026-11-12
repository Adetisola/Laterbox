package com.laterbox.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

interface LaterboxHaptics {
    fun tick()
    fun confirm()
    fun warning()
    fun dragHandle()
}

class LaterboxHapticsImpl(
    private val composeHaptics: HapticFeedback,
    private val view: View?
) : LaterboxHaptics {

    override fun tick() {
        try {
            if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Exception) {
            try {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    override fun confirm() {
        try {
            if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {
            try {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
        }
    }

    override fun warning() {
        try {
            if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {
            try {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
        }
    }

    override fun dragHandle() {
        try {
            if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
            } else {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Exception) {
            try {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun rememberLaterboxHaptics(): LaterboxHaptics {
    val composeHaptics = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(composeHaptics, view) {
        LaterboxHapticsImpl(composeHaptics, view)
    }
}
