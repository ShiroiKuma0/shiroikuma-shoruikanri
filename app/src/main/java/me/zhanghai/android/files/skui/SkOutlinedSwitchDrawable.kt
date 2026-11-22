/*
 * 白い熊 fork (skui): the theme's switch as a drawable — a round outline with a solid dot,
 * right and in the on colour when checked, left and in the darker off colour when not. Never
 * filled. The button of a checkable menu row.
 */

package me.zhanghai.android.files.skui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class SkOutlinedSwitchDrawable(
    private val on: Int,
    private val off: Int,
    private val widthPx: Int,
    private val heightPx: Int,
    strokePx: Int
) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx.toFloat()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var checked = false

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val color = if (checked) on else off
        val inset = stroke.strokeWidth / 2f
        rect.set(
            bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset
        )
        stroke.color = color
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, stroke)
        fill.color = color
        val centerX = if (checked) {
            bounds.right - bounds.height() / 2f
        } else {
            bounds.left + bounds.height() / 2f
        }
        canvas.drawCircle(centerX, bounds.exactCenterY(), bounds.height() * DOT_RADIUS_SHARE, fill)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val now = state.contains(android.R.attr.state_checked)
        if (now == checked) {
            return false
        }
        checked = now
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = widthPx

    override fun getIntrinsicHeight() = heightPx

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val DOT_RADIUS_SHARE = 0.23f // a dot 46 % of the track's height
    }
}
