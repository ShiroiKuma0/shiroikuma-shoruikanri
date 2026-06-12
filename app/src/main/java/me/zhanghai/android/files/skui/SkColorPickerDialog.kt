/*
 * 白い熊 fork (skui): color picker — a grid of material swatches plus a hex
 * field for arbitrary colors; the neutral button reverts the slot to its
 * inherited 白い熊 default.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.toColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

private val SWATCH_COLORS =
    intArrayOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFFFFEB3B.toInt(),
        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
        0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
        0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
        0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(), 0xFF795548.toInt(),
        0xFF9E9E9E.toInt(), 0xFF607D8B.toInt()
    )

class SkColorPickerDialog(
    private val activity: Activity,
    initialColor: Int,
    private val onResult: (color: Int?) -> Unit // null = revert to default
) {
    init {
        val density = activity.resources.displayMetrics.density
        val swatchSize = (40 * density).toInt()
        val swatchMargin = (6 * density).toInt()
        val padding = (20 * density).toInt()

        val hexEdit = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = activity.getString(R.string.sk_color_hex_hint)
            setText(String.format("#%08X", initialColor))
        }

        val grid = GridLayout(activity).apply { columnCount = 6 }
        SWATCH_COLORS.forEach { color ->
            val swatch = android.view.View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((1 * density).toInt(), 0x66888888)
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = swatchSize
                    height = swatchSize
                    setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin)
                }
                setOnClickListener { hexEdit.setText(String.format("#%08X", color)) }
            }
            grid.addView(swatch)
        }

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(grid)
            addView(hexEdit)
        }

        MaterialAlertDialogBuilder(activity)
            .setView(ScrollView(activity).apply { addView(holder) })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                parseColor(hexEdit.text.toString())?.let { onResult(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.sk_color_default) { _, _ -> onResult(null) }
            .show()
    }

    private fun parseColor(text: String): Int? =
        try {
            val trimmed = text.trim().let { if (it.startsWith("#")) it else "#$it" }
            trimmed.toColorInt()
        } catch (e: IllegalArgumentException) {
            null
        }
}
