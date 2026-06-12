/*
 * 白い熊 fork (skui): helpers for applying the configured slot colors and fonts
 * to real views (toolbars, text views), used by the wired-up surfaces.
 */

package me.zhanghai.android.files.skui

import android.util.TypedValue
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children

/** Apply a slot's font family / weight / size, leaving the view's colors alone. */
fun TextView.applySkFontOnly(slot: SkThemeSlot) {
    typeface = skTypeface(SkUi.getFontFamily(slot.key), SkUi.getFontWeight(slot.key))
    val sizeSp = SkUi.getFontSize(slot.key)
    if (sizeSp > 0) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
    }
}

/**
 * Apply background, title/subtitle color + font and icon tints to a toolbar.
 * The title/subtitle text views are looked up after layout since Toolbar
 * creates them lazily.
 */
fun Toolbar.applySkChrome(
    backgroundSlot: SkThemeSlot,
    titleSlot: SkThemeSlot,
    subtitleSlot: SkThemeSlot?,
    iconsSlot: SkThemeSlot
) {
    setBackgroundColor(skColor(backgroundSlot))
    setTitleTextColor(skColor(titleSlot))
    subtitleSlot?.let { setSubtitleTextColor(skColor(it)) }
    val iconColor = skColor(iconsSlot)
    navigationIcon?.setTint(iconColor)
    overflowIcon?.setTint(iconColor)
    menu?.let { menu ->
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon?.setTint(iconColor)
        }
    }
    post {
        val titleText = title?.toString()
        val subtitleText = subtitle?.toString()
        children.filterIsInstance<TextView>().forEach { textView ->
            when (textView.text?.toString()) {
                titleText -> textView.applySkFontOnly(titleSlot)
                subtitleText -> subtitleSlot?.let { textView.applySkFontOnly(it) }
            }
        }
    }
}
