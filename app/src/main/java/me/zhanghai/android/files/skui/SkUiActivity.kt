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
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.databinding.SkUiActivityBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.FileViewType
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.primaryText
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat

// The kxkb indent ladder: section heading 36, sub-heading 54, level-1 rows 72,
// level-2 rows 90 — one 18dp step per cascade level.
private const val HEADING_INDENT_DP = 36
private const val SUBHEADING_INDENT_DP = 54
private const val ROW_INDENT_DP = 72
private const val ROW_SUB_INDENT_DP = 90
private const val INDENT_STEP_DP = 18
private const val MAX_FONT_SIZE_SP = 40
private const val WARN_COLOR = 0xFFFF5252.toInt()

class SkUiActivity : AppActivity() {
    private lateinit var binding: SkUiActivityBinding
    private var rowL1Px = 0
    private var rowL2Px = 0
    private var densityPx = 1f

    private var pendingFontSlot: SkThemeSlot? = null

    private val fontImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onFontImported(uri)
        }

    private var eximportSheet: SkEximportSheet? = null

    private val eximportDirLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract()) { path ->
            path?.let { eximportSheet?.onDirectoryPicked(it) }
        }

    private val eximportFileLauncher =
        registerForActivityResult(FileListActivity.OpenFileContract()) { path ->
            path?.let { eximportSheet?.onImportFilePicked(it) }
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
        rowL1Px = dp(ROW_INDENT_DP)
        rowL2Px = dp(ROW_SUB_INDENT_DP)
        binding.holder.removeAllViews()

        window.setBackgroundDrawable(ColorDrawable(skColor(SkThemeSlot.BACKGROUND)))
        binding.toolbar.setBackgroundColor(skColor(SkThemeSlot.TOOLBAR_BACKGROUND))
        binding.toolbar.setTitleTextColor(skColor(SkThemeSlot.TOOLBAR_TITLE))
        binding.toolbar.navigationIcon?.setTint(skColor(SkThemeSlot.TOOLBAR_ICONS))

        // Export / Import — carry every setting between installs (the Kōjiki
        // flow); the row's value is the latest export found in the export
        // directory, queried on every page open.
        addSection(R.string.sk_eximport_title)
        val (eximportStatus, eximportWarn) = eximportPageStatus()
        addValueRow(
            R.string.sk_eximport_open, eximportStatus, rowL1Px,
            if (eximportWarn) WARN_COLOR else null
        ) { openEximportSheet() }
        // …and, right below the export rows, the automation gate: the same
        // export run headlessly by a sister app's 保存復元 task, plus (contract
        // v2) the data door 応用管理 uses to back this app up with its data.
        // Three rows, in the order every sister app shows them.
        addSwitchRow(
            R.string.sk_automation_enabled, SkAutomation.isEnabled, rowL1Px,
            R.string.sk_automation_enabled_desc
        ) { SkAutomation.isEnabled = it }
        addSwitchRow(
            R.string.sk_automation_require_token, SkAutomation.isTokenRequired, rowL1Px,
            R.string.sk_automation_require_token_desc
        ) {
            SkAutomation.isTokenRequired = it
            buildRows()
        }
        // Hidden while the token is not being asked for: a 48-character secret sitting under an
        // off switch invites 白い熊 to paste it somewhere it will do nothing.
        if (SkAutomation.isTokenRequired) {
            addAutomationTokenRow(rowL1Px)
        }

        // Foundation — the colors everything else inherits from
        addSection(R.string.sk_ui_group_foundation)
        addSwitchRow(R.string.sk_ui_theme_enabled, SkUi.isSkThemeEnabled, rowL1Px) {
            SkUi.isSkThemeEnabled = it
            recreate()
        }
        SkThemeSlot.entries.filter { it.group == SkThemeGroup.FOUNDATION }.forEach {
            addColorRow(it, rowL1Px)
        }

        // Toolbar
        addSection(R.string.sk_ui_group_toolbar)
        addColorRow(SkThemeSlot.TOOLBAR_BACKGROUND, rowL1Px)
        addTextRow(SkThemeSlot.TOOLBAR_TITLE, rowL1Px)
        addTextRow(SkThemeSlot.TOOLBAR_SUBTITLE, rowL1Px)
        addColorRow(SkThemeSlot.TOOLBAR_ICONS, rowL1Px)

        // Breadcrumbs
        addSection(R.string.sk_ui_group_breadcrumbs)
        addTextRow(SkThemeSlot.BREADCRUMB_SELECTED, rowL1Px)
        addColorRow(SkThemeSlot.BREADCRUMB_UNSELECTED, rowL1Px)
        addColorRow(SkThemeSlot.BREADCRUMB_ARROWS, rowL1Px)

        // File list
        addSection(R.string.sk_ui_group_file_list)
        val updateFileListPreview = addFileListPreview(rowL1Px)
        addSubgroup(R.string.sk_ui_subgroup_text)
        addTextRow(SkThemeSlot.FILE_NAME, rowL2Px)
        addTextRow(SkThemeSlot.FILE_DESCRIPTION, rowL2Px)
        addSubgroup(R.string.sk_ui_subgroup_icons)
        addColorRow(SkThemeSlot.FILE_ICONS, rowL2Px)
        addSliderRow(
            R.string.sk_ui_file_icon_size, 16, 64, SkUi.fileIconSizeDp, rowL2Px
        ) {
            SkUi.fileIconSizeDp = it
            updateFileListPreview()
        }
        addSubgroup(R.string.sk_ui_subgroup_grid)
        val updateGridPreview = addGridPreview(rowL2Px)
        addTextRow(SkThemeSlot.GRID_TEXT, rowL2Px)
        addSliderRow(
            R.string.sk_ui_grid_image_width, 48, 320, SkUi.gridImageWidthDp, rowL2Px
        ) {
            SkUi.gridImageWidthDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_image_height, 48, 320, SkUi.gridImageHeightDp, rowL2Px
        ) {
            SkUi.gridImageHeightDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_padding_h, 0, 32, SkUi.gridPaddingHDp, rowL2Px
        ) {
            SkUi.gridPaddingHDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_padding_v, 0, 32, SkUi.gridPaddingVDp, rowL2Px
        ) {
            SkUi.gridPaddingVDp = it
            updateGridPreview()
        }
        addSliderRow(
            R.string.sk_ui_grid_text_gap, 0, 24, SkUi.gridTextGapDp, rowL2Px
        ) {
            SkUi.gridTextGapDp = it
            updateGridPreview()
        }
        addSwitchRow(
            R.string.sk_ui_grid_text_overlay, SkUi.isGridTextOverlay, rowL2Px
        ) {
            SkUi.isGridTextOverlay = it
            updateGridPreview()
        }
        addSwitchRow(
            R.string.sk_ui_grid_text_show, SkUi.isGridTextVisible, rowL2Px
        ) {
            SkUi.isGridTextVisible = it
            updateGridPreview()
        }
        addSubgroup(R.string.sk_ui_subgroup_separators)
        listOf(
            FileViewType.LIST to R.string.sk_view_list,
            FileViewType.COMPACT to R.string.sk_view_compact,
            FileViewType.COLUMN to R.string.sk_view_column,
            FileViewType.DETAILED to R.string.sk_view_detailed,
            FileViewType.WRAPPED to R.string.sk_view_wrapped
        ).forEach { (separatorViewType, nameRes) ->
            addSliderRowText(
                getString(nameRes), 0, 8,
                SkSeparators.getGlobalThickness(separatorViewType), rowL2Px
            ) { SkSeparators.setGlobalThickness(separatorViewType, it) }
            addCustomColorRow(
                getString(R.string.sk_ui_separator_color_format, getString(nameRes)),
                SkSeparators.getGlobalColor(separatorViewType), rowL2Px
            ) { SkSeparators.setGlobalColor(separatorViewType, it) }
        }
        addSubgroup(R.string.sk_ui_subgroup_options)
        addSliderRow(
            R.string.sk_ui_file_padding, 0, 24, SkUi.filePaddingDp, rowL2Px
        ) {
            SkUi.filePaddingDp = it
            updateFileListPreview()
        }
        addSwitchRow(
            R.string.settings_file_list_animation_title,
            Settings.FILE_LIST_ANIMATION.valueCompat,
            rowL2Px
        ) { Settings.FILE_LIST_ANIMATION.putValue(it) }
        addValueRow(
            R.string.settings_file_name_ellipsize_title,
            ellipsizeLabel(Settings.FILE_NAME_ELLIPSIZE.valueCompat),
            rowL2Px
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
        addColorRow(SkThemeSlot.DRAWER_BACKGROUND, rowL1Px)
        addTextRow(SkThemeSlot.DRAWER_ITEM, rowL1Px)
        addColorRow(SkThemeSlot.DRAWER_ICONS, rowL1Px)

        // Tab bar
        addSection(R.string.sk_ui_group_tabs)
        addColorRow(SkThemeSlot.TAB_BACKGROUND, rowL1Px)
        addTextRow(SkThemeSlot.TAB_SELECTED, rowL1Px)
        addColorRow(SkThemeSlot.TAB_UNSELECTED, rowL1Px)
        addColorRow(SkThemeSlot.TAB_BUTTONS, rowL1Px)

        // Bottom bar
        addSection(R.string.sk_ui_group_bottom_bar)
        addColorRow(SkThemeSlot.BOTTOM_BAR_BACKGROUND, rowL1Px)
        addTextRow(SkThemeSlot.BOTTOM_BAR_TEXT, rowL1Px)
        addColorRow(SkThemeSlot.BOTTOM_BAR_ICONS, rowL1Px)

        // Speed dial
        addSection(R.string.sk_ui_group_speed_dial)
        addColorRow(SkThemeSlot.FAB_BACKGROUND, rowL1Px)
        addColorRow(SkThemeSlot.FAB_ICON, rowL1Px)

        // Audio mini-player
        addSection(R.string.sk_ui_group_audio_player)
        addColorRow(SkThemeSlot.AUDIO_PLAYER_BACKGROUND, rowL1Px)
        addTextRow(SkThemeSlot.AUDIO_PLAYER_TITLE, rowL1Px)
        addTextRow(SkThemeSlot.AUDIO_PLAYER_TIME, rowL1Px)
        addColorRow(SkThemeSlot.AUDIO_PLAYER_CONTROLS, rowL1Px)

        // Share → Termux
        addSection(R.string.sk_ui_group_share)
        addSwitchRow(R.string.sk_ui_share_one_target, SkTermux.oneTargetMode, rowL1Px) {
            SkTermux.oneTargetMode = it
        }
        addValueRow(R.string.sk_ui_share_staging_dir, SkTermux.stagingDir, rowL1Px) { valueView ->
            showStagingDirDialog(valueView)
        }
    }

    // The Export/Import row's page status: the latest export found in the
    // export directory, or a short warning (true = warn).
    private fun eximportPageStatus(): Pair<String, Boolean> {
        val dir = SkEximport.exportDirPath
            ?: return getString(R.string.sk_eximport_status_nodir) to true
        val newest = SkEximport.latestExport(dir)
            ?: return getString(R.string.sk_eximport_status_none) to true
        return getString(
            R.string.sk_eximport_last,
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
                .format(java.util.Date(newest.second))
        ) to false
    }

    private fun openEximportSheet() {
        eximportSheet = SkEximportSheet(
            this,
            onPickDirectory = { eximportDirLauncher.launch(SkEximport.exportDirPath) },
            onPickImportFile = { eximportFileLauncher.launch(listOf(MimeType.ANY)) },
            onDismissed = { eximportSheet = null }
        ).also { it.show() }
    }

    private fun showStagingDirDialog(valueView: TextView) {
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(SkTermux.stagingDir)
            setSelection(text.length)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                TextView(this@SkUiActivity).apply {
                    text = getString(R.string.sk_ui_share_staging_dir_help)
                    textSize = 13f
                    setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
                    setPadding(0, 0, 0, dp(8))
                }
            )
            addView(edit)
        }
        SkMaterialAlertDialogBuilder(this)
            .setTitle(R.string.sk_ui_share_staging_dir)
            .setView(form)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                SkTermux.stagingDir = edit.text.toString()
                valueView.text = SkTermux.stagingDir
            }
            .setNeutralButton(R.string.sk_ui_reset_default) { _, _ ->
                SkTermux.stagingDir = SkTermux.DEFAULT_STAGING_DIR
                valueView.text = SkTermux.stagingDir
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ellipsizeLabel(value: TextUtils.TruncateAt): String =
        resources.getStringArray(R.array.settings_file_name_ellipsize_entries)
            .getOrNull(value.ordinal) ?: value.name

    // --- Row builders ---

    private fun dp(value: Int): Int = (value * densityPx).toInt()

    // A heading underlined exactly as wide as its text: the wrap_content
    // wrapper measures to the text, so the match_parent underline collapses to
    // that width (the kxkb idiom).
    private fun makeUnderlinedHeading(
        @StringRes labelRes: Int,
        textSizeSp: Float,
        underlineHeightDp: Float
    ): LinearLayout {
        val accent = skColor(SkThemeSlot.ACCENT)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        box.addView(
            TextView(this).apply {
                text = getString(labelRes)
                textSize = textSizeSp
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
            }
        )
        box.addView(
            View(this).apply {
                setBackgroundColor(accent)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (underlineHeightDp * densityPx).toInt()
                ).apply { topMargin = dp(2) }
            }
        )
        return box
    }

    // kxkb-style section header: an edge-to-edge 1px accent rule separating it
    // from the previous section, then a 20sp bold accent title with a 2.5dp
    // text-wide underline.
    private fun addSection(@StringRes labelRes: Int) {
        val first = binding.holder.childCount == 0
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(if (first) 12 else 10), 0, dp(2))
        }
        if (!first) {
            holder.addView(
                View(this).apply {
                    setBackgroundColor(skColor(SkThemeSlot.ACCENT))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                }
            )
        }
        holder.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(HEADING_INDENT_DP), dp(8), 0, 0)
                addView(makeUnderlinedHeading(labelRes, 20f, 2.5f))
            }
        )
        binding.holder.addView(holder)
    }

    // A sub-section heading one level down: 17sp bold with a 1.5dp text-wide
    // underline, no rule of its own.
    private fun addSubgroup(@StringRes labelRes: Int) {
        binding.holder.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(SUBHEADING_INDENT_DP), dp(10), 0, dp(2))
                addView(makeUnderlinedHeading(labelRes, 17f, 1.5f))
            }
        )
    }

    private fun makeRow(indent: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPaddingRelative(indent, dp(4), dp(16), dp(4))
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

    private fun makeLabel(@StringRes labelRes: Int): TextView = makeLabelText(getString(labelRes))

    private fun makeLabelText(labelText: String): TextView =
        TextView(this).apply {
            text = labelText
            textSize = 16f
            setTextColor(skColor(SkThemeSlot.TEXT))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    // The label, optionally over a smaller explanatory line — both taking the
    // row's free width, so the control on the right keeps its natural size.
    private fun makeLabelColumn(@StringRes labelRes: Int, @StringRes descriptionRes: Int?): View {
        val label = makeLabel(labelRes)
        if (descriptionRes == null) {
            return label
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            label.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(label)
            addView(
                TextView(this@SkUiActivity).apply {
                    text = getString(descriptionRes)
                    textSize = 13f
                    setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
                    setPadding(0, dp(1), dp(8), 0)
                }
            )
        }
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

    // A color row for non-slot values (e.g. the per-view separator colors);
    // a null pick result reverts to the default.
    private fun addCustomColorRow(
        labelText: String,
        color: Int,
        indent: Int,
        onResult: (Int?) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabelText(labelText))
        row.addView(makeSwatch(color))
        row.setOnClickListener {
            SkColorPickerDialog(this, color) { newColor ->
                onResult(newColor)
                buildRows()
            }
        }
        binding.holder.addView(row)
    }

    private fun addValueRow(
        @StringRes labelRes: Int,
        value: String,
        indent: Int,
        valueColor: Int? = null,
        onClick: (TextView) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabel(labelRes))
        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(valueColor ?: skColor(SkThemeSlot.TEXT_SECONDARY))
        }
        row.addView(valueView)
        row.setOnClickListener { onClick(valueView) }
        binding.holder.addView(row)
    }

    private fun addSwitchRow(
        @StringRes labelRes: Int,
        checked: Boolean,
        indent: Int,
        @StringRes descriptionRes: Int? = null,
        onToggle: (Boolean) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabelColumn(labelRes, descriptionRes))
        val switch = SwitchCompat(this).apply { isChecked = checked }
        row.addView(switch)
        switch.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        row.setOnClickListener { switch.toggle() }
        binding.holder.addView(row)
    }

    // The automation shared secret: shown abbreviated, copied whole on tap (it
    // is pasted into 自由作業盤's 保存復元 settings), regenerated from the action
    // at the end of the row.
    private fun addAutomationTokenRow(indent: Int) {
        val row = makeRow(indent)
        row.addView(makeLabel(R.string.sk_automation_token))
        val valueView = TextView(this).apply {
            text = SkAutomation.abbreviatedToken()
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
        }
        row.addView(valueView)
        row.addView(
            TextView(this).apply {
                text = getString(R.string.sk_automation_token_regenerate)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(skColor(SkThemeSlot.ACCENT))
                setPaddingRelative(dp(16), dp(8), dp(4), dp(8))
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { confirmRegenerateToken(valueView) }
            }
        )
        row.setOnClickListener {
            clipboardManager.primaryText = SkAutomation.token
            showToast(R.string.sk_automation_token_copied)
        }
        binding.holder.addView(row)
    }

    private fun confirmRegenerateToken(valueView: TextView) {
        SkMaterialAlertDialogBuilder(this)
            .setTitle(R.string.sk_automation_token_regenerate)
            .setMessage(R.string.sk_automation_token_regenerate_message)
            .setPositiveButton(R.string.sk_automation_token_regenerate) { _, _ ->
                SkAutomation.regenerateToken()
                valueView.text = SkAutomation.abbreviatedToken()
                showToast(R.string.sk_automation_token_regenerated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

        val subIndent = indent + dp(INDENT_STEP_DP)

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
            setPaddingRelative(subIndent, dp(2), dp(16), dp(10))
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
    ) = addSliderRowText(getString(labelRes), min, max, value, indent, onChange)

    private fun addSliderRowText(
        labelText: String,
        min: Int,
        max: Int,
        value: Int,
        indent: Int,
        onChange: (Int) -> Unit
    ) {
        val row = makeRow(indent)
        val label = makeLabelText(labelText)
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
            setPaddingRelative(indent, dp(4), dp(16), dp(4))
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
                indent, dp(4) + paddingPx, dp(16), dp(4) + paddingPx
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
            setPaddingRelative(indent, dp(4), dp(16), dp(4))
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
