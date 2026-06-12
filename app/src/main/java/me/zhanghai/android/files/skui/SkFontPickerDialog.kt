/*
 * 白い熊 fork (skui): font picker — lists every available font with its name
 * drawn in its own typeface, plus an "Add font…" action for importing .ttf/.otf
 * files. Ported from the sister repos.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

class SkFontPickerDialog(
    private val activity: Activity,
    private val onAddFont: () -> Unit,
    private val onPick: (fileName: String) -> Unit
) {
    private var dialog: AlertDialog? = null

    init {
        val density = activity.resources.displayMetrics.density
        val paddingH = (24 * density).toInt()
        val paddingV = (14 * density).toInt()

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        fun addRow(label: String, typefaceFileName: String?, color: Int, onClick: () -> Unit) {
            holder.addView(
                TextView(activity).apply {
                    text = label
                    textSize = 18f
                    setTextColor(color)
                    typefaceFileName?.let { typeface = skFontTypeface(it) }
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(paddingH, paddingV, paddingH, paddingV)
                    setBackgroundResource(
                        android.R.drawable.list_selector_background
                    )
                    setOnClickListener {
                        dialog?.dismiss()
                        onClick()
                    }
                }
            )
        }

        activity.skAvailableFontOptions().forEach { option ->
            addRow(option.displayName, option.fileName, skColor(SkThemeSlot.TEXT)) {
                onPick(option.fileName)
            }
        }
        addRow(activity.getString(R.string.sk_font_add), null, skColor(SkThemeSlot.ACCENT)) {
            onAddFont()
        }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sk_font)
            .setView(ScrollView(activity).apply { addView(holder) })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
