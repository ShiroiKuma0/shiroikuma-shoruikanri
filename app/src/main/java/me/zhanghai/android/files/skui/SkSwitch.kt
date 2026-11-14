/*
 * 白い熊 fork (skui): the switch of the black-yellow theme — an outlined pill
 * with the background showing through and a solid dot inside it, on the right
 * when on and on the left when off. Nothing is ever filled; the two states
 * differ only by the dot's side and by brightness (the accent when on, an
 * opaque darker yellow when off).
 *
 * It stays a SwitchCompat, so preference rows and every caller that expects
 * one keep working; with the 白い熊 theme off it draws as the stock switch.
 */

package me.zhanghai.android.files.skui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils

class SkSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.switchStyle
) : SwitchCompat(context, attrs, defStyleAttr) {
    // False while the super constructor runs (it may call setChecked()).
    private var isReady = false
    private val isSk = SkUi.isSkThemeEnabled
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val density = resources.displayMetrics.density

    // 0 = off (dot on the left), 1 = on (dot on the right).
    private var fraction = 0f
    private var animator: ValueAnimator? = null

    init {
        fraction = if (isChecked) 1f else 0f
        if (isSk) {
            background = null
        }
        isReady = true
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)

        if (!isReady || !isSk) {
            return
        }
        val target = if (checked) 1f else 0f
        animator?.cancel()
        if (isAttachedToWindow && isLaidOut && isShown) {
            animator = ValueAnimator.ofFloat(fraction, target).apply {
                duration = TRAVEL_MILLIS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    fraction = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            fraction = target
            invalidate()
        }
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()

        if (isReady && isSk) {
            animator?.end()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!isSk) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        setMeasuredDimension(
            View.resolveSize(
                Math.round(WIDTH_DP * density) + paddingLeft + paddingRight, widthMeasureSpec
            ),
            View.resolveSize(
                Math.round(HEIGHT_DP * density) + paddingTop + paddingBottom, heightMeasureSpec
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!isSk) {
            super.onDraw(canvas)
            return
        }
        val trackWidth = WIDTH_DP * density
        val trackHeight = HEIGHT_DP * density
        val left = paddingLeft + (width - paddingLeft - paddingRight - trackWidth) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - trackHeight) / 2f
        val stroke = STROKE_DP * density
        var color = ColorUtils.blendARGB(
            skColor(SkThemeSlot.SWITCH_OFF), skColor(SkThemeSlot.ACCENT), fraction
        )
        if (!isEnabled) {
            color = ColorUtils.setAlphaComponent(color, 0x61)
        }
        paint.color = color

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        trackRect.set(
            left + stroke / 2f, top + stroke / 2f, left + trackWidth - stroke / 2f,
            top + trackHeight - stroke / 2f
        )
        val radius = trackRect.height() / 2f
        canvas.drawRoundRect(trackRect, radius, radius, paint)

        // The dot keeps the same gap to the frame all round, at either end.
        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val position = if (isRtl) 1f - fraction else fraction
        val startX = left + trackHeight / 2f
        val endX = left + trackWidth - trackHeight / 2f
        paint.style = Paint.Style.FILL
        canvas.drawCircle(
            startX + (endX - startX) * position, top + trackHeight / 2f,
            trackHeight * DOT_FRACTION / 2f, paint
        )
    }

    // A plain tap toggles; the stock drag handling works on the stock thumb, which is not drawn.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSk) {
            return super.onTouchEvent(event)
        }
        if (!isEnabled || !isClickable) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> isPressed = true
            MotionEvent.ACTION_MOVE ->
                if (event.x < 0 || event.y < 0 || event.x > width || event.y > height) {
                    isPressed = false
                }
            MotionEvent.ACTION_UP ->
                if (isPressed) {
                    isPressed = false
                    performClick()
                }
            MotionEvent.ACTION_CANCEL -> isPressed = false
        }
        return true
    }

    companion object {
        private const val WIDTH_DP = 52f
        private const val HEIGHT_DP = 28f
        private const val STROKE_DP = 2f
        private const val DOT_FRACTION = 0.46f
        private const val TRAVEL_MILLIS = 180L
    }
}
