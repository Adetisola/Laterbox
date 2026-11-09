package com.laterbox.app.capture.accessibility

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Custom Drawable that paints a rounded rectangle container with a solid background,
 * subtle base border, and an animated accent stroke traveling around the perimeter
 * to indicate countdown time before auto-dismissal.
 */
class CountdownBorderDrawable(
    private val cornerRadius: Float,
    private val strokeWidth: Float,
    private val backgroundColor: Int = Color.parseColor("#22201D"),
    private val baseBorderColor: Int = Color.parseColor("#3D3934"),
    private val progressBorderColor: Int = Color.parseColor("#E0A96D")
) : Drawable() {

    var progress: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidateSelf()
            }
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }

    private val baseBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = baseBorderColor
        strokeWidth = this@CountdownBorderDrawable.strokeWidth
    }

    private val progressBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = progressBorderColor
        strokeWidth = this@CountdownBorderDrawable.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val rectF = RectF()
    private val perimeterPath = Path()
    private val segmentPath = Path()
    private val pathMeasure = PathMeasure()
    private val lastBounds = Rect()

    private fun rebuildPerimeterPath() {
        perimeterPath.reset()
        val halfStroke = strokeWidth / 2f
        rectF.set(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke
        )

        if (rectF.width() <= 0f || rectF.height() <= 0f) return

        val r = cornerRadius.coerceAtMost(minOf(rectF.width(), rectF.height()) / 2f)
        val left = rectF.left
        val top = rectF.top
        val right = rectF.right
        val bottom = rectF.bottom
        val midX = rectF.centerX()

        // Clockwise perimeter starting at top-center
        perimeterPath.moveTo(midX, top)
        perimeterPath.lineTo(right - r, top)
        perimeterPath.arcTo(right - 2f * r, top, right, top + 2f * r, 270f, 90f, false)
        perimeterPath.lineTo(right, bottom - r)
        perimeterPath.arcTo(right - 2f * r, bottom - 2f * r, right, bottom, 0f, 90f, false)
        perimeterPath.lineTo(left + r, bottom)
        perimeterPath.arcTo(left, bottom - 2f * r, left + 2f * r, bottom, 90f, 90f, false)
        perimeterPath.lineTo(left, top + r)
        perimeterPath.arcTo(left, top, left + 2f * r, top + 2f * r, 180f, 90f, false)
        perimeterPath.lineTo(midX, top)
        perimeterPath.close()

        pathMeasure.setPath(perimeterPath, true)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds != lastBounds) {
            lastBounds.set(bounds)
            rebuildPerimeterPath()
        }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        if (perimeterPath.isEmpty) {
            rebuildPerimeterPath()
        }

        val r = cornerRadius.coerceAtMost(minOf(rectF.width(), rectF.height()) / 2f)

        // 1. Solid background fill
        canvas.drawRoundRect(rectF, r, r, fillPaint)

        // 2. Subtle baseline border
        canvas.drawRoundRect(rectF, r, r, baseBorderPaint)

        // 3. Countdown progress border
        if (progress > 0f) {
            segmentPath.reset()
            val totalLength = pathMeasure.length
            if (totalLength > 0f) {
                val lengthToDraw = totalLength * progress
                pathMeasure.getSegment(0f, lengthToDraw, segmentPath, true)
                canvas.drawPath(segmentPath, progressBorderPaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        baseBorderPaint.alpha = alpha
        progressBorderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        baseBorderPaint.colorFilter = colorFilter
        progressBorderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
