/*
 * 白い熊 fork (skui): the 白い熊 書類管理 UI page — every customizable aspect of
 * the UI laid out as a section > subgroup > controls cascade, each cascade level
 * indented one more step (the sister-repo convention). All controls write
 * immediately; the main screen re-applies styling in onResume.
 */

package me.zhanghai.android.files.skui

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.databinding.SkUiActivityBinding
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat

private const val INDENT_STEP_DP = 72
private const val MAX_FONT_SIZE_SP = 40

class SkUiActivity : AppActivity() {
    private lateinit var binding: SkUiActivityBinding
    private var stepPx = 0
    private var densityPx = 1f

    private var pendingFontSlot: SkThemeSlot? = null

    private val fontImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onFontImported(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SkUiActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.sk_ui_title)
    }

    override fun onResume() {
        super.onResume()

        buildRows()
    }

    private fun buildRows() {
        densityPx = resources.displayMetrics.density
        stepPx = (INDENT_STEP_DP * densityPx).toInt()
        binding.holder.removeAllViews()

        window.setBackgroundDrawable(ColorDrawable(skColor(SkThemeSlot.BACKGROUND)))
        binding.toolbar.setBackgroundColor(skColor(SkThemeSlot.TOOLBAR_BACKGROUND))
        binding.toolbar.setTitleTextColor(skColor(SkThemeSlot.TOOLBAR_TITLE))
        binding.toolbar.navigationIcon?.setTint(skColor(SkThemeSlot.TOOLBAR_ICONS))

        // Foundation — the colors everything else inherits from
        addSection(R.string.sk_ui_group_foundation)
        addSwitchRow(R.string.sk_ui_theme_enabled, SkUi.isSkThemeEnabled, stepPx) {
            SkUi.isSkThemeEnabled = it
            recreate()
        }
        SkThemeSlot.entries.filter { it.group == SkThemeGroup.FOUNDATION }.forEach {
            addColorRow(it, stepPx)
        }

        // Toolbar
        addSection(R.string.sk_ui_group_toolbar)
        addColorRow(SkThemeSlot.TOOLBAR_BACKGROUND, stepPx)
        addTextRow(SkThemeSlot.TOOLBAR_TITLE, stepPx)
        addTextRow(SkThemeSlot.TOOLBAR_SUBTITLE, stepPx)
        addColorRow(SkThemeSlot.TOOLBAR_ICONS, stepPx)

        // Breadcrumbs
        addSection(R.string.sk_ui_group_breadcrumbs)
        addTextRow(SkThemeSlot.BREADCRUMB_SELECTED, stepPx)
        addColorRow(SkThemeSlot.BREADCRUMB_UNSELECTED, stepPx)
        addColorRow(SkThemeSlot.BREADCRUMB_ARROWS, stepPx)

        // File list
        addSection(R.string.sk_ui_group_file_list)
        val updateFileListPreview = addFileListPreview(stepPx)
        addSubgroup(R.string.sk_ui_subgroup_text)
        addTextRow(SkThemeSlot.FILE_NAME, stepPx * 2)
        addTextRow(SkThemeSlot.FILE_DESCRIPTION, stepPx * 2)
        addSubgroup(R.string.sk_ui_subgroup_icons)
        addColorRow(SkThemeSlot.FILE_ICONS, stepPx * 2)
        addSliderRow(
            R.string.sk_ui_file_icon_size, 16, 64, SkUi.fileIconSizeDp, stepPx * 2
        ) {
            SkUi.fileIconSizeDp = it
            updateFileListPreview()
        }
        addSubgroup(R.string.sk_ui_subgroup_grid)
        val updateGridPreview = addGridPreview(stepPx * 2)
        addTextRow(SkThemeSlot.GRID_TEXT, stepPx * 2)
        addSliderRow(
            R.string.sk_ui_grid_image_width, 48, 320, SkUi.gridImageWidthDp, stepPx * 2
        ) {
            SkUi.gridImageWidthDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_image_height, 48, 320, SkUi.gridImageHeightDp, stepPx * 2
        ) {
            SkUi.gridImageHeightDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_padding_h, 0, 32, SkUi.gridPaddingHDp, stepPx * 2
        ) {
            SkUi.gridPaddingHDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_padding_v, 0, 32, SkUi.gridPaddingVDp, stepPx * 2
        ) {
            SkUi.gridPaddingVDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_text_gap, 0, 24, SkUi.gridTextGapDp, stepPx * 2
        ) {
            SkUi.gridTextGapDp = it
            updateGridPreview()
        }
        addSwitchRow(
            R.string.sk_ui_grid_text_overlay, SkUi.isGridTextOverlay, stepPx * 2
        ) {
            SkUi.isGridTextOverlay = it
            updateGridPreview()
        }
        addSwitchRow(
            R.string.sk_ui_grid_text_show, SkUi.isGridTextVisible, stepPx * 2
        ) {
            SkUi.isGridTextVisible = it
            updateGridPreview()
        }
        addSubgroup(R.string.sk_ui_subgroup_options)
        addSliderRow(
            R.string.sk_ui_file_padding, 0, 24, SkUi.filePaddingDp, stepPx * 2
        ) {
            SkUi.filePaddingDp = it
            updateFileListPreview()
        }
        addSwitchRow(
            R.string.settings_file_list_animation_title,
            Settings.FILE_LIST_ANIMATION.valueCompat,
            stepPx * 2
        ) { Settings.FILE_LIST_ANIMATION.putValue(it) }
        addValueRow(
            R.string.settings_file_name_ellipsize_title,
            ellipsizeLabel(Settings.FILE_NAME_ELLIPSIZE.valueCompat),
            stepPx * 2
        ) { valueView ->
            val entries = resources.getStringArray(R.array.settings_file_name_ellipsize_entries)
            SkMaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_file_name_ellipsize_title)
                .setSingleChoiceItems(
                    entries, Settings.FILE_NAME_ELLIPSIZE.valueCompat.ordinal
                ) { dialog, which ->
                    Settings.FILE_NAME_ELLIPSIZE.putValue(TextUtils.TruncateAt.entries[which])
                    valueView.text = ellipsizeLabel(Settings.FILE_NAME_ELLIPSIZE.valueCompat)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Navigation drawer
        addSection(R.string.sk_ui_group_drawer)
        addColorRow(SkThemeSlot.DRAWER_BACKGROUND, stepPx)
        addTextRow(SkThemeSlot.DRAWER_ITEM, stepPx)
        addColorRow(SkThemeSlot.DRAWER_ICONS, stepPx)

        // Tab bar
        addSection(R.string.sk_ui_group_tabs)
        addColorRow(SkThemeSlot.TAB_BACKGROUND, stepPx)
        addTextRow(SkThemeSlot.TAB_SELECTED, stepPx)
        addColorRow(SkThemeSlot.TAB_UNSELECTED, stepPx)
        addColorRow(SkThemeSlot.TAB_BUTTONS, stepPx)

        // Bottom bar
        addSection(R.string.sk_ui_group_bottom_bar)
        addColorRow(SkThemeSlot.BOTTOM_BAR_BACKGROUND, stepPx)
        addTextRow(SkThemeSlot.BOTTOM_BAR_TEXT, stepPx)
        addColorRow(SkThemeSlot.BOTTOM_BAR_ICONS, stepPx)

        // Speed dial
        addSection(R.string.sk_ui_group_speed_dial)
        addColorRow(SkThemeSlot.FAB_BACKGROUND, stepPx)
        addColorRow(SkThemeSlot.FAB_ICON, stepPx)
    }

    private fun ellipsizeLabel(value: TextUtils.TruncateAt): String =
        resources.getStringArray(R.array.settings_file_name_ellipsize_entries)
            .getOrNull(value.ordinal) ?: value.name

    // --- Row builders ---

    private fun dp(value: Int): Int = (value * densityPx).toInt()

    private fun addSection(@StringRes labelRes: Int) {
        val accent = skColor(SkThemeSlot.ACCENT)
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(6))
        }
        holder.addView(
            TextView(this).apply {
                text = getString(labelRes)
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
            }
        )
        holder.addView(
            View(this).apply {
                setBackgroundColor(accent)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
                ).apply { topMargin = dp(6) }
            }
        )
        binding.holder.addView(holder)
    }

    private fun addSubgroup(@StringRes labelRes: Int) {
        val accent = skColor(SkThemeSlot.ACCENT)
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(stepPx, dp(12), dp(16), dp(4))
        }
        holder.addView(
            TextView(this).apply {
                text = getString(labelRes)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
            }
        )
        holder.addView(
            View(this).apply {
                setBackgroundColor(accent)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(2)
                ).apply { topMargin = dp(4) }
            }
        )
        binding.holder.addView(holder)
    }

    private fun makeRow(indent: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPaddingRelative(dp(16) + indent, dp(4), dp(16), dp(4))
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

    private fun makeLabel(@StringRes labelRes: Int): TextView =
        TextView(this).apply {
            text = getString(labelRes)
            textSize = 16f
            setTextColor(skColor(SkThemeSlot.TEXT))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    private fun makeSwatch(color: Int): View =
        View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(1), 0x66888888)
            }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }

    private fun addColorRow(slot: SkThemeSlot, indent: Int) {
        val row = makeRow(indent)
        row.addView(makeLabel(slot.labelRes))
        row.addView(makeSwatch(skColor(slot)))
        row.setOnClickListener {
            SkColorPickerDialog(this, skColor(slot)) { color ->
                if (color != null) setSkColor(slot, color) else resetSkColor(slot)
                buildRows()
            }
        }
        binding.holder.addView(row)
    }

    private fun addValueRow(
        @StringRes labelRes: Int,
        value: String,
        indent: Int,
        onClick: (TextView) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabel(labelRes))
        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
        }
        row.addView(valueView)
        row.setOnClickListener { onClick(valueView) }
        binding.holder.addView(row)
    }

    private fun addSwitchRow(
        @StringRes labelRes: Int,
        checked: Boolean,
        indent: Int,
        onToggle: (Boolean) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabel(labelRes))
        val switch = SwitchCompat(this).apply { isChecked = checked }
        row.addView(switch)
        switch.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        row.setOnClickListener { switch.toggle() }
        binding.holder.addView(row)
    }

    // A concrete text element: its color, font family, weight, size and a live sample of all four.
    private fun addTextRow(slot: SkThemeSlot, indent: Int) {
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val colorRow = makeRow(indent)
        colorRow.addView(makeLabel(slot.labelRes))
        colorRow.addView(makeSwatch(skColor(slot)))
        colorRow.setOnClickListener {
            SkColorPickerDialog(this, skColor(slot)) { color ->
                if (color != null) setSkColor(slot, color) else resetSkColor(slot)
                buildRows()
            }
        }
        holder.addView(colorRow)

        val subIndent = indent + stepPx

        // Font family
        val fontRow = makeRow(subIndent)
        fontRow.addView(makeLabel(R.string.sk_font))
        val fontValue = TextView(this).apply {
            text = skFontDisplayName(SkUi.getFontFamily(slot.key))
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
        }
        fontRow.addView(fontValue)
        fontRow.setOnClickListener {
            SkFontPickerDialog(
                this,
                onAddFont = {
                    pendingFontSlot = slot
                    fontImportLauncher.launch(arrayOf("*/*"))
                },
                onPick = { fileName ->
                    SkUi.setFontFamily(slot.key, fileName)
                    buildRows()
                }
            )
        }
        holder.addView(fontRow)

        // Font weight
        val weightRow = makeRow(subIndent)
        weightRow.addView(makeLabel(R.string.sk_font_weight))
        val weightValue = TextView(this).apply {
            text = getString(SkFontWeight.fromValue(SkUi.getFontWeight(slot.key)).labelRes)
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
        }
        weightRow.addView(weightValue)
        weightRow.setOnClickListener {
            val entries = SkFontWeight.entries
            val labels = entries.map { getString(it.labelRes) }.toTypedArray()
            val checked = entries.indexOf(SkFontWeight.fromValue(SkUi.getFontWeight(slot.key)))
            SkMaterialAlertDialogBuilder(this)
                .setTitle(R.string.sk_font_weight)
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    SkUi.setFontWeight(slot.key, entries[which].value)
                    dialog.dismiss()
                    buildRows()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        holder.addView(weightRow)

        // Font size + live sample
        val sample = TextView(this).apply {
            setPaddingRelative(dp(16) + subIndent, dp(2), dp(16), dp(10))
        }

        val sizeRow = makeRow(subIndent)
        sizeRow.addView(makeLabel(R.string.sk_font_size))
        val sizeValue = TextView(this).apply {
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
            text = sizeLabel(SkUi.getFontSize(slot.key))
        }
        val seekBar = SeekBar(this).apply {
            max = MAX_FONT_SIZE_SP
            progress = SkUi.getFontSize(slot.key)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        // label keeps natural width; the seek bar takes the rest
        (sizeRow.getChildAt(0) as TextView).layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        sizeRow.addView(seekBar)
        sizeRow.addView(sizeValue)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                SkUi.setFontSize(slot.key, progress)
                sizeValue.text = sizeLabel(progress)
                refreshSample(sample, slot)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        holder.addView(sizeRow)

        refreshSample(sample, slot)
        holder.addView(sample)

        binding.holder.addView(holder)
    }

    // A generic labeled slider row; values are in dp.
    private fun addSliderRow(
        @StringRes labelRes: Int,
        min: Int,
        max: Int,
        value: Int,
        indent: Int,
        onChange: (Int) -> Unit
    ) {
        val row = makeRow(indent)
        val label = makeLabel(labelRes)
        label.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        row.addView(label)
        val valueView = TextView(this).apply {
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
            text = getString(R.string.sk_ui_dp_format, value)
        }
        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(seekBar)
        row.addView(valueView)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val newValue = progress + min
                valueView.text = getString(R.string.sk_ui_dp_format, newValue)
                onChange(newValue)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.holder.addView(row)
    }

    // A live preview of a file row: icon size, padding, fonts and colors.
    private fun addFileListPreview(indent: Int): () -> Unit {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(16) + indent, dp(4), dp(16), dp(4))
        }
        val icon = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.directory_icon_white_24dp)
        }
        row.addView(icon)
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16), 0, 0, 0)
        }
        val nameView = TextView(this).apply {
            text = getString(R.string.sk_ui_preview_file_name)
        }
        val descriptionView = TextView(this).apply {
            text = getString(R.string.sk_ui_preview_file_description)
        }
        textColumn.addView(nameView)
        textColumn.addView(descriptionView)
        row.addView(textColumn)
        binding.holder.addView(row)

        val update = {
            val iconSizePx = dp(SkUi.fileIconSizeDp)
            icon.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
            icon.imageTintList =
                android.content.res.ColorStateList.valueOf(skColor(SkThemeSlot.FILE_ICONS))
            val paddingPx = dp(SkUi.filePaddingDp)
            row.setPaddingRelative(
                dp(16) + indent, dp(4) + paddingPx, dp(16), dp(4) + paddingPx
            )
            nameView.applySkSlot(SkThemeSlot.FILE_NAME)
            descriptionView.applySkSlot(SkThemeSlot.FILE_DESCRIPTION)
        }
        update()
        return update
    }

    // A live preview of a grid cell: image box size, paddings, text gap/overlay
    // and the grid text itself.
    private fun addGridPreview(indent: Int): () -> Unit {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16) + indent, dp(4), dp(16), dp(4))
        }
        val imageBox = View(this)
        val nameView = TextView(this).apply {
            text = getString(R.string.sk_ui_preview_file_name)
            gravity = Gravity.CENTER
        }
        cell.addView(imageBox)
        cell.addView(nameView)
        binding.holder.addView(cell)

        val update = {
            val paddingHPx = dp(SkUi.gridPaddingHDp)
            val paddingVPx = dp(SkUi.gridPaddingVDp)
            imageBox.layoutParams = LinearLayout.LayoutParams(
                dp(SkUi.gridImageWidthDp), dp(SkUi.gridImageHeightDp)
            ).apply {
                leftMargin = paddingHPx
                rightMargin = paddingHPx
                topMargin = paddingVPx
            }
            imageBox.background = GradientDrawable().apply {
                setColor(0x22FFFF00)
                setStroke(dp(1), skColor(SkThemeSlot.ACCENT))
                cornerRadius = dp(4).toFloat()
            }
            nameView.isVisible = SkUi.isGridTextVisible
            nameView.applySkSlot(SkThemeSlot.GRID_TEXT)
            nameView.layoutParams = LinearLayout.LayoutParams(
                dp(SkUi.gridImageWidthDp) + 2 * paddingHPx,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = paddingVPx
                topMargin = if (SkUi.isGridTextOverlay) {
                    -dp(28)
                } else {
                    dp(SkUi.gridTextGapDp)
                }
            }
        }
        update()
        return update
    }

    private fun refreshSample(sample: TextView, slot: SkThemeSlot) {
        sample.showSkFontSample(
            SkUi.getFontFamily(slot.key),
            SkUi.getFontWeight(slot.key),
            SkUi.getFontSize(slot.key),
            skColor(slot)
        )
    }

    private fun sizeLabel(sp: Int): String =
        if (sp > 0) getString(R.string.sk_font_size_sp_format, sp)
        else getString(R.string.sk_font_size_default)

    private fun onFontImported(uri: Uri?) {
        val slot = pendingFontSlot
        pendingFontSlot = null
        if (uri == null || slot == null) {
            return
        }
        val fileName = skImportFont(uri)
        if (fileName == null) {
            showToast(R.string.sk_font_invalid)
            return
        }
        SkUi.setFontFamily(slot.key, fileName)
        buildRows()
    }
}
