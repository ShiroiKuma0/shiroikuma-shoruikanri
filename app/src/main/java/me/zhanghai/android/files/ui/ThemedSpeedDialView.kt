/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RotateDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Property
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.leinardi.android.speeddial.FabWithLabelView
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.compat.createCompat
import me.zhanghai.android.files.compat.drawableCompat
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.setTextAppearanceCompat
import me.zhanghai.android.files.skui.SkThemeSlot
import me.zhanghai.android.files.skui.skColor
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.asColor
import me.zhanghai.android.files.util.dpToDimensionPixelSize
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.getParcelableSafe
import me.zhanghai.android.files.util.getResourceIdByAttr
import me.zhanghai.android.files.util.isMaterial3Theme
import me.zhanghai.android.files.util.shortAnimTime
import me.zhanghai.android.files.util.withModulatedAlpha

class ThemedSpeedDialView : SpeedDialView {
    private var onChangeListener: OnChangeListener? = null

    private var mainFabAnimator: Animator? = null

    // 白い熊 fork: the opened action items read as one dialog — a black box with a single
    // yellow frame drawn around all of them, labels and mini buttons alike, like the audio
    // mini-player's box. The main FAB stays outside it: it's the button that opens this.
    private val skFrameDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 12 * resources.displayMetrics.density
    }

    // 0 while closed, 1 while open — fades the frame in and out with the items.
    private var skFrameFraction = 0f

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    init {
        // Work around ripple bug on Android 12 when useCompatPadding = true.
        // @see https://github.com/material-components/material-components-android/issues/2617
        mainFab.apply {
            updateLayoutParams<MarginLayoutParams> {
                setMargins(context.dpToDimensionPixelSize(16))
            }
            useCompatPadding = false
        }
        val context = context
        if (context.isMaterial3Theme) {
            mainFabClosedBackgroundColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorSecondaryContainer)
            mainFabClosedIconColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
            mainFabOpenedBackgroundColor =
                context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
            mainFabOpenedIconColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorOnPrimary)
        } else {
            mainFabClosedBackgroundColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorSecondary)
            mainFabClosedIconColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorOnSecondary)
            mainFabOpenedBackgroundColor = mainFabClosedBackgroundColor
            mainFabOpenedIconColor = mainFabClosedIconColor
        }
        // Always use our own animation to fix the library issue that ripple is rotated as well.
        val mainFabDrawable = RotateDrawable::class.createCompat().apply {
            drawableCompat = mainFab.drawable
            toDegrees = mainFabAnimationRotateAngle
        }
        mainFabAnimationRotateAngle = 0f
        setMainFabClosedDrawable(mainFabDrawable)
        super.setOnChangeListener(object : OnChangeListener {
            override fun onMainActionSelected(): Boolean =
                onChangeListener?.onMainActionSelected() ?: false

            override fun onToggleChanged(isOpen: Boolean) {
                mainFabAnimator?.cancel()
                mainFabAnimator = createMainFabAnimator(isOpen).apply {
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            mainFabAnimator = null
                        }
                    })
                    start()
                }
                onChangeListener?.onToggleChanged(isOpen)
            }
        })
        applySkStyle()
    }

    override fun setOnChangeListener(onChangeListener: OnChangeListener?) {
        this.onChangeListener = onChangeListener
    }

    // 白い熊 fork: (re)read the configured slot colors into the dialog frame. Called again
    // whenever the UI page changes a slot.
    fun applySkStyle() {
        skFrameDrawable.setColor(skColor(SkThemeSlot.FAB_BACKGROUND))
        skFrameDrawable.setStroke(
            resources.displayMetrics.density.toInt().coerceAtLeast(1),
            skColor(SkThemeSlot.FAB_ICON)
        )
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawSkFrame(canvas)

        super.dispatchDraw(canvas)
    }

    // 白い熊 fork: one rounded frame around the union of every action item's label chip and
    // mini button. The item views themselves are wider than their content — the mini fabs
    // carry 20dp side margins — so the box is measured from the content views, not from the
    // rows, and then padded out evenly.
    private fun drawSkFrame(canvas: Canvas) {
        if (skFrameFraction <= 0f) {
            return
        }
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (index in 0 until childCount) {
            val itemView = getChildAt(index) as? FabWithLabelView ?: continue
            if (itemView.visibility != VISIBLE) {
                continue
            }
            for (contentView in arrayOf<View>(itemView.labelBackground, itemView.fab)) {
                if (contentView.visibility != VISIBLE) {
                    continue
                }
                left = minOf(left, itemView.left + contentView.left)
                top = minOf(top, itemView.top + contentView.top)
                right = maxOf(right, itemView.left + contentView.right)
                bottom = maxOf(bottom, itemView.top + contentView.bottom)
            }
        }
        if (left > right || top > bottom) {
            return
        }
        val padding = context.dpToDimensionPixelSize(12)
        skFrameDrawable.alpha = (255 * skFrameFraction).toInt()
        skFrameDrawable.setBounds(left - padding, top - padding, right + padding, bottom + padding)
        skFrameDrawable.draw(canvas)
    }

    private fun createMainFabAnimator(isOpen: Boolean): Animator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofArgb(
                    mainFab, VIEW_PROPERTY_BACKGROUND_TINT,
                    if (isOpen) mainFabOpenedBackgroundColor else mainFabClosedBackgroundColor
                ),
                ObjectAnimator.ofArgb(
                    mainFab, IMAGE_VIEW_PROPERTY_IMAGE_TINT,
                    if (isOpen) mainFabOpenedIconColor else mainFabClosedIconColor
                ),
                ObjectAnimator.ofInt(
                    mainFab.drawable, DRAWABLE_PROPERTY_LEVEL, if (isOpen) 10000 else 0
                ),
                // 白い熊 fork: fade the dialog frame in and out along with the items.
                ValueAnimator.ofFloat(skFrameFraction, if (isOpen) 1f else 0f).apply {
                    addUpdateListener {
                        skFrameFraction = it.animatedValue as Float
                        invalidate()
                    }
                }
            )
            duration = context.shortAnimTime.toLong()
            interpolator = FastOutSlowInInterpolator()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val overlayLayout = overlayLayout
        if (overlayLayout != null) {
            val surfaceColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorSurface)
            val overlayColor = surfaceColor.asColor().withModulatedAlpha(0.87f).value
            overlayLayout.setBackgroundColor(overlayColor)
        }
    }

    override fun addActionItem(
        actionItem: SpeedDialActionItem,
        position: Int,
        animate: Boolean
    ): FabWithLabelView? {
        val context = context
        val isMaterial3Theme = context.isMaterial3Theme
        val fabImageTintColor = if (isMaterial3Theme) {
            context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
        } else {
            context.getColorByAttr(com.google.android.material.R.attr.colorSecondary)
        }
        val fabBackgroundColor =
            context.getColorByAttr(com.google.android.material.R.attr.colorSurface)
        val labelColor = context.getColorByAttr(android.R.attr.textColorSecondary)
        val labelBackgroundColor = if (isMaterial3Theme) {
            Color.TRANSPARENT
        } else {
            // Label view doesn't have enough elevation (only 1dp) for elevation overlay to work
            // well.
            context.getColorByAttr(androidx.appcompat.R.attr.colorBackgroundFloating)
        }
        val actionItem = SpeedDialActionItem.Builder(
            actionItem.id,
            // Should not be a resource, pass null to fail fast.
            actionItem.getFabImageDrawable(null)
        )
            .setLabel(actionItem.getLabel(context))
            .setFabImageTintColor(fabImageTintColor)
            .setFabBackgroundColor(fabBackgroundColor)
            .setLabelColor(labelColor)
            .setLabelBackgroundColor(labelBackgroundColor)
            .setLabelClickable(actionItem.isLabelClickable)
            .setTheme(actionItem.theme)
            .create()
        return super.addActionItem(actionItem, position, animate)?.apply {
            fab.apply {
                updateLayoutParams<MarginLayoutParams> {
                    val horizontalMargin = context.dpToDimensionPixelSize(20)
                    setMargins(horizontalMargin, 0, horizontalMargin, 0)
                }
                useCompatPadding = false
            }
            if (isMaterial3Theme) {
                labelBackground.apply {
                    useCompatPadding = false
                    setContentPadding(0, 0, 0, 0)
                    foregroundCompat = null
                    (getChildAt(0) as TextView).apply {
                        setTextAppearanceCompat(
                            context.getResourceIdByAttr(
                                com.google.android.material.R.attr.textAppearanceLabelLarge
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = (super.onSaveInstanceState() as Bundle)
            .getParcelableSafe<Parcelable>("superState")
        return State(superState, isOpen)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        state as State
        super.onRestoreInstanceState(state.superState)
        if (state.isOpen) {
            toggle(false)
        }
    }

    companion object {
        private val VIEW_PROPERTY_BACKGROUND_TINT =
            object : Property<View, Int>(Int::class.java, "backgroundTint") {
                override fun get(view: View): Int? = view.backgroundTintList!!.defaultColor

                override fun set(view: View, value: Int?) {
                    view.backgroundTintList = ColorStateList.valueOf(value!!)
                }
            }

        private val IMAGE_VIEW_PROPERTY_IMAGE_TINT =
            object : Property<ImageView, Int>(Int::class.java, "imageTint") {
                override fun get(view: ImageView): Int? = view.imageTintList!!.defaultColor

                override fun set(view: ImageView, value: Int?) {
                    view.imageTintList = ColorStateList.valueOf(value!!)
                }
            }

        private val DRAWABLE_PROPERTY_LEVEL =
            object : Property<Drawable, Int>(Int::class.java, "level") {
                override fun get(drawable: Drawable): Int? = drawable.level

                override fun set(drawable: Drawable, value: Int?) {
                    drawable.level = value!!
                }
            }
    }

    @Parcelize
    private class State(val superState: Parcelable?, val isOpen: Boolean) : ParcelableState
}
