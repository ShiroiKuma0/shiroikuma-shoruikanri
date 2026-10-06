/*
 * 白い熊 fork: the image viewer's zoomed-out mode — every image of the folder laid out once, in
 * order, as a wall of equal cells, freely zoomable between "the whole wall fits" and "one image
 * fills the screen", and draggable / flingable in any direction. The column count is picked so
 * that the fully zoomed-out wall matches the screen's shape: a few images on a wide screen make a
 * strip, many make rows.
 */

package me.zhanghai.android.files.viewer.image

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.skui.SkThemeSlot
import me.zhanghai.android.files.skui.skColor
import me.zhanghai.android.files.util.dpToDimension
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SkImageWallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    /** A tap on an image: show it alone. */
    var onImageTap: ((Int) -> Unit)? = null
    /** A pinch out beyond one-image size: show the image under the fingers alone. */
    var onZoomPastImage: ((Int) -> Unit)? = null

    var highlightedIndex = -1
        set(value) {
            field = value
            invalidate()
        }

    private var paths: List<Path> = emptyList()

    // Wall geometry, in wall units.
    private val cellWidth = 1000f
    private var cellHeight = 1000f
    private val gap = 30f
    private var columns = 1
    private var rows = 1

    private val contentInsets = Rect()

    // Screen = offset + wall * scale.
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var minScale = 1f
    private var maxScale = 1f

    private var isGestureHandedOff = false
    private var unclampedScale = 1f

    private val entries = HashMap<Int, Entry>()

    private val placeholderPaint = Paint().apply { color = 0xFF151515.toInt() }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToDimension(3)
        color = skColor(SkThemeSlot.ACCENT)
    }
    private val tempRect = RectF()
    private val tempBounds = Rect()

    private val scroller = OverScroller(context)
    private var animator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(
        context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastFocusX = 0f
            private var lastFocusY = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                unclampedScale = scale
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isGestureHandedOff) {
                    return true
                }
                // Pan with the fingers' midpoint as well as zooming around it.
                offsetX += detector.focusX - lastFocusX
                offsetY += detector.focusY - lastFocusY
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                applyScale(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scroller.forceFinished(true)
                animator?.cancel()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleDetector.isInProgress || e2.pointerCount > 1) {
                    return false
                }
                offsetX -= distanceX
                offsetY -= distanceY
                constrain()
                invalidate()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val (minX, maxX) = offsetRange(columnsWidth(), contentInsets.left, areaWidth)
                val (minY, maxY) = offsetRange(rowsHeight(), contentInsets.top, areaHeight)
                scroller.fling(
                    offsetX.roundToInt(), offsetY.roundToInt(), velocityX.roundToInt(),
                    velocityY.roundToInt(), minX.roundToInt(), maxX.roundToInt(),
                    minY.roundToInt(), maxY.roundToInt()
                )
                postInvalidateOnAnimation()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val index = indexAt(e.x, e.y)
                if (index != -1) {
                    onImageTap?.invoke(index)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val index = indexAt(e.x, e.y)
                if (scale < maxScale * 0.9f && index != -1) {
                    animateTo(maxScale, index)
                } else {
                    animateTo(minScale, -1, e.x, e.y)
                }
                return true
            }
        }
    )

    private val areaWidth: Float
        get() = (width - contentInsets.left - contentInsets.right).toFloat()

    private val areaHeight: Float
        get() = (height - contentInsets.top - contentInsets.bottom).toFloat()

    private val pitchX: Float
        get() = cellWidth + gap

    private val pitchY: Float
        get() = cellHeight + gap

    private fun columnsWidth(): Float = (columns * pitchX - gap) * scale

    private fun rowsHeight(): Float = (rows * pitchY - gap) * scale

    fun setPaths(paths: List<Path>, cellAspect: Float) {
        clearEntries()
        this.paths = paths
        cellHeight = cellWidth * cellAspect.coerceIn(0.2f, 5f)
        updateLayout()
        invalidate()
    }

    /** Same images minus some (after a delete), keeping the cell shape and the view. */
    fun replacePaths(paths: List<Path>) {
        val centerIndex = indexNearestToCenter()
        setPaths(paths, cellHeight / cellWidth)
        if (centerIndex != -1) {
            centerOn(centerIndex.coerceAtMost(paths.lastIndex))
        }
    }

    fun setContentInsets(left: Int, top: Int, right: Int, bottom: Int) {
        if (contentInsets.left == left && contentInsets.top == top &&
            contentInsets.right == right && contentInsets.bottom == bottom) {
            return
        }
        val centerIndex = indexNearestToCenter()
        contentInsets.set(left, top, right, bottom)
        updateLayout()
        if (centerIndex != -1) {
            centerOn(centerIndex)
        }
        invalidate()
    }

    /**
     * Show [index] at [fraction] of the one-image size, centred, e.g. just after a pinch in on
     * the single-image view so the neighbours already peek in.
     */
    fun showImage(index: Int, fraction: Float) {
        scale = (maxScale * fraction).coerceIn(minScale, maxScale)
        centerOn(index)
        invalidate()
    }

    /** Continue a pinch that began on the single-image view. */
    fun applyExternalScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        applyScale(scaleFactor, focusX, focusY)
    }

    fun beginExternalScale() {
        isGestureHandedOff = false
        unclampedScale = scale
    }

    private fun applyScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        unclampedScale *= scaleFactor
        if (unclampedScale > maxScale * ZOOM_PAST_IMAGE_FACTOR) {
            isGestureHandedOff = true
            val index = indexAt(focusX, focusY).takeIf { it != -1 } ?: indexNearestToCenter()
            if (index != -1) {
                onZoomPastImage?.invoke(index)
            }
            return
        }
        val newScale = unclampedScale.coerceIn(minScale, maxScale)
        val wallX = (focusX - offsetX) / scale
        val wallY = (focusY - offsetY) / scale
        scale = newScale
        offsetX = focusX - wallX * scale
        offsetY = focusY - wallY * scale
        constrain()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            isGestureHandedOff = false
        }
        if (isGestureHandedOff) {
            return true
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val centerIndex = indexNearestToCenter()
        updateLayout()
        if (centerIndex != -1) {
            centerOn(centerIndex)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            constrain()
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        animator?.cancel()
        clearEntries()
    }

    // Choose the column count whose fully zoomed-out wall best matches the area's shape.
    private fun updateLayout() {
        val count = paths.size
        if (count == 0 || areaWidth <= 0 || areaHeight <= 0) {
            return
        }
        val areaAspect = areaWidth / areaHeight
        var bestColumns = 1
        var bestError = Float.MAX_VALUE
        for (candidate in 1..count) {
            val candidateRows = ceil(count / candidate.toFloat()).toInt()
            val wallAspect = (candidate * pitchX - gap) / (candidateRows * pitchY - gap)
            val error = abs(ln(wallAspect / areaAspect))
            if (error < bestError) {
                bestError = error
                bestColumns = candidate
            }
        }
        columns = bestColumns
        rows = ceil(count / columns.toFloat()).toInt()
        maxScale = min(areaWidth / cellWidth, areaHeight / cellHeight)
        minScale = min(
            areaWidth / (columns * pitchX - gap), areaHeight / (rows * pitchY - gap)
        ).coerceAtMost(maxScale)
        scale = scale.coerceIn(minScale, maxScale)
        constrain()
    }

    private fun offsetRange(contentSize: Float, areaStart: Int, areaSize: Float): Pair<Float, Float> =
        if (contentSize <= areaSize) {
            val centered = areaStart + (areaSize - contentSize) / 2
            centered to centered
        } else {
            (areaStart + areaSize - contentSize) to areaStart.toFloat()
        }

    private fun constrain() {
        val (minX, maxX) = offsetRange(columnsWidth(), contentInsets.left, areaWidth)
        val (minY, maxY) = offsetRange(rowsHeight(), contentInsets.top, areaHeight)
        offsetX = offsetX.coerceIn(minX, maxX)
        offsetY = offsetY.coerceIn(minY, maxY)
    }

    private fun centerOn(index: Int) {
        val column = index % columns
        val row = index / columns
        val centerX = (column * pitchX + cellWidth / 2) * scale
        val centerY = (row * pitchY + cellHeight / 2) * scale
        offsetX = contentInsets.left + areaWidth / 2 - centerX
        offsetY = contentInsets.top + areaHeight / 2 - centerY
        constrain()
    }

    private fun animateTo(targetScale: Float, index: Int, focusX: Float = 0f, focusY: Float = 0f) {
        animator?.cancel()
        scroller.forceFinished(true)
        val startScale = scale
        val startOffsetX = offsetX
        val startOffsetY = offsetY
        // Compute the end state, then interpolate towards it.
        scale = targetScale
        if (index != -1) {
            centerOn(index)
        } else {
            val wallX = (focusX - startOffsetX) / startScale
            val wallY = (focusY - startOffsetY) / startScale
            offsetX = focusX - wallX * scale
            offsetY = focusY - wallY * scale
            constrain()
        }
        val endOffsetX = offsetX
        val endOffsetY = offsetY
        scale = startScale
        offsetX = startOffsetX
        offsetY = startOffsetY
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                scale = startScale + (targetScale - startScale) * fraction
                offsetX = startOffsetX + (endOffsetX - startOffsetX) * fraction
                offsetY = startOffsetY + (endOffsetY - startOffsetY) * fraction
                invalidate()
            }
            start()
        }
    }

    private fun indexAt(x: Float, y: Float): Int {
        val wallX = (x - offsetX) / scale
        val wallY = (y - offsetY) / scale
        if (wallX < 0 || wallY < 0) {
            return -1
        }
        val column = floor(wallX / pitchX).toInt()
        val row = floor(wallY / pitchY).toInt()
        if (column >= columns || wallX - column * pitchX > cellWidth ||
            wallY - row * pitchY > cellHeight) {
            return -1
        }
        val index = row * columns + column
        return if (index in paths.indices) index else -1
    }

    private fun indexNearestToCenter(): Int {
        if (paths.isEmpty() || width == 0) {
            return -1
        }
        val wallX = (contentInsets.left + areaWidth / 2 - offsetX) / scale
        val wallY = (contentInsets.top + areaHeight / 2 - offsetY) / scale
        val column = floor(wallX / pitchX).toInt().coerceIn(0, columns - 1)
        val row = floor(wallY / pitchY).toInt().coerceIn(0, rows - 1)
        return (row * columns + column).coerceIn(paths.indices)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (paths.isEmpty()) {
            return
        }
        val firstColumn = floor((-offsetX / scale) / pitchX).toInt().coerceAtLeast(0)
        val lastColumn = floor(((width - offsetX) / scale) / pitchX).toInt()
            .coerceAtMost(columns - 1)
        val firstRow = floor((-offsetY / scale) / pitchY).toInt().coerceAtLeast(0)
        val lastRow = floor(((height - offsetY) / scale) / pitchY).toInt().coerceAtMost(rows - 1)
        val bucket = sizeBucket(max(cellWidth, cellHeight) * scale)
        val visible = HashSet<Int>()
        for (row in firstRow..lastRow) {
            for (column in firstColumn..lastColumn) {
                val index = row * columns + column
                if (index !in paths.indices) {
                    continue
                }
                visible += index
                val left = offsetX + column * pitchX * scale
                val top = offsetY + row * pitchY * scale
                tempRect.set(left, top, left + cellWidth * scale, top + cellHeight * scale)
                val entry = entries.getOrPut(index) { Entry() }
                ensureLoaded(index, entry, bucket)
                val drawable = entry.drawable
                if (drawable == null) {
                    canvas.drawRect(tempRect, placeholderPaint)
                } else {
                    fitInto(drawable, tempRect)
                    drawable.bounds = tempBounds
                    drawable.draw(canvas)
                }
                if (index == highlightedIndex) {
                    if (drawable != null) {
                        tempRect.set(tempBounds)
                    }
                    canvas.drawRect(tempRect, highlightPaint)
                }
            }
        }
        releaseInvisible(visible, firstRow, lastRow, firstColumn, lastColumn)
    }

    private fun fitInto(drawable: Drawable, cell: RectF) {
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            cell.round(tempBounds)
            return
        }
        val fit = min(cell.width() / intrinsicWidth, cell.height() / intrinsicHeight)
        val drawWidth = intrinsicWidth * fit
        val drawHeight = intrinsicHeight * fit
        val left = cell.left + (cell.width() - drawWidth) / 2
        val top = cell.top + (cell.height() - drawHeight) / 2
        tempBounds.set(
            left.roundToInt(), top.roundToInt(), (left + drawWidth).roundToInt(),
            (top + drawHeight).roundToInt()
        )
    }

    // Decode at the next power of two above the on-screen size (never above the screen), so a
    // zoom only reloads when it crosses a bucket, and the old bitmap shows until the new lands.
    private fun sizeBucket(onScreenSize: Float): Int {
        var bucket = MIN_BUCKET
        while (bucket < onScreenSize) {
            bucket *= 2
        }
        return min(bucket, max(width, height).coerceAtLeast(MIN_BUCKET))
    }

    private fun ensureLoaded(index: Int, entry: Entry, bucket: Int) {
        if (entry.requestedSize >= bucket || entry.failed) {
            return
        }
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        entry.job?.cancel()
        entry.requestedSize = bucket
        val path = paths[index]
        entry.job = scope.launch {
            val attributes = entry.attributes ?: try {
                withContext(Dispatchers.IO) {
                    path.readAttributes(BasicFileAttributes::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                entry.failed = true
                return@launch
            }
            entry.attributes = attributes
            val request = ImageRequest.Builder(context)
                .data(path to attributes)
                .size(bucket, bucket)
                .precision(Precision.INEXACT)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                entry.drawable = result.drawable
                invalidate()
            } else {
                entry.failed = true
            }
        }
    }

    // Keep decoded images only for cells on screen or just around it.
    private fun releaseInvisible(
        visible: Set<Int>,
        firstRow: Int,
        lastRow: Int,
        firstColumn: Int,
        lastColumn: Int
    ) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (index, entry) = iterator.next()
            if (index in visible) {
                continue
            }
            val row = index / columns
            val column = index % columns
            val isNear = row in (firstRow - KEEP_MARGIN)..(lastRow + KEEP_MARGIN) &&
                column in (firstColumn - KEEP_MARGIN)..(lastColumn + KEEP_MARGIN)
            if (!isNear) {
                entry.job?.cancel()
                iterator.remove()
            }
        }
    }

    private fun clearEntries() {
        for (entry in entries.values) {
            entry.job?.cancel()
        }
        entries.clear()
    }

    private class Entry {
        var attributes: BasicFileAttributes? = null
        var drawable: Drawable? = null
        var requestedSize = 0
        var failed = false
        var job: Job? = null
    }

    companion object {
        private const val MIN_BUCKET = 256
        private const val KEEP_MARGIN = 1
        private const val ZOOM_PAST_IMAGE_FACTOR = 1.2f
    }
}
