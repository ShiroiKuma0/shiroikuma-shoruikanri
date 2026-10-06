/*
 * 白い熊 fork: wraps the image viewer's pager and watches for a pinch-in that starts with the
 * current image at its minimum zoom. Such a pinch leaves the single-image view for the grid of
 * neighbouring images, and the rest of that same pinch keeps zooming the grid; any other pinch
 * passes through to the zoomable image untouched.
 */

package me.zhanghai.android.files.viewer.image

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

class SkPinchToGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var canPinchToGrid: () -> Boolean = { false }
    var onPinchToGrid: (() -> Unit)? = null
    var onPinchContinue: ((scaleFactor: Float, focusX: Float, focusY: Float) -> Unit)? = null

    private var isArmed = false
    private var cumulativeScale = 1f
    private var isSwallowingGesture = false

    private val scaleDetector = ScaleGestureDetector(
        context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isArmed = canPinchToGrid()
                cumulativeScale = 1f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isSwallowingGesture) {
                    onPinchContinue?.invoke(
                        detector.scaleFactor, detector.focusX, detector.focusY
                    )
                    return true
                }
                if (!isArmed) {
                    return true
                }
                cumulativeScale *= detector.scaleFactor
                if (cumulativeScale < TRIGGER_SCALE) {
                    isArmed = false
                    isSwallowingGesture = true
                    cancelChildGesture()
                    onPinchToGrid?.invoke()
                }
                return true
            }
        }
    )

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isSwallowingGesture) {
            scaleDetector.onTouchEvent(event)
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isSwallowingGesture = false
            }
            return true
        }
        scaleDetector.onTouchEvent(event)
        if (isSwallowingGesture) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun cancelChildGesture() {
        val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    companion object {
        private const val TRIGGER_SCALE = 0.75f
    }
}
