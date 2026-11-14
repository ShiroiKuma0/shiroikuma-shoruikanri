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
import android.graphics.Typeface
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
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.setTextAppearanceCompat
import me.zhanghai.android.files.skui.SkThemeSlot
import me.zhanghai.android.files.skui.skColor
import me.zhanghai.android.files.skui.skPillFrame
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

    // 白い熊 fork: every opened action item is a pill — an outlined, never filled, fully round
    // frame around its label and its icon together (black-yellow theme: every action is a
    // pill). The items are equals, so each gets the thin frame and normal-weight text.
    private var skPillDrawable: GradientDrawable? = null

    // 0 while closed, 1 while open — fades the pills in and out with the items.
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
        val mainFabDrawable = RotateDrawable().apply {
            drawable = mainFab.drawable
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

    // 白い熊 fork: (re)read the configured slot colors into the pills and their items. Called
    // again whenever the UI page changes a slot.
    fun applySkStyle() {
        skPillDrawable = skPillFrame(
            resources.displayMetrics.density, skColor(SkThemeSlot.FAB_ICON),
            skColor(SkThemeSlot.FAB_BACKGROUND), false
        )
        for (index in 0 until childCount) {
            (getChildAt(index) as? FabWithLabelView)?.applySkItemStyle()
        }
        invalidate()
    }

    // The label and the icon sit inside the pill drawn behind them, so neither keeps a
    // background, a card or a shadow of its own.
    private fun FabWithLabelView.applySkItemStyle() {
        val ink = skColor(SkThemeSlot.FAB_ICON)
        fab.apply {
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ink)
            compatElevation = 0f
        }
        labelBackground.apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            cardElevation = 0f
            useCompatPadding = false
            setContentPadding(0, 0, 0, 0)
            foregroundCompat = null
            (getChildAt(0) as? TextView)?.apply {
                setTextColor(ink)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawSkPills(canvas)

        super.dispatchDraw(canvas)
    }

    // 白い熊 fork: one pill per action item, around the union of its label and its mini button.
    // The item views themselves are wider than their content — the mini fabs carry 20dp side
    // margins — so each pill is measured from the content views, not from the row.
    private fun drawSkPills(canvas: Canvas) {
        val pillDrawable = skPillDrawable ?: return
        if (skFrameFraction <= 0f) {
            return
        }
        val paddingStart = context.dpToDimensionPixelSize(18)
        val paddingEnd = context.dpToDimensionPixelSize(6)
        val paddingVertical = context.dpToDimensionPixelSize(2)
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        pillDrawable.alpha = (255 * skFrameFraction).toInt()
        for (index in 0 until childCount) {
            val itemView = getChildAt(index) as? FabWithLabelView ?: continue
            if (itemView.visibility != VISIBLE) {
                continue
            }
            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            for (contentView in arrayOf<View>(itemView.labelBackground, itemView.fab)) {
                if (contentView.visibility != VISIBLE) {
                    continue
                }
                left = minOf(left, itemView.left + contentView.left)
                top = minOf(top, itemView.top + contentView.top)
                right = maxOf(right, itemView.left + contentView.right)
                bottom = maxOf(bottom, itemView.top + contentView.bottom)
            }
            if (left > right || top > bottom) {
                continue
            }
            // The label is on the start side, the icon on the end side.
            pillDrawable.setBounds(
                left - (if (isRtl) paddingEnd else paddingStart), top - paddingVertical,
                right + (if (isRtl) paddingStart else paddingEnd), bottom + paddingVertical
            )
            pillDrawable.draw(canvas)
        }
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
                // 白い熊 fork: fade the pills in and out along with the items.
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
            // 白い熊 fork: room between the rows for each item's pill, and the whole pill
            // tappable rather than only its label and icon.
            updateLayoutParams<MarginLayoutParams> {
                val verticalMargin = context.dpToDimensionPixelSize(5)
                topMargin = verticalMargin
                bottomMargin = verticalMargin
            }
            setOnClickListener { fab.performClick() }
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
            // After the Material 3 text appearance, which would reset color and weight.
            applySkItemStyle()
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
