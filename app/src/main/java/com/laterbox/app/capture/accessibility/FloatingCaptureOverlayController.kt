package com.laterbox.app.capture.accessibility

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.laterbox.app.ui.components.LaterboxIcons
import kotlin.math.hypot

enum class OverlayState {
    IDLE,
    PRESENTING,
    DRAGGING,
    DISMISSED
}

/**
 * Manages the draggable, contextual capture prompt overlaid via WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY.
 */
class FloatingCaptureOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {

    companion object {
        const val AUTO_DISMISS_DELAY_MS = 5000L
    }

    private var currentView: View? = null
    private var countdownAnimator: ValueAnimator? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var sessionGeneration: Long = 0L
    var currentState: OverlayState = OverlayState.IDLE
        private set

    val isShowing: Boolean
        get() = currentState == OverlayState.PRESENTING || currentState == OverlayState.DRAGGING

    @SuppressLint("ClickableViewAccessibility")
    fun showPrompt(
        domain: String,
        url: String,
        boundsInScreen: Rect? = null,
        initialXRatio: Float = 0.5f,
        initialYRatio: Float = 0.82f,
        onPositionChanged: ((xRatio: Float, yRatio: Float) -> Unit)? = null,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        dismissImmediately()

        sessionGeneration++
        val sessionId = sessionGeneration
        currentState = OverlayState.PRESENTING

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        // Create Clockwise Countdown Border Drawable
        val borderDrawable = CountdownBorderDrawable(
            cornerRadius = dp(24f).toFloat(),
            strokeWidth = dp(1.5f).toFloat(),
            backgroundColor = Color.parseColor("#22201D"),
            baseBorderColor = Color.parseColor("#3D3934"),
            progressBorderColor = Color.parseColor("#E0A96D")
        )

        // Root Pill Layout
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(6f), dp(8f), dp(6f))
            elevation = dp(6f).toFloat()
            background = borderDrawable
        }

        // Leading Drag Affordance / Globe Icon
        val iconView = ImageView(context).apply {
            val size = dp(18f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(8f)
            }
            setImageResource(LaterboxIcons.Globe)
            setColorFilter(Color.parseColor("#E0A96D"))
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
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        textContainer.addView(titleView)

        val domainView = TextView(context).apply {
            text = domain
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(Color.parseColor("#B3ABA0"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textContainer.addView(domainView)

        rootLayout.addView(textContainer)

        // Action: "Save" Pill Button
        val saveButton = TextView(context).apply {
            text = "Save"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#1B1A18"))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                setColor(Color.parseColor("#E0A96D"))
            }
            setOnClickListener {
                if (sessionId != sessionGeneration) return@setOnClickListener
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
                if (sessionId != sessionGeneration) return@setOnClickListener
                dismissWithAnimation(onDismiss)
            }
        }
        rootLayout.addView(closeButton)

        // Boundaries calculation
        val safeSideMargin = dp(16f)
        val safeTopMargin = dp(48f)
        val safeBottomMargin = dp(76f)

        // Estimated initial bounds before first layout pass
        var currentPillWidth = dp(270f)
        var currentPillHeight = dp(52f)

        fun calculateClampedX(ratio: Float, pillWidth: Int): Int {
            val minX = safeSideMargin
            val maxX = (screenWidth - pillWidth - safeSideMargin).coerceAtLeast(minX)
            val range = maxX - minX
            return (minX + range * ratio).toInt().coerceIn(minX, maxX)
        }

        fun calculateClampedY(ratio: Float, pillHeight: Int): Int {
            val minY = safeTopMargin
            val maxY = (screenHeight - pillHeight - safeBottomMargin).coerceAtLeast(minY)
            val range = maxY - minY
            return (minY + range * ratio).toInt().coerceIn(minY, maxY)
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = calculateClampedX(initialXRatio, currentPillWidth)
            y = calculateClampedY(initialYRatio, currentPillHeight)
        }

        // Post-layout measurement listener to adjust clamping to exact rendered size
        rootLayout.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (sessionId != sessionGeneration || currentState == OverlayState.DISMISSED) return@addOnLayoutChangeListener
            val measuredWidth = right - left
            val measuredHeight = bottom - top
            if (measuredWidth > 0 && measuredHeight > 0 && (measuredWidth != currentPillWidth || measuredHeight != currentPillHeight)) {
                currentPillWidth = measuredWidth
                currentPillHeight = measuredHeight
                if (currentState != OverlayState.DRAGGING) {
                    layoutParams.x = calculateClampedX(initialXRatio, currentPillWidth)
                    layoutParams.y = calculateClampedY(initialYRatio, currentPillHeight)
                    try {
                        windowManager.updateViewLayout(rootLayout, layoutParams)
                    } catch (_: Exception) {}
                }
            }
        }

        // Drag handling on the pill body
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialLayoutX = 0
        var initialLayoutY = 0
        var isDraggingPill = false

        rootLayout.setOnTouchListener { _, event ->
            if (sessionId != sessionGeneration || currentState == OverlayState.DISMISSED) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialLayoutX = layoutParams.x
                    initialLayoutY = layoutParams.y
                    isDraggingPill = false
                    false // Allow child views to receive down
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDraggingPill && hypot(dx, dy) > touchSlop) {
                        isDraggingPill = true
                        currentState = OverlayState.DRAGGING
                        countdownAnimator?.pause()
                    }

                    if (isDraggingPill) {
                        val minX = safeSideMargin
                        val maxX = (screenWidth - currentPillWidth - safeSideMargin).coerceAtLeast(minX)
                        val minY = safeTopMargin
                        val maxY = (screenHeight - currentPillHeight - safeBottomMargin).coerceAtLeast(minY)

                        layoutParams.x = (initialLayoutX + dx.toInt()).coerceIn(minX, maxX)
                        layoutParams.y = (initialLayoutY + dy.toInt()).coerceIn(minY, maxY)
                        try {
                            windowManager.updateViewLayout(rootLayout, layoutParams)
                        } catch (_: Exception) {}
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingPill) {
                        isDraggingPill = false
                        currentState = OverlayState.PRESENTING

                        val minX = safeSideMargin
                        val maxX = (screenWidth - currentPillWidth - safeSideMargin).coerceAtLeast(minX)
                        val minY = safeTopMargin
                        val maxY = (screenHeight - currentPillHeight - safeBottomMargin).coerceAtLeast(minY)

                        val rangeX = (maxX - minX).coerceAtLeast(1)
                        val rangeY = (maxY - minY).coerceAtLeast(1)

                        val newXRatio = ((layoutParams.x - minX).toFloat() / rangeX).coerceIn(0f, 1f)
                        val newYRatio = ((layoutParams.y - minY).toFloat() / rangeY).coerceIn(0f, 1f)

                        onPositionChanged?.invoke(newXRatio, newYRatio)
                        countdownAnimator?.resume()
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
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

            // Synchronized Countdown Animator (single timing source for border and dismissal)
            val animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = AUTO_DISMISS_DELAY_MS
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    if (sessionId == sessionGeneration && currentState != OverlayState.DISMISSED) {
                        borderDrawable.progress = va.animatedValue as Float
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (sessionId == sessionGeneration && currentState == OverlayState.PRESENTING) {
                            dismissWithAnimation(onDismiss)
                        }
                    }
                })
            }
            countdownAnimator = animator
            animator.start()
        } catch (_: Exception) {
            currentView = null
            currentState = OverlayState.IDLE
        }
    }

    fun dismissImmediately() {
        sessionGeneration++
        currentState = OverlayState.DISMISSED

        countdownAnimator?.cancel()
        countdownAnimator = null

        val view = currentView ?: return
        currentView = null
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Exception) {
            // Ignore removeView errors
        }
    }

    fun dismissWithAnimation(onDismiss: (() -> Unit)? = null) {
        val sessionId = sessionGeneration
        currentState = OverlayState.DISMISSED

        countdownAnimator?.cancel()
        countdownAnimator = null

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
                    if (sessionId == sessionGeneration) {
                        onDismiss?.invoke()
                    }
                }
            })
            .start()
    }
}
