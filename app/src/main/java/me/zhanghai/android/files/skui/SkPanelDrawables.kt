/*
 * 白い熊 fork (skui): the three shapes the platform reads from the theme before any of the
 * fork's code can hand it a drawable — the panel of a dialog it builds itself, the panel of a
 * popup menu, and the divider of a list. From Android 7 on a drawable XML may name a class
 * (drawable-v24/sk_*.xml do), so these draw themselves from their roles and no colour is
 * written in XML. On Android 6 the XML shapes in drawable/ stand in, naming the role colour
 * resources.
 */

package me.zhanghai.android.files.skui

import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import androidx.annotation.Keep

private val density: Float
    get() = Resources.getSystem().displayMetrics.density

private fun GradientDrawable.panel(ground: Int, frame: Int, radiusDp: Float) {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radiusDp * density
    setColor(ground)
    setStroke(maxOf(1, Math.round(density)), frame)
}

@Keep
class SkDialogPanelDrawable : GradientDrawable() {
    init {
        panel(skColor(SkThemeSlot.DIALOG_GROUND), skColor(SkThemeSlot.DIALOG_FRAME), 12f)
    }
}

@Keep
class SkMenuPanelDrawable : GradientDrawable() {
    init {
        panel(skColor(SkThemeSlot.MENU_GROUND), skColor(SkThemeSlot.MENU_FRAME), 8f)
    }
}

@Keep
class SkListDividerDrawable : GradientDrawable() {
    init {
        shape = GradientDrawable.RECTANGLE
        setColor(skColor(SkThemeSlot.LIST_DIVIDER))
        setSize(-1, maxOf(1, Math.round(density)))
    }
}
