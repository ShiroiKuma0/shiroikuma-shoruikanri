/*
 * 白い熊 fork (skui): the one place every inflated button, switch, check box and radio
 * button passes through.
 *
 * Named by `viewInflaterClass` in ThemeOverlay.Sk, so AppCompat hands it every view a layout
 * declares — upstream's screens, dialogs and preference rows included, with no layout or call
 * site touched. What it does:
 *  - a button becomes a pill drawn from its roles (frame, label, ground). The positive button
 *    of a dialog is the main pill, 3 dp and bold; in a dialog that deletes it takes the danger
 *    role. A button that shows only an icon stays a glyph (白い熊, 2026-11-22);
 *  - a switch becomes the outlined SkSwitch, whichever switch class the layout names;
 *  - check boxes and radio buttons stay selection marks (白い熊, 2026-11-22), in their own
 *    two roles.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.widget.CompoundButtonCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.theme.MaterialComponentsViewInflater
import me.zhanghai.android.files.R

@Keep
class SkViewInflater : MaterialComponentsViewInflater() {
    override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
        super.createButton(context, attrs).also { button ->
            // Its id and its text are only known once it is attached.
            button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    stylePill(button)
                }

                override fun onViewDetachedFromWindow(view: View) {}
            })
        }

    override fun createView(context: Context, name: String, attrs: AttributeSet): View? =
        when (name) {
            in SWITCH_CLASSES -> SkSwitch(context, attrs)
            else -> super.createView(context, name, attrs)
        }

    override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton =
        super.createRadioButton(context, attrs).also {
            CompoundButtonCompat.setButtonTintList(it, marks())
        }

    override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox =
        super.createCheckBox(context, attrs).also {
            CompoundButtonCompat.setButtonTintList(it, marks())
        }

    private fun marks() = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled, android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        ),
        intArrayOf(
            skDisabled(skColor(SkThemeSlot.MARK_ON)),
            skDisabled(skColor(SkThemeSlot.MARK_OFF)),
            skColor(SkThemeSlot.MARK_ON),
            skColor(SkThemeSlot.MARK_OFF)
        )
    )

    companion object {
        private val SWITCH_CLASSES = setOf(
            "androidx.appcompat.widget.SwitchCompat",
            "com.google.android.material.switchmaterial.SwitchMaterial",
            "com.google.android.material.materialswitch.MaterialSwitch",
            "Switch"
        )

        /** Draw a button as the pill its place calls for. Safe to call again. */
        fun stylePill(button: AppCompatButton) {
            if (button !is MaterialButton) {
                return
            }
            // An icon without a label is a glyph, not a pill.
            if (button.text.isNullOrEmpty() && button.icon != null) {
                return
            }
            val context = button.context
            val isMain = button.id == android.R.id.button1
            // The confirm button of a dialog that deletes, and any button that says so itself.
            val label = button.text?.toString()
            val isDestructive = (isMain && isDestructiveDialog(context))
                || label == context.getString(R.string.delete)
                || label == context.getString(R.string.remove)
            val text = skColor(if (isDestructive) SkThemeSlot.DANGER else SkThemeSlot.PILL_TEXT)
            val frame = skColor(if (isDestructive) SkThemeSlot.DANGER else SkThemeSlot.PILL_FRAME)
            val density = context.resources.displayMetrics.density
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
            button.setTextColor(ColorStateList(states, intArrayOf(skDisabled(text), text)))
            button.strokeColor = ColorStateList(states, intArrayOf(skDisabled(frame), frame))
            button.iconTint = ColorStateList(states, intArrayOf(skDisabled(text), text))
            button.backgroundTintList = ColorStateList.valueOf(skColor(SkThemeSlot.PILL_GROUND))
            button.rippleColor = ColorStateList.valueOf(
                (frame and 0x00FFFFFF) or 0x4D000000
            )
            button.cornerRadius = (100 * density).toInt()
            button.isAllCaps = false
            if (isMain) {
                button.strokeWidth = skPillStrokePx(density, true)
                button.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else if (button.strokeWidth == 0) {
                // A button whose style gave it no frame (a text button): a regular pill.
                button.strokeWidth = skPillStrokePx(density, false)
            }
        }

        private fun isDestructiveDialog(context: Context): Boolean {
            val attributes = context.obtainStyledAttributes(intArrayOf(R.attr.skDestructiveDialog))
            return try {
                attributes.getBoolean(0, false)
            } finally {
                attributes.recycle()
            }
        }
    }
}
