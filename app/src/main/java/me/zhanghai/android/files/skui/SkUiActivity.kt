/*
 * 白い熊 fork (skui): the 白い熊 書類管理 UI page — every customizable aspect of
 * the UI laid out as a section > subgroup > controls cascade, each cascade level
 * indented one more step (the sister-repo convention). All controls write
 * immediately; the main screen re-applies styling in onResume.
 */

package me.zhanghai.android.files.skui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import androidx.core.view.isVisible
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.databinding.SkUiActivityBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.FileViewType
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.SettingsActivity
import me.zhanghai.android.files.util.createIntent
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

class SkUiActivity : AppActivity() {
    private lateinit var binding: SkUiActivityBinding
    private var rowL1Px = 0
    private var rowL2Px = 0
    private var densityPx = 1f

    private var pendingFontSlot: SkThemeSlot? = null

    // Where the row builders put their rows: the body of the section being built.
    private lateinit var rowTarget: LinearLayout

    private class Section(
        val key: String,
        @StringRes val labelRes: Int,
        val icon: Int,
        // One of the seven section colours, by what kind of thing the section is.
        val tone: SkThemeSlot,
        val build: () -> Unit
    )

    private val sectionHeaders = mutableMapOf<String, View>()

    // The page repaints itself while its colours are edited.
    override val skRecreatesOnColorChange: Boolean
        get() = false

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
        rowTarget = binding.holder
        sectionHeaders.clear()

        window.setBackgroundDrawable(ColorDrawable(skColor(SkThemeSlot.BACKGROUND)))
        binding.toolbar.setBackgroundColor(skColor(SkThemeSlot.TOOLBAR_BACKGROUND))
        binding.toolbar.setTitleTextColor(skColor(SkThemeSlot.TOOLBAR_TITLE))
        binding.toolbar.navigationIcon?.setTint(skColor(SkThemeSlot.TOOLBAR_ICONS))

        // The link to the app's own settings, first and thick, right under the
        // page title (black-yellow-theme: every pressable row is a pill).
        addActionRow(
            getString(R.string.settings_title), null, null, rowL1Px, main = true, opens = true,
            icon = R.drawable.settings_icon_white_24dp, role = SkIconRole.VIEW
        ) { startActivity(SettingsActivity::class.createIntent()) }

        val sections = mutableListOf<Section>()
        fun section(
            key: String,
            @StringRes labelRes: Int,
            icon: Int,
            tone: SkThemeSlot,
            build: () -> Unit
        ) {
            sections += Section(key, labelRes, icon, tone, build)
        }
        val base = SkThemeSlot.SECTION_BASE
        val controls = SkThemeSlot.SECTION_CONTROLS
        val words = SkThemeSlot.SECTION_WORDS
        val frame = SkThemeSlot.SECTION_FRAME
        val reach = SkThemeSlot.SECTION_REACH

        // The sections are built in their default order, in which no two neighbours share a
        // colour. App language is the first section, so its chip is the first chip.
        section("language", R.string.sk_ui_group_language, R.drawable.sk_ic_globe, words) {
            addActionRow(
                getString(R.string.sk_ui_language_row, SkLanguage.currentName(this)), null, null,
                rowL1Px, main = true, opens = true, icon = R.drawable.sk_ic_globe,
                role = SkIconRole.PERSON
            ) { SkLanguage.showPicker(this) }
        }

        // Export / Import — carry every setting between installs (the Kōjiki
        // flow); the explanation beneath is the latest export found in the
        // export directory, queried on every page open.
        section("eximport", R.string.sk_eximport_title, R.drawable.sk_ic_swap, reach) {
            val (eximportStatus, eximportWarn) = eximportPageStatus()
            addActionRow(
                getString(R.string.sk_eximport_open), eximportStatus,
                if (eximportWarn) skColor(SkThemeSlot.DANGER) else null, rowL1Px, main = true,
                opens = true, icon = R.drawable.sk_ic_swap, role = SkIconRole.SHARE
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
        }

            // Foundation — the colors everything else inherits from
        section("foundation", R.string.sk_ui_group_foundation, R.drawable.sk_ic_palette, base) {
            addSwitchRow(
                R.string.sk_ui_theme_enabled, SkUi.isSkThemeEnabled, rowL1Px,
                R.string.sk_ui_theme_enabled_desc
            ) {
                SkUi.isSkThemeEnabled = it
                recreate()
            }
            SkThemeSlot.entries.filter { it.group == SkThemeGroup.FOUNDATION }.forEach {
                addColorRow(it, rowL1Px)
            }
            addSubgroup(SkThemeGroup.SECTION_ICONS.labelRes)
            SkThemeSlot.entries.filter { it.group == SkThemeGroup.SECTION_ICONS }.forEach {
                addColorRow(it, rowL2Px)
            }
        }

        section("text", R.string.sk_ui_group_text, R.drawable.sk_ic_text, words) {
            addGroupRows(SkThemeGroup.TEXT)
        }

        section("buttons", R.string.sk_ui_group_buttons, R.drawable.sk_ic_button, controls) {
            addGroupRows(SkThemeGroup.BUTTONS)
        }

        section("dialogs", R.string.sk_ui_group_dialogs, R.drawable.sk_ic_window, reach) {
            addGroupRows(SkThemeGroup.DIALOGS)
        }

        section(
            "switches", R.string.sk_ui_group_switches, R.drawable.sk_ic_toggle, controls
        ) {
            addGroupRows(SkThemeGroup.SWITCHES)
        }

        section("menus", R.string.sk_ui_group_menus, R.drawable.sk_ic_list, reach) {
            addGroupRows(SkThemeGroup.MENUS)
        }

        section("marks", R.string.sk_ui_group_marks, R.drawable.sk_ic_check_box, controls) {
            addGroupRows(SkThemeGroup.MARKS)
        }

        section("lists", R.string.sk_ui_group_lists, R.drawable.sk_ic_list, frame) {
            addGroupRows(SkThemeGroup.LISTS)
        }

        section("fields", R.string.sk_ui_group_fields, R.drawable.sk_ic_field, words) {
            addGroupRows(SkThemeGroup.FIELDS)
        }

        section("bars", R.string.sk_ui_group_bars, R.drawable.sk_ic_layers, frame) {
            addNote(R.string.sk_ui_bars_note, rowL1Px)
            addGroupRows(SkThemeGroup.BARS)
        }

        section("toasts", R.string.sk_ui_group_toasts, R.drawable.sk_ic_bell, SkThemeSlot.SECTION_FLASH) {
            addGroupRows(SkThemeGroup.TOASTS)
        }

            // Toolbar
        section("toolbar", R.string.sk_ui_group_toolbar, R.drawable.sk_ic_window, frame) {
            addColorRow(SkThemeSlot.TOOLBAR_BACKGROUND, rowL1Px)
            addTextRow(SkThemeSlot.TOOLBAR_TITLE, rowL1Px)
            addTextRow(SkThemeSlot.TOOLBAR_SUBTITLE, rowL1Px)
            addColorRow(SkThemeSlot.TOOLBAR_ICONS, rowL1Px)
        }

            // File list
        section("file_list", R.string.sk_ui_group_file_list, R.drawable.file_icon_white_24dp, base) {
            val updateFileListPreview = addFileListPreview(rowL1Px)
            addSubgroup(R.string.sk_ui_subgroup_text)
            addTextRow(SkThemeSlot.FILE_NAME, rowL2Px)
            addTextRow(SkThemeSlot.FILE_DESCRIPTION, rowL2Px)
            addSubgroup(R.string.sk_ui_subgroup_icons)
            addColorRow(SkThemeSlot.FILE_ICONS, rowL2Px)
            addColorRow(SkThemeSlot.FILE_BADGE, rowL2Px)
            addColorRow(SkThemeSlot.GRID_OUTLINE, rowL2Px)
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
        }

            // Breadcrumbs
        section("breadcrumbs", R.string.sk_ui_group_breadcrumbs, R.drawable.arrow_end_icon_white_24dp, frame) {
            addTextRow(SkThemeSlot.BREADCRUMB_SELECTED, rowL1Px)
            addColorRow(SkThemeSlot.BREADCRUMB_UNSELECTED, rowL1Px)
            addColorRow(SkThemeSlot.BREADCRUMB_ARROWS, rowL1Px)
        }

            // Audio mini-player
        section("audio_player", R.string.sk_ui_group_audio_player, R.drawable.sk_ic_play, base) {
            addColorRow(SkThemeSlot.AUDIO_PLAYER_BACKGROUND, rowL1Px)
            addTextRow(SkThemeSlot.AUDIO_PLAYER_TITLE, rowL1Px)
            addTextRow(SkThemeSlot.AUDIO_PLAYER_TIME, rowL1Px)
            addColorRow(SkThemeSlot.AUDIO_PLAYER_CONTROLS, rowL1Px)
        }

            // Navigation drawer
        section("drawer", R.string.sk_ui_group_drawer, R.drawable.menu_icon_control_normal_24dp, frame) {
            addColorRow(SkThemeSlot.DRAWER_BACKGROUND, rowL1Px)
            addTextRow(SkThemeSlot.DRAWER_ITEM, rowL1Px)
            addColorRow(SkThemeSlot.DRAWER_ICONS, rowL1Px)
        }

            // Speed dial
        section("speed_dial", R.string.sk_ui_group_speed_dial, R.drawable.add_icon_white_24dp, controls) {
            addColorRow(SkThemeSlot.FAB_BACKGROUND, rowL1Px)
            addColorRow(SkThemeSlot.FAB_ICON, rowL1Px)
        }

            // Tab bar
        section("tabs", R.string.sk_ui_group_tabs, R.drawable.sk_ic_tab, frame) {
            addColorRow(SkThemeSlot.TAB_BACKGROUND, rowL1Px)
            addTextRow(SkThemeSlot.TAB_SELECTED, rowL1Px)
            addColorRow(SkThemeSlot.TAB_UNSELECTED, rowL1Px)
            addColorRow(SkThemeSlot.TAB_BUTTONS, rowL1Px)
        }

        section("extract_progress", R.string.sk_ui_group_extract_progress, R.drawable.extract_icon_control_normal_24dp, reach) {
            addColorRow(SkThemeSlot.EXTRACT_PROGRESS_BAR, rowL1Px)
            addColorRow(SkThemeSlot.EXTRACT_PROGRESS_TRACK, rowL1Px)
            addTextRow(SkThemeSlot.EXTRACT_PROGRESS_NAME, rowL1Px)
            addTextRow(SkThemeSlot.EXTRACT_PROGRESS_INFO, rowL1Px)
            addTextRow(SkThemeSlot.EXTRACT_PROGRESS_SPEED, rowL1Px)
        }

            // Bottom bar
        section("bottom_bar", R.string.sk_ui_group_bottom_bar, R.drawable.paste_icon_control_normal_24dp, frame) {
            addColorRow(SkThemeSlot.BOTTOM_BAR_BACKGROUND, rowL1Px)
            addTextRow(SkThemeSlot.BOTTOM_BAR_TEXT, rowL1Px)
            addColorRow(SkThemeSlot.BOTTOM_BAR_ICONS, rowL1Px)
        }

            // Share → Termux
        section("share", R.string.sk_ui_group_share, R.drawable.sk_share_icon_control_normal_24dp, reach) {
            addSwitchRow(R.string.sk_ui_share_one_target, SkTermux.oneTargetMode, rowL1Px) {
                SkTermux.oneTargetMode = it
            }
            addValueRow(R.string.sk_ui_share_staging_dir, SkTermux.stagingDir, rowL1Px) { valueView ->
                showStagingDirDialog(valueView)
            }
        }

            // APK files: what a tap on an APK / XAPK / APKS / APKM does (SkApkRoute).
        section("apk", R.string.sk_ui_group_apk, R.drawable.sk_ic_shield, base) {
            addValueRow(
                R.string.sk_ui_apk_route, getString(SkApkRoute.mode.labelRes), rowL1Px
            ) { valueView -> showApkRouteDialog(valueView) }

        }

        section(
            "maintenance", R.string.sk_ui_group_maintenance, R.drawable.reset_icon_white_24dp,
            SkThemeSlot.SECTION_WARNING
        ) {
            addActionRow(
                getString(R.string.sk_ui_reset_section_order),
                getString(R.string.sk_ui_reset_section_order_desc), null, rowL1Px, main = false,
                opens = false, icon = R.drawable.sk_ic_swap, role = SkIconRole.VIEW
            ) {
                SkUi.resetUiSectionOrder()
                buildRows()
            }
            addActionRow(
                getString(R.string.sk_ui_reset_all_colors),
                getString(R.string.sk_ui_reset_all_colors_desc), null, rowL1Px, main = false,
                opens = true, icon = R.drawable.sk_ic_palette, role = SkIconRole.DELETE,
                destructive = true
            ) {
                SkMaterialAlertDialogBuilder(this, destructive = true)
                    .setTitle(R.string.sk_ui_reset_all_colors)
                    .setMessage(R.string.sk_ui_reset_all_colors_message)
                    .setPositiveButton(R.string.sk_ui_reset_all_colors) { _, _ ->
                        SkUi.clearAllColorOverrides()
                        buildRows()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        // The stored order: stored keys first, as far as they still exist, then any section
        // added since at its built place.
        // App language comes first also in an order stored before the section existed: nothing
        // dragged it anywhere yet. An order that names it is left as it was dragged.
        val stored = SkUi.uiSectionOrder.filter { key -> sections.any { it.key == key } }
            .distinct().let { if (it.isNotEmpty() && "language" !in it) listOf("language") + it else it }
        val ordered = stored.mapNotNull { key -> sections.find { it.key == key } } +
            sections.filter { it.key !in stored }
        addSectionIndex(ordered)
        ordered.forEach { addSection(it) }
    }

    private fun addGroupRows(group: SkThemeGroup) {
        SkThemeSlot.entries.filter { it.group == group }.forEach {
            if (it.hasFont) addTextRow(it, rowL1Px) else addColorRow(it, rowL1Px)
        }
    }

    // A note that belongs to a whole section rather than to one row: dim gold, nothing to press.
    private fun addNote(@StringRes textRes: Int, indent: Int) {
        rowTarget.addView(
            TextView(this).apply {
                text = getString(textRes)
                textSize = 13f
                setTextColor(skColor(SkThemeSlot.NOTE))
                setPaddingRelative(indent, dp(2), dp(16), dp(2))
            }
        )
    }

    // The index: one small chip for every section. A tap unfolds the section and scrolls to
    // it; a long press drags the chip, and the sections follow its new place.
    private fun addSectionIndex(ordered: List<Section>) {
        val index = SkSectionIndex.build(
            this, ordered.map {
                SkSectionIndex.Item(it.key, getString(it.labelRes), it.icon, skColor(it.tone))
            },
            skColor(SkThemeSlot.PILL_FRAME), skColor(SkThemeSlot.PILL_TEXT),
            onJump = { key ->
                if (key !in SkUi.uiSectionsOpen) {
                    SkUi.uiSectionsOpen = SkUi.uiSectionsOpen + key
                    buildRows()
                }
                binding.holder.post {
                    sectionHeaders[key]?.let { header ->
                        (binding.holder.parent as? androidx.core.widget.NestedScrollView)
                            ?.smoothScrollTo(0, header.top)
                    }
                }
            },
            onReordered = {
                SkUi.uiSectionOrder = it
                buildRows()
            }
        )
        binding.holder.addView(
            LinearLayout(this).apply {
                setPaddingRelative(dp(HEADING_INDENT_DP), dp(6), dp(16), dp(2))
                addView(
                    index, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )
    }

    private fun showApkRouteDialog(valueView: TextView) {
        val modes = SkApkRoute.Mode.entries
        SkMaterialAlertDialogBuilder(this)
            .setTitle(R.string.sk_ui_apk_route)
            .setSingleChoiceItems(
                modes.map { getString(it.labelRes) }.toTypedArray(),
                modes.indexOf(SkApkRoute.mode)
            ) { dialog, which ->
                SkApkRoute.mode = modes[which]
                valueView.text = getString(SkApkRoute.mode.labelRes)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val accent = skColor(SkThemeSlot.HEADING)
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

    // A folding section: a 1 px darker-accent rule separating it from what is above — kept
    // whether the section is folded or open — then its heading: the section's icon, a 20 sp
    // bold title with a 2.5 dp text-wide underline, and at the right a › when folded, a ⌄ when
    // open. A tap on the heading folds or unfolds the rows. Folded unless it was opened; the
    // rows of a folded section are not built at all.
    private fun addSection(section: Section) {
        val isOpen = section.key in SkUi.uiSectionsOpen
        val heading = skColor(SkThemeSlot.HEADING)
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        holder.addView(
            View(this).apply {
                setBackgroundColor(skColor(SkThemeSlot.MENU_DIVIDER))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
            }
        )
        val headingBox = makeUnderlinedHeading(section.labelRes, 20f, 2.5f)
        (headingBox.getChildAt(0) as TextView).let {
            applyRowIcon(it, section.icon, skColor(section.tone))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(HEADING_INDENT_DP), dp(8), dp(16), dp(6))
            setBackgroundResource(android.R.drawable.list_selector_background)
            addView(headingBox)
            addView(
                View(this@SkUiActivity),
                LinearLayout.LayoutParams(0, 1, 1f)
            )
            // › when folded, ⌄ when open: the same mark, turned.
            addView(
                android.widget.ImageView(this@SkUiActivity).apply {
                    setImageDrawable(
                        androidx.appcompat.content.res.AppCompatResources.getDrawable(
                            this@SkUiActivity, R.drawable.sk_ic_chevron
                        )
                    )
                    imageTintList = ColorStateList.valueOf(heading)
                    rotation = if (isOpen) 90f else 0f
                }, LinearLayout.LayoutParams(dp(24), dp(24))
            )
            setOnClickListener {
                val open = SkUi.uiSectionsOpen
                SkUi.uiSectionsOpen =
                    if (section.key in open) open - section.key else open + section.key
                buildRows()
            }
        }
        holder.addView(header)
        sectionHeaders[section.key] = holder
        if (isOpen) {
            val sectionBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            holder.addView(sectionBody)
            rowTarget = sectionBody
            section.build()
            rowTarget = binding.holder
        }
        binding.holder.addView(holder)
    }

    // The icon of a row, at the start of its label: coloured by what the row does and as tall
    // as the label's text line (1.25 × its text size), never under 18 dp.
    private fun applyRowIcon(label: TextView, iconRes: Int, role: SkIconRole) =
        applyRowIcon(label, iconRes, skColor(role))

    private fun applyRowIcon(label: TextView, iconRes: Int, color: Int) {
        if (iconRes == 0) {
            return
        }
        val size = maxOf(dp(18), Math.round(label.textSize * 1.25f))
        val icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, iconRes)
            ?.mutate() ?: return
        icon.setTint(color)
        // Centred on the label's first line, also when the label wraps.
        val placed = SkFirstLineIcon(icon, label)
        placed.setBounds(0, 0, size, size)
        label.compoundDrawablePadding = dp(8)
        label.setCompoundDrawablesRelative(placed, null, null, null)
    }

    // The 2 dp dim-gold rule down the left edge of a row that carries an explanation, 10 dp
    // before its text.
    private fun ruled(row: View) {
        row.foreground = SkLeftRule(
            row.paddingStart - dp(12).toFloat(), dp(2).toFloat(), dp(2).toFloat(),
            skColor(SkThemeSlot.NOTE)
        )
    }

    // A sub-section heading one level down: 17sp bold with a 1.5dp text-wide
    // underline, no rule of its own.
    private fun addSubgroup(@StringRes labelRes: Int) {
        rowTarget.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(dp(SUBHEADING_INDENT_DP), dp(10), 0, dp(2))
                addView(makeUnderlinedHeading(labelRes, 17f, 1.5f))
            }
        )
    }

    // Every row only as tall as its content: no 48 dp minimum, 2 dp above and
    // below (black-yellow-theme: row padding minimal).
    private fun makeRow(indent: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 0
            setPaddingRelative(indent, dp(2), dp(16), dp(2))
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

    private fun makeLabel(
        @StringRes labelRes: Int,
        icon: Int,
        role: SkIconRole = SkIconRole.VIEW
    ): TextView = makeLabelText(getString(labelRes), icon, role)

    private fun makeLabelText(
        labelText: String,
        icon: Int,
        role: SkIconRole = SkIconRole.VIEW
    ): TextView =
        TextView(this).apply {
            text = labelText
            textSize = 16f
            setTextColor(skColor(SkThemeSlot.TEXT))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            applyRowIcon(this, icon, role)
        }

    // The label, optionally over a smaller explanatory line — both taking the
    // row's free width, so the control on the right keeps its natural size.
    private fun makeLabelColumn(
        @StringRes labelRes: Int,
        @StringRes descriptionRes: Int?,
        icon: Int,
        role: SkIconRole
    ): View {
        val label = makeLabel(labelRes, icon, role)
        if (descriptionRes == null) {
            return label
        }
        // An explained row: bold label, dim-gold description.
        label.setTypeface(label.typeface, android.graphics.Typeface.BOLD)
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
                    setTextColor(skColor(SkThemeSlot.NOTE))
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
                setStroke(dp(1), skColor(SkThemeSlot.TEXT_TERTIARY))
            }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }

    private fun addColorRow(slot: SkThemeSlot, indent: Int) {
        val row = makeRow(indent)
        row.addView(makeLabel(slot.labelRes, R.drawable.sk_ic_palette))
        row.addView(makeSwatch(skColor(slot)))
        row.setOnClickListener {
            SkColorPickerDialog(this, skColor(slot)) { color ->
                if (color != null) setSkColor(slot, color) else resetSkColor(slot)
                buildRows()
            }
        }
        rowTarget.addView(row)
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
        row.addView(makeLabelText(labelText, R.drawable.sk_ic_palette))
        row.addView(makeSwatch(color))
        row.setOnClickListener {
            SkColorPickerDialog(this, color) { newColor ->
                onResult(newColor)
                buildRows()
            }
        }
        rowTarget.addView(row)
    }

    private fun addValueRow(
        @StringRes labelRes: Int,
        value: String,
        indent: Int,
        valueColor: Int? = null,
        onClick: (TextView) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabel(labelRes, R.drawable.sk_ic_list, SkIconRole.OPEN))
        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(valueColor ?: skColor(SkThemeSlot.TEXT_SECONDARY))
        }
        row.addView(valueView)
        row.setOnClickListener { onClick(valueView) }
        rowTarget.addView(row)
    }

    // An action / navigation row: an outlined pill as wide as its label
    // (› when it opens something; 3 dp and bold for the main action of its
    // section, else 1.25 dp), its explanation beneath in the secondary color.
    // Only the pill is the touch target.
    private fun addActionRow(
        labelText: String,
        summary: String?,
        summaryColor: Int?,
        indent: Int,
        main: Boolean,
        opens: Boolean,
        icon: Int = 0,
        role: SkIconRole = SkIconRole.VIEW,
        destructive: Boolean = false,
        onClick: () -> Unit
    ) {
        val ink = skColor(if (destructive) SkThemeSlot.DANGER else SkThemeSlot.PILL_TEXT)
        val frame = skColor(if (destructive) SkThemeSlot.DANGER else SkThemeSlot.PILL_FRAME)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(indent, dp(3), dp(16), dp(3))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(18), dp(9), dp(16), dp(9))
            background = skPillBackground(
                densityPx, frame, skColor(SkThemeSlot.PILL_GROUND), main
            )
            setOnClickListener { onClick() }
        }
        pill.addView(
            TextView(this).apply {
                text = labelText
                textSize = 16f
                setTextColor(ink)
                if (main) setTypeface(typeface, android.graphics.Typeface.BOLD)
                applyRowIcon(this, icon, role)
            }
        )
        if (opens) {
            pill.addView(
                TextView(this).apply {
                    text = "\u203A"
                    textSize = 22f
                    includeFontPadding = false
                    setTextColor(ink)
                    if (main) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(10) }
                }
            )
        }
        // The pill hugs its label — never stretched across the line.
        row.addView(
            pill, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        if (!summary.isNullOrEmpty()) {
            row.addView(
                TextView(this).apply {
                    text = summary
                    textSize = 13f
                    setTextColor(summaryColor ?: skColor(SkThemeSlot.NOTE))
                    setPaddingRelative(dp(18), dp(3), 0, 0)
                }
            )
            ruled(row)
        }
        rowTarget.addView(row)
    }

    private fun addSwitchRow(
        @StringRes labelRes: Int,
        checked: Boolean,
        indent: Int,
        @StringRes descriptionRes: Int? = null,
        icon: Int = R.drawable.sk_ic_toggle,
        role: SkIconRole = SkIconRole.VIEW,
        onToggle: (Boolean) -> Unit
    ) {
        val row = makeRow(indent)
        row.addView(makeLabelColumn(labelRes, descriptionRes, icon, role))
        if (descriptionRes != null) {
            ruled(row)
        }
        val switch = SkSwitch(this).apply {
            isChecked = checked
            minHeight = 0
            minimumHeight = 0
        }
        row.addView(switch)
        switch.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        row.setOnClickListener { switch.toggle() }
        rowTarget.addView(row)
    }

    // The automation shared secret: shown abbreviated, copied whole on tap (it
    // is pasted into 自由作業盤's 保存復元 settings), regenerated from the action
    // at the end of the row.
    private fun addAutomationTokenRow(indent: Int) {
        val row = makeRow(indent)
        row.addView(makeLabel(R.string.sk_automation_token, R.drawable.sk_ic_key, SkIconRole.BLOCK))
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
                applySkPill()
                setPadding(dp(14), dp(5), dp(14), dp(5))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(12) }
                setOnClickListener { confirmRegenerateToken(valueView) }
            }
        )
        row.setOnClickListener {
            clipboardManager.primaryText = SkAutomation.token
            showToast(R.string.sk_automation_token_copied)
        }
        rowTarget.addView(row)
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
        colorRow.addView(makeLabel(slot.labelRes, R.drawable.sk_ic_palette))
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
        fontRow.addView(makeLabel(R.string.sk_font, R.drawable.sk_ic_text, SkIconRole.WRITE))
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
        weightRow.addView(makeLabel(R.string.sk_font_weight, R.drawable.sk_ic_text, SkIconRole.WRITE))
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
            setPaddingRelative(subIndent, dp(2), dp(16), dp(2))
        }

        val sizeRow = makeRow(subIndent)
        sizeRow.addView(makeLabel(R.string.sk_font_size, R.drawable.sk_ic_tune, SkIconRole.WRITE))
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

        rowTarget.addView(holder)
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
        val label = makeLabelText(labelText, R.drawable.sk_ic_tune)
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
        rowTarget.addView(row)
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
        rowTarget.addView(row)

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
        rowTarget.addView(cell)

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
                setColor(skColor(SkThemeSlot.LIST_SELECTED))
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
