/*
 * 白い熊 fork (skui): the pill — the one button look of the black-yellow theme.
 *
 * A pill is fully round and never filled: an accent frame with the background
 * showing through. The main action of a block has a 3 dp frame and bold text,
 * every other action a 1.25 dp frame and normal text; they differ by weight
 * only. Buttons inflated from a theme get the same look from Widget.Sk.Pill
 * in sk_theme.xml — this file is for views built in code.
 */

package me.zhanghai.android.files.skui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView

const val SK_PILL_MAIN_STROKE_DP = 3f
const val SK_PILL_STROKE_DP = 1.25f

fun skPillStrokePx(density: Float, main: Boolean): Int =
    maxOf(1, Math.round((if (main) SK_PILL_MAIN_STROKE_DP else SK_PILL_STROKE_DP) * density))

/** The bare pill outline, without a ripple — for drawing on a canvas. */
fun skPillFrame(density: Float, ink: Int, fill: Int, main: Boolean): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 100 * density
        setColor(fill)
        setStroke(skPillStrokePx(density, main), ink)
    }

/** A pill background for a tappable view: the outline plus an accent ripple inside it. */
fun skPillBackground(
    density: Float,
    ink: Int,
    fill: Int = Color.TRANSPARENT,
    main: Boolean = false
): Drawable {
    // The ripple needs an opaque mask: the frame's own inside is transparent.
    val mask = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 100 * density
        setColor(Color.WHITE)
    }
    return RippleDrawable(
        ColorStateList.valueOf((ink and 0x00FFFFFF) or 0x4D000000),
        skPillFrame(density, ink, fill, main),
        mask
    )
}

/** Turn a text view (or a Button) into a pill that hugs its label. */
fun TextView.applySkPill(
    main: Boolean = false,
    ink: Int = skColor(SkThemeSlot.ACCENT),
    fill: Int = Color.TRANSPARENT
) {
    val density = resources.displayMetrics.density
    isAllCaps = false
    setTextColor(ink)
    typeface = Typeface.create(Typeface.SANS_SERIF, if (main) Typeface.BOLD else Typeface.NORMAL)
    background = skPillBackground(density, ink, fill, main)
    stateListAnimator = null
    minWidth = 0
    minimumWidth = 0
    minHeight = 0
    minimumHeight = 0
    val paddingH = (18 * density).toInt()
    val paddingV = (9 * density).toInt()
    setPadding(paddingH, paddingV, paddingH, paddingV)
}
