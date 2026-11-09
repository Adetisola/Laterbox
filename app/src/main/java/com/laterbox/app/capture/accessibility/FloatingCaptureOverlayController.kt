package com.laterbox.app.capture.accessibility

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.laterbox.app.R
import com.laterbox.app.ui.components.LaterboxIcons

/**
 * Manages the floating contextual capture prompt overlaid via WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY.
 */
class FloatingCaptureOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 5000L
    }

    private var currentView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    val isShowing: Boolean
        get() = currentView != null

    fun showPrompt(
        domain: String,
        url: String,
        boundsInScreen: Rect?,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        dismissImmediately()

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        // Create Root Pill Layout
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(6f), dp(8f), dp(6f))
            elevation = dp(6f).toFloat()

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24f).toFloat()
                setColor(Color.parseColor("#22201D")) // Laterbox dark surface
                setStroke(dp(1f), Color.parseColor("#3D3934")) // Laterbox border subtle
            }
        }

        // Leading Globe / Link Icon
        val iconView = ImageView(context).apply {
            val size = dp(18f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(8f)
            }
            setImageResource(LaterboxIcons.Globe)
            setColorFilter(Color.parseColor("#E0A96D")) // Laterbox accent primary
        }
        rootLayout.addView(iconView)

        // Middle Text (Title + Domain)
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10f)
            }
        }

        val titleView = TextView(context).apply {
            text = "Save to Laterbox"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(Color.parseColor("#F5F2EB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        textContainer.addView(titleView)

        val domainView = TextView(context).apply {
            text = domain
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(Color.parseColor("#B3ABA0"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(domainView)

        rootLayout.addView(textContainer)

        // Action: "Save" Pill Button
        val saveButton = TextView(context).apply {
            text = "Save"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#1B1A18"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                setColor(Color.parseColor("#E0A96D")) // Accent primary
            }
            setOnClickListener {
                dismissImmediately()
                onSave()
            }
        }
        rootLayout.addView(saveButton)

        // Action: Close "✕" Button
        val closeButton = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#8A8276"))
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(4f), dp(6f), dp(4f))
            setOnClickListener {
                dismissImmediately()
                onDismiss()
            }
        }
        rootLayout.addView(closeButton)

        // Window Layout Parameters
        val estimatedPillWidth = dp(270f)
        val estimatedPillHeight = dp(52f)

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        // Calculate Position: Relative to selection bounds if reliable, safe bottom fallback otherwise
        val safeBottomMargin = dp(76f)
        val safeSideMargin = dp(16f)

        if (boundsInScreen != null &&
            !boundsInScreen.isEmpty &&
            boundsInScreen.width() > 0 &&
            boundsInScreen.width() < (screenWidth * 0.90f) &&
            boundsInScreen.height() > 0 &&
            boundsInScreen.height() < (screenHeight * 0.80f)
        ) {
            layoutParams.gravity = Gravity.TOP or Gravity.START

            // Center horizontally on selection bounds, clamped within safe margins
            val targetX = boundsInScreen.centerX() - (estimatedPillWidth / 2)
            layoutParams.x = targetX.coerceIn(safeSideMargin, screenWidth - estimatedPillWidth - safeSideMargin)

            // Prefer below selection; if too close to bottom, position above selection
            val spaceBelow = screenHeight - boundsInScreen.bottom - safeBottomMargin
            if (spaceBelow >= estimatedPillHeight + dp(12f)) {
                layoutParams.y = boundsInScreen.bottom + dp(10f)
            } else {
                layoutParams.y = (boundsInScreen.top - estimatedPillHeight - dp(10f)).coerceAtLeast(dp(40f))
            }
        } else {
            // Safe bottom fallback positioned above navigation bar
            layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams.x = 0
            layoutParams.y = safeBottomMargin
        }

        // Entrance animation
        rootLayout.alpha = 0f
        rootLayout.translationY = dp(16f).toFloat()

        try {
            windowManager.addView(rootLayout, layoutParams)
            currentView = rootLayout

            rootLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start()

            // Schedule auto-dismiss
            val autoDismissRunnable = Runnable {
                dismissWithAnimation(onDismiss)
            }
            dismissRunnable = autoDismissRunnable
            handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_DELAY_MS)
        } catch (_: Exception) {
            // Ignore WindowManager addView failures
            currentView = null
        }
    }

    fun dismissImmediately() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null

        val view = currentView ?: return
        currentView = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Exception) {
            // Ignore removeView errors
        }
    }

    fun dismissWithAnimation(onDismiss: (() -> Unit)? = null) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null

        val view = currentView ?: return
        currentView = null

        val dp16 = 16f * context.resources.displayMetrics.density
        view.animate()
            .alpha(0f)
            .translationY(dp16)
            .setDuration(150)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        windowManager.removeView(view)
                    } catch (_: Exception) {}
                    onDismiss?.invoke()
                }
            })
            .start()
    }
}
