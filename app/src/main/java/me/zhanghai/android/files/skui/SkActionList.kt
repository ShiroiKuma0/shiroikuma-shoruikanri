/*
 * 白い熊 fork (skui): a list of actions in a dialog, drawn like the rows of a menu — every row
 * a full-width outlined pill with its icon at the left, coloured by what the row does, and a ›
 * at the right when it opens something. Replaces AlertDialog's plain setItems() list, whose
 * lines look like text.
 */

package me.zhanghai.android.files.skui

import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.CompoundButtonCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

class SkAction(
    val label: CharSequence,
    val icon: Int,
    val role: SkIconRole,
    val opens: Boolean = false,
    val run: () -> Unit
)

/** Show the dialog with the actions as its content; a tap runs the action and closes it. */
fun MaterialAlertDialogBuilder.showActions(actions: List<SkAction>): AlertDialog =
    createWithActions(actions).also { it.show() }

/** The dialog with the actions as its content, not shown yet (for a dialog fragment). */
fun MaterialAlertDialogBuilder.createWithActions(actions: List<SkAction>): AlertDialog {
    val context = context
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = Math.round(value * density)
    val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(4))
    }
    lateinit var dialog: AlertDialog
    for (action in actions) {
        val destructive = action.role == SkIconRole.DELETE
        val ink = skColor(if (destructive) SkThemeSlot.DANGER else SkThemeSlot.MENU_LABEL)
        val frame = skColor(if (destructive) SkThemeSlot.DANGER else SkThemeSlot.MENU_ROW_FRAME)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = skPillBackground(density, frame)
            setOnClickListener {
                dialog.dismiss()
                action.run()
            }
        }
        row.addView(
            ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, action.icon))
                imageTintList = ColorStateList.valueOf(skColor(action.role))
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) }
        )
        row.addView(
            TextView(context).apply {
                text = action.label
                textSize = 16f
                setTextColor(ink)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (action.opens) {
            row.addView(
                ImageView(context).apply {
                    setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.sk_ic_chevron)
                    )
                    imageTintList = ColorStateList.valueOf(ink)
                }
            )
        }
        list.addView(
            row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
        )
    }
    setView(NestedScrollView(context).apply { addView(list) })
    dialog = create()
    return dialog
}

/**
 * A check box or radio button built in code: a selection mark in its own two roles. Inflated
 * ones get the same from SkViewInflater.
 */
fun CompoundButton.applySkMark() {
    if (!SkUi.isSkThemeEnabled) {
        return
    }
    CompoundButtonCompat.setButtonTintList(
        this, ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(skColor(SkThemeSlot.MARK_ON), skColor(SkThemeSlot.MARK_OFF))
        )
    )
}
