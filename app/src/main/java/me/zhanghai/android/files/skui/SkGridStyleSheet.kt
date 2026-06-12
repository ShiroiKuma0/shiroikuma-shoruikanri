/*
 * 白い熊 fork (skui): the per-folder grid style sheet, opened from the button
 * at the right of the address line. A bottom sheet with live controls — the
 * list stays visible above it and restyles as you slide. Values are stored as
 * a per-folder override, so the folder opens with them in any tab; "Reset"
 * reverts the folder to the global defaults from the UI page.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import java8.nio.file.Path
import me.zhanghai.android.files.R

class SkGridStyleSheet(
    private val activity: Activity,
    private val path: Path,
    private val onChanged: () -> Unit
) {
    private val density = activity.resources.displayMetrics.density

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun update(transform: (SkGridStyle) -> SkGridStyle) {
        SkGridStyles.set(path, transform(SkGridStyles.get(path) ?: SkGridStyle()))
        onChanged()
    }

    init {
        val dialog = BottomSheetDialog(activity)
        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = AppCompatResources.getDrawable(activity, R.drawable.sk_dialog_background)
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }

        holder.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.sk_grid_style_title)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(skColor(SkThemeSlot.ACCENT))
                setPadding(0, 0, 0, dp(8))
            }
        )

        val effective = skEffectiveGridStyle(path)

        addSlider(holder, R.string.sk_font_size, 0, 40, effective.textSizeSp, isSp = true) { value ->
            update { it.copy(textSizeSp = value) }
        }
        addSlider(holder, R.string.sk_ui_grid_image_width, 48, 320, effective.imageWidthDp) { value ->
            update { it.copy(imageWidthDp = value) }
        }
        addSlider(holder, R.string.sk_ui_grid_image_height, 48, 320, effective.imageHeightDp) { value ->
            update { it.copy(imageHeightDp = value) }
        }
        addSlider(holder, R.string.sk_ui_grid_padding_h, 0, 32, effective.paddingHDp) { value ->
            update { it.copy(paddingHDp = value) }
        }
        addSlider(holder, R.string.sk_ui_grid_padding_v, 0, 32, effective.paddingVDp) { value ->
            update { it.copy(paddingVDp = value) }
        }
        addSlider(holder, R.string.sk_ui_grid_text_gap, 0, 24, effective.textGapDp) { value ->
            update { it.copy(textGapDp = value) }
        }
        addSwitch(holder, R.string.sk_ui_grid_text_overlay, effective.isTextOverlay) { checked ->
            update { it.copy(textOverlay = if (checked) 1 else 0) }
        }
        addSwitch(holder, R.string.sk_ui_grid_text_show, effective.isTextVisible) { checked ->
            update { it.copy(textVisible = if (checked) 1 else 0) }
        }

        holder.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.sk_grid_style_reset)
                textSize = 15f
                setTextColor(skColor(SkThemeSlot.ACCENT))
                gravity = Gravity.END
                setPadding(dp(8), dp(12), dp(8), dp(4))
                setOnClickListener {
                    SkGridStyles.set(path, null)
                    onChanged()
                    dialog.dismiss()
                }
            }
        )

        dialog.setContentView(holder)
        // Let our bordered background show instead of the sheet's own.
        (holder.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        dialog.show()
    }

    private fun addSlider(
        holder: LinearLayout,
        @StringRes labelRes: Int,
        min: Int,
        max: Int,
        value: Int,
        isSp: Boolean = false,
        onChange: (Int) -> Unit
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        fun format(v: Int): String =
            when {
                isSp && v <= 0 -> activity.getString(R.string.sk_font_size_default)
                isSp -> activity.getString(R.string.sk_font_size_sp_format, v)
                else -> activity.getString(R.string.sk_ui_dp_format, v)
            }
        row.addView(
            TextView(activity).apply {
                text = activity.getString(labelRes)
                textSize = 14f
                setTextColor(skColor(SkThemeSlot.TEXT))
                layoutParams =
                    LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        )
        val valueView = TextView(activity).apply {
            textSize = 13f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
            text = format(value)
            layoutParams = LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.END
        }
        val seekBar = SeekBar(activity).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(seekBar)
        row.addView(valueView)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                val newValue = progress + min
                valueView.text = format(newValue)
                onChange(newValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        holder.addView(row)
    }

    private fun addSwitch(
        holder: LinearLayout,
        @StringRes labelRes: Int,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(
            TextView(activity).apply {
                text = activity.getString(labelRes)
                textSize = 14f
                setTextColor(skColor(SkThemeSlot.TEXT))
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        val switch = SwitchCompat(activity).apply { isChecked = checked }
        switch.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        row.addView(switch)
        row.setOnClickListener { switch.toggle() }
        holder.addView(row)
    }
}
