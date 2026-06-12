/*
 * 白い熊 fork (skui): the customizable color slots, grouped by UI surface.
 *
 * Foundation slots are the small set everything else inherits from (two-tier):
 * a slot only diverges once the user gives it an explicit override, so a single
 * foundation change cascades through the whole app. The defaults seed the
 * 白い熊 look — black background, yellow text/icons/borders.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import me.zhanghai.android.files.R

enum class SkThemeGroup(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.sk_ui_group_foundation),
    TOOLBAR(R.string.sk_ui_group_toolbar),
    BREADCRUMBS(R.string.sk_ui_group_breadcrumbs),
    FILE_LIST(R.string.sk_ui_group_file_list),
    DRAWER(R.string.sk_ui_group_drawer),
    TABS(R.string.sk_ui_group_tabs),
    BOTTOM_BAR(R.string.sk_ui_group_bottom_bar),
    SPEED_DIAL(R.string.sk_ui_group_speed_dial)
}

enum class SkThemeSlot(
    val key: String,
    val group: SkThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text elements (family / weight / size are configurable)
    val hasFont: Boolean = false
) {
    // Foundation — everything else inherits from these
    BACKGROUND("sk_background", SkThemeGroup.FOUNDATION, R.string.sk_ui_background, isFoundation = true),
    ACCENT("sk_accent", SkThemeGroup.FOUNDATION, R.string.sk_ui_accent, isFoundation = true),
    TEXT("sk_text", SkThemeGroup.FOUNDATION, R.string.sk_ui_text, isFoundation = true),
    TEXT_SECONDARY("sk_text_secondary", SkThemeGroup.FOUNDATION, R.string.sk_ui_text_secondary, isFoundation = true),

    // Toolbar (app bar)
    TOOLBAR_BACKGROUND("sk_toolbar_background", SkThemeGroup.TOOLBAR, R.string.sk_ui_toolbar_background),
    TOOLBAR_TITLE("sk_toolbar_title", SkThemeGroup.TOOLBAR, R.string.sk_ui_toolbar_title, hasFont = true),
    TOOLBAR_SUBTITLE("sk_toolbar_subtitle", SkThemeGroup.TOOLBAR, R.string.sk_ui_toolbar_subtitle, hasFont = true),
    TOOLBAR_ICONS("sk_toolbar_icons", SkThemeGroup.TOOLBAR, R.string.sk_ui_toolbar_icons),

    // Breadcrumbs
    BREADCRUMB_SELECTED("sk_breadcrumb_selected", SkThemeGroup.BREADCRUMBS, R.string.sk_ui_breadcrumb_selected, hasFont = true),
    BREADCRUMB_UNSELECTED("sk_breadcrumb_unselected", SkThemeGroup.BREADCRUMBS, R.string.sk_ui_breadcrumb_unselected),
    BREADCRUMB_ARROWS("sk_breadcrumb_arrows", SkThemeGroup.BREADCRUMBS, R.string.sk_ui_breadcrumb_arrows),

    // File list
    FILE_NAME("sk_file_name", SkThemeGroup.FILE_LIST, R.string.sk_ui_file_name, hasFont = true),
    FILE_DESCRIPTION("sk_file_description", SkThemeGroup.FILE_LIST, R.string.sk_ui_file_description, hasFont = true),
    FILE_ICONS("sk_file_icons", SkThemeGroup.FILE_LIST, R.string.sk_ui_file_icons),
    GRID_TEXT("sk_grid_text", SkThemeGroup.FILE_LIST, R.string.sk_ui_grid_text, hasFont = true),

    // Navigation drawer
    DRAWER_BACKGROUND("sk_drawer_background", SkThemeGroup.DRAWER, R.string.sk_ui_drawer_background),
    DRAWER_ITEM("sk_drawer_item", SkThemeGroup.DRAWER, R.string.sk_ui_drawer_item, hasFont = true),
    DRAWER_ICONS("sk_drawer_icons", SkThemeGroup.DRAWER, R.string.sk_ui_drawer_icons),

    // Tab bar
    TAB_BACKGROUND("sk_tab_background", SkThemeGroup.TABS, R.string.sk_ui_tab_background),
    TAB_SELECTED("sk_tab_selected", SkThemeGroup.TABS, R.string.sk_ui_tab_selected, hasFont = true),
    TAB_UNSELECTED("sk_tab_unselected", SkThemeGroup.TABS, R.string.sk_ui_tab_unselected),
    TAB_BUTTONS("sk_tab_buttons", SkThemeGroup.TABS, R.string.sk_ui_tab_buttons),

    // Bottom bar (paste / pick bar)
    BOTTOM_BAR_BACKGROUND("sk_bottom_bar_background", SkThemeGroup.BOTTOM_BAR, R.string.sk_ui_bottom_bar_background),
    BOTTOM_BAR_TEXT("sk_bottom_bar_text", SkThemeGroup.BOTTOM_BAR, R.string.sk_ui_bottom_bar_text, hasFont = true),
    BOTTOM_BAR_ICONS("sk_bottom_bar_icons", SkThemeGroup.BOTTOM_BAR, R.string.sk_ui_bottom_bar_icons),

    // Speed dial (new file/folder button)
    FAB_BACKGROUND("sk_fab_background", SkThemeGroup.SPEED_DIAL, R.string.sk_ui_fab_background),
    FAB_ICON("sk_fab_icon", SkThemeGroup.SPEED_DIAL, R.string.sk_ui_fab_icon)
}

private fun Int.withAlphaFraction(fraction: Float): Int =
    ColorUtils.setAlphaComponent(this, (fraction * 255).toInt())

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun skColor(slot: SkThemeSlot): Int {
    val override = SkUi.getColorOverride(slot.key)
    return if (override != SkUi.UNSET) override else skDefault(slot)
}

private fun skDefault(slot: SkThemeSlot): Int = when (slot) {
    // Foundation — the 白い熊 palette
    SkThemeSlot.BACKGROUND -> SkUi.PALETTE_BLACK
    SkThemeSlot.ACCENT -> SkUi.PALETTE_YELLOW
    SkThemeSlot.TEXT -> SkUi.PALETTE_YELLOW
    SkThemeSlot.TEXT_SECONDARY -> skColor(SkThemeSlot.TEXT).withAlphaFraction(0.7f)

    // Toolbar
    SkThemeSlot.TOOLBAR_BACKGROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.TOOLBAR_TITLE -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.TOOLBAR_SUBTITLE -> skColor(SkThemeSlot.TEXT_SECONDARY)
    SkThemeSlot.TOOLBAR_ICONS -> skColor(SkThemeSlot.ACCENT)

    // Breadcrumbs
    SkThemeSlot.BREADCRUMB_SELECTED -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.BREADCRUMB_UNSELECTED -> skColor(SkThemeSlot.TEXT_SECONDARY)
    SkThemeSlot.BREADCRUMB_ARROWS -> skColor(SkThemeSlot.TEXT_SECONDARY)

    // File list
    SkThemeSlot.FILE_NAME -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.FILE_DESCRIPTION -> skColor(SkThemeSlot.TEXT_SECONDARY)
    SkThemeSlot.FILE_ICONS -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.GRID_TEXT -> skColor(SkThemeSlot.TEXT)

    // Navigation drawer
    SkThemeSlot.DRAWER_BACKGROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.DRAWER_ITEM -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.DRAWER_ICONS -> skColor(SkThemeSlot.ACCENT)

    // Tab bar
    SkThemeSlot.TAB_BACKGROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.TAB_SELECTED -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.TAB_UNSELECTED -> skColor(SkThemeSlot.TEXT_SECONDARY)
    SkThemeSlot.TAB_BUTTONS -> skColor(SkThemeSlot.ACCENT)

    // Bottom bar
    SkThemeSlot.BOTTOM_BAR_BACKGROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.BOTTOM_BAR_TEXT -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.BOTTOM_BAR_ICONS -> skColor(SkThemeSlot.ACCENT)

    // Speed dial: black button, yellow border and plus
    SkThemeSlot.FAB_BACKGROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.FAB_ICON -> skColor(SkThemeSlot.ACCENT)
}

fun setSkColor(slot: SkThemeSlot, color: Int) = SkUi.setColorOverride(slot.key, color)

/** Revert a slot to its default (palette for the foundation slots, inherited otherwise). */
fun resetSkColor(slot: SkThemeSlot) = SkUi.clearColorOverride(slot.key)

@Suppress("unused")
fun Context.skColorOf(slot: SkThemeSlot): Int = skColor(slot)
