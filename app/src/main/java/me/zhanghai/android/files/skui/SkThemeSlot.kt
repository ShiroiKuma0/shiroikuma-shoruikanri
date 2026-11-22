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
    TEXT(R.string.sk_ui_group_text),
    BUTTONS(R.string.sk_ui_group_buttons),
    DIALOGS(R.string.sk_ui_group_dialogs),
    SWITCHES(R.string.sk_ui_group_switches),
    MARKS(R.string.sk_ui_group_marks),
    MENUS(R.string.sk_ui_group_menus),
    LISTS(R.string.sk_ui_group_lists),
    FIELDS(R.string.sk_ui_group_fields),
    BARS(R.string.sk_ui_group_bars),
    TOASTS(R.string.sk_ui_group_toasts),
    TOOLBAR(R.string.sk_ui_group_toolbar),
    BREADCRUMBS(R.string.sk_ui_group_breadcrumbs),
    FILE_LIST(R.string.sk_ui_group_file_list),
    DRAWER(R.string.sk_ui_group_drawer),
    TABS(R.string.sk_ui_group_tabs),
    BOTTOM_BAR(R.string.sk_ui_group_bottom_bar),
    SPEED_DIAL(R.string.sk_ui_group_speed_dial),
    AUDIO_PLAYER(R.string.sk_ui_group_audio_player),
    EXTRACT_PROGRESS(R.string.sk_ui_group_extract_progress)
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

    // Text — every kind of text that has no surface of its own
    TEXT_TERTIARY("sk_text_tertiary", SkThemeGroup.TEXT, R.string.sk_ui_text_tertiary),
    TEXT_HINT("sk_text_hint", SkThemeGroup.TEXT, R.string.sk_ui_text_hint),
    TEXT_DISABLED("sk_text_disabled", SkThemeGroup.TEXT, R.string.sk_ui_text_disabled),
    TEXT_SECONDARY_DISABLED("sk_text_secondary_disabled", SkThemeGroup.TEXT, R.string.sk_ui_text_secondary_disabled),
    HEADING("sk_heading", SkThemeGroup.TEXT, R.string.sk_ui_heading),
    // The dim gold of explanations and of the rule down their left edge.
    NOTE("sk_note", SkThemeGroup.TEXT, R.string.sk_ui_note),
    LINK("sk_link", SkThemeGroup.TEXT, R.string.sk_ui_link),
    TEXT_SELECTION("sk_text_selection", SkThemeGroup.TEXT, R.string.sk_ui_text_selection),
    ICON("sk_icon", SkThemeGroup.TEXT, R.string.sk_ui_icon),
    ICON_ACTIVE("sk_icon_active", SkThemeGroup.TEXT, R.string.sk_ui_icon_active),

    // Buttons — the pill
    PILL_FRAME("sk_pill_frame", SkThemeGroup.BUTTONS, R.string.sk_ui_pill_frame),
    PILL_TEXT("sk_pill_text", SkThemeGroup.BUTTONS, R.string.sk_ui_pill_text),
    PILL_GROUND("sk_pill_ground", SkThemeGroup.BUTTONS, R.string.sk_ui_pill_ground),
    // A button that deletes lends this to its frame and its label; so does a menu row.
    DANGER("sk_danger", SkThemeGroup.BUTTONS, R.string.sk_ui_danger),

    // Dialogs
    DIALOG_GROUND("sk_dialog_ground", SkThemeGroup.DIALOGS, R.string.sk_ui_dialog_ground),
    DIALOG_FRAME("sk_dialog_frame", SkThemeGroup.DIALOGS, R.string.sk_ui_dialog_frame),
    DIALOG_TITLE("sk_dialog_title", SkThemeGroup.DIALOGS, R.string.sk_ui_dialog_title),
    DIALOG_TEXT("sk_dialog_text", SkThemeGroup.DIALOGS, R.string.sk_ui_dialog_text),

    // Switches. Off is an opaque darker yellow, never a faded one.
    SWITCH_ON("sk_switch_on", SkThemeGroup.SWITCHES, R.string.sk_ui_switch_on),
    SWITCH_OFF("sk_switch_off", SkThemeGroup.SWITCHES, R.string.sk_ui_switch_off),

    // Selection marks: check boxes and radio buttons
    MARK_ON("sk_mark_on", SkThemeGroup.MARKS, R.string.sk_ui_mark_on),
    MARK_OFF("sk_mark_off", SkThemeGroup.MARKS, R.string.sk_ui_mark_off),

    // Menus. The icon of a row is coloured by what the row does.
    MENU_GROUND("sk_menu_ground", SkThemeGroup.MENUS, R.string.sk_ui_menu_ground),
    MENU_FRAME("sk_menu_frame", SkThemeGroup.MENUS, R.string.sk_ui_menu_frame),
    MENU_LABEL("sk_menu_label", SkThemeGroup.MENUS, R.string.sk_ui_menu_label),
    MENU_ROW_FRAME("sk_menu_row_frame", SkThemeGroup.MENUS, R.string.sk_ui_menu_row_frame),
    MENU_DIVIDER("sk_menu_divider", SkThemeGroup.MENUS, R.string.sk_ui_menu_divider),
    MENU_ICON_OPEN("sk_menu_icon_open", SkThemeGroup.MENUS, R.string.sk_ui_menu_icon_open),
    MENU_ICON_WRITE("sk_menu_icon_write", SkThemeGroup.MENUS, R.string.sk_ui_menu_icon_write),
    MENU_ICON_PERSON("sk_menu_icon_person", SkThemeGroup.MENUS, R.string.sk_ui_menu_icon_person),
    MENU_ICON_SHARE("sk_menu_icon_share", SkThemeGroup.MENUS, R.string.sk_ui_menu_icon_share),
    MENU_ICON_VIEW("sk_menu_icon_view", SkThemeGroup.MENUS, R.string.sk_ui_menu_icon_view),
    MENU_ICON_BLOCK("sk_menu_icon_block", SkThemeGroup.MENUS, R.string.sk_ui_menu_icon_block),

    // Lists
    LIST_DIVIDER("sk_list_divider", SkThemeGroup.LISTS, R.string.sk_ui_list_divider),
    LIST_PRESSED("sk_list_pressed", SkThemeGroup.LISTS, R.string.sk_ui_list_pressed),
    LIST_SELECTED("sk_list_selected", SkThemeGroup.LISTS, R.string.sk_ui_list_selected),
    LIST_SELECTED_TEXT("sk_list_selected_text", SkThemeGroup.LISTS, R.string.sk_ui_list_selected_text),

    // Text fields
    FIELD_TEXT("sk_field_text", SkThemeGroup.FIELDS, R.string.sk_ui_field_text),
    FIELD_FRAME("sk_field_frame", SkThemeGroup.FIELDS, R.string.sk_ui_field_frame),

    // System bars
    STATUS_BAR("sk_status_bar", SkThemeGroup.BARS, R.string.sk_ui_status_bar),
    NAVIGATION_BAR("sk_navigation_bar", SkThemeGroup.BARS, R.string.sk_ui_navigation_bar),
    NOTIFICATION_ACCENT("sk_notification_accent", SkThemeGroup.BARS, R.string.sk_ui_notification_accent),

    // Toasts and tooltips
    TOAST_GROUND("sk_toast_ground", SkThemeGroup.TOASTS, R.string.sk_ui_toast_ground),
    TOAST_FRAME("sk_toast_frame", SkThemeGroup.TOASTS, R.string.sk_ui_toast_frame),
    TOAST_TEXT("sk_toast_text", SkThemeGroup.TOASTS, R.string.sk_ui_toast_text),

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
    // The small mark on a file's icon: a link, a broken link, an encrypted file.
    FILE_BADGE("sk_file_badge", SkThemeGroup.FILE_LIST, R.string.sk_ui_file_badge),
    GRID_OUTLINE("sk_grid_outline", SkThemeGroup.FILE_LIST, R.string.sk_ui_grid_outline),

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
    FAB_ICON("sk_fab_icon", SkThemeGroup.SPEED_DIAL, R.string.sk_ui_fab_icon),

    // Audio mini-player
    AUDIO_PLAYER_BACKGROUND("sk_audio_player_background", SkThemeGroup.AUDIO_PLAYER, R.string.sk_ui_audio_player_background),
    AUDIO_PLAYER_TITLE("sk_audio_player_title", SkThemeGroup.AUDIO_PLAYER, R.string.sk_ui_audio_player_title, hasFont = true),
    AUDIO_PLAYER_TIME("sk_audio_player_time", SkThemeGroup.AUDIO_PLAYER, R.string.sk_ui_audio_player_time, hasFont = true),
    AUDIO_PLAYER_CONTROLS("sk_audio_player_controls", SkThemeGroup.AUDIO_PLAYER, R.string.sk_ui_audio_player_controls),

    // Extraction progress dialog
    EXTRACT_PROGRESS_BAR("sk_extract_progress_bar", SkThemeGroup.EXTRACT_PROGRESS, R.string.sk_ui_extract_progress_bar),
    EXTRACT_PROGRESS_TRACK("sk_extract_progress_track", SkThemeGroup.EXTRACT_PROGRESS, R.string.sk_ui_extract_progress_track),
    EXTRACT_PROGRESS_NAME("sk_extract_progress_name", SkThemeGroup.EXTRACT_PROGRESS, R.string.sk_ui_extract_progress_name, hasFont = true),
    EXTRACT_PROGRESS_INFO("sk_extract_progress_info", SkThemeGroup.EXTRACT_PROGRESS, R.string.sk_ui_extract_progress_info, hasFont = true),
    EXTRACT_PROGRESS_SPEED("sk_extract_progress_speed", SkThemeGroup.EXTRACT_PROGRESS, R.string.sk_ui_extract_progress_speed, hasFont = true)
}

private fun Int.withAlphaFraction(fraction: Float): Int =
    ColorUtils.setAlphaComponent(this, (fraction * 255).toInt())

/** A disabled control shows its colour at 38 %. */
const val SK_DISABLED_FRACTION = 0.38f

fun skDisabled(color: Int): Int =
    ColorUtils.setAlphaComponent(color, (android.graphics.Color.alpha(color) * SK_DISABLED_FRACTION).toInt())

// An opaque colour the given share of the way from the background to the accent.
private fun skBlend(fraction: Float): Int = ColorUtils.blendARGB(
    skColor(SkThemeSlot.BACKGROUND), skColor(SkThemeSlot.ACCENT), fraction
) or 0xFF000000.toInt()

/**
 * What a row does — of a menu, a drawer, a list, the UI page. It decides the colour of the row's
 * icon, so a surface is read by colour before it is read by word.
 */
enum class SkIconRole(val slot: SkThemeSlot) {
    // opens, connects, loads, extracts
    OPEN(SkThemeSlot.MENU_ICON_OPEN),
    // writes, creates, renames, edits
    WRITE(SkThemeSlot.MENU_ICON_WRITE),
    // is about a person or an owner
    PERSON(SkThemeSlot.MENU_ICON_PERSON),
    // copies, shares, exports, sends
    SHARE(SkThemeSlot.MENU_ICON_SHARE),
    // views, sorts, selects, settings
    VIEW(SkThemeSlot.MENU_ICON_VIEW),
    // locks, hides, stops, cancels
    BLOCK(SkThemeSlot.MENU_ICON_BLOCK),
    // deletes, removes
    DELETE(SkThemeSlot.DANGER)
}

fun skColor(role: SkIconRole): Int = skColor(role.slot)

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

    // Text
    SkThemeSlot.TEXT_TERTIARY -> skColor(SkThemeSlot.TEXT).withAlphaFraction(0.5f)
    SkThemeSlot.TEXT_HINT -> skColor(SkThemeSlot.TEXT).withAlphaFraction(0.5f)
    SkThemeSlot.TEXT_DISABLED -> skColor(SkThemeSlot.TEXT).withAlphaFraction(SK_DISABLED_FRACTION)
    SkThemeSlot.TEXT_SECONDARY_DISABLED -> skColor(SkThemeSlot.TEXT).withAlphaFraction(0.3f)
    SkThemeSlot.HEADING -> skColor(SkThemeSlot.ACCENT)
    // 78 % of the way from the background to the accent: #C8C800 on the default palette.
    SkThemeSlot.NOTE -> skBlend(0.785f)
    SkThemeSlot.LINK -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.TEXT_SELECTION -> skColor(SkThemeSlot.ACCENT).withAlphaFraction(0.35f)
    SkThemeSlot.ICON -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.ICON_ACTIVE -> skColor(SkThemeSlot.ACCENT)

    // Buttons
    SkThemeSlot.PILL_FRAME -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.PILL_TEXT -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.PILL_GROUND -> SkUi.PALETTE_CLEAR
    SkThemeSlot.DANGER -> SkUi.PALETTE_RED

    // Dialogs
    SkThemeSlot.DIALOG_GROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.DIALOG_FRAME -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.DIALOG_TITLE -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.DIALOG_TEXT -> skColor(SkThemeSlot.TEXT)

    // Switches. Off: 55 % of the way from the background to the accent, #8C8C00 by default.
    SkThemeSlot.SWITCH_ON -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.SWITCH_OFF -> skBlend(0.55f)

    // Selection marks
    SkThemeSlot.MARK_ON -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.MARK_OFF -> skBlend(0.55f)

    // Menus. The divider: 70 % of the way from the background to the accent, #B3B300.
    SkThemeSlot.MENU_GROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.MENU_FRAME -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.MENU_LABEL -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.MENU_ROW_FRAME -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.MENU_DIVIDER -> skBlend(0.7f)
    SkThemeSlot.MENU_ICON_OPEN -> SkUi.PALETTE_ICON_GREEN
    SkThemeSlot.MENU_ICON_WRITE -> SkUi.PALETTE_ICON_BLUE
    SkThemeSlot.MENU_ICON_PERSON -> SkUi.PALETTE_ICON_CYAN
    SkThemeSlot.MENU_ICON_SHARE -> SkUi.PALETTE_ICON_ORANGE
    SkThemeSlot.MENU_ICON_VIEW -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.MENU_ICON_BLOCK -> SkUi.PALETTE_ICON_MAGENTA

    // Lists
    SkThemeSlot.LIST_DIVIDER -> skColor(SkThemeSlot.ACCENT).withAlphaFraction(0.2f)
    SkThemeSlot.LIST_PRESSED -> skColor(SkThemeSlot.ACCENT).withAlphaFraction(0.3f)
    SkThemeSlot.LIST_SELECTED -> skBlend(0.25f)
    SkThemeSlot.LIST_SELECTED_TEXT -> skColor(SkThemeSlot.TEXT)

    // Text fields
    SkThemeSlot.FIELD_TEXT -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.FIELD_FRAME -> skColor(SkThemeSlot.ACCENT)

    // System bars
    SkThemeSlot.STATUS_BAR -> skColor(SkThemeSlot.TOOLBAR_BACKGROUND)
    SkThemeSlot.NAVIGATION_BAR -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.NOTIFICATION_ACCENT -> skColor(SkThemeSlot.ACCENT)

    // Toasts and tooltips
    SkThemeSlot.TOAST_GROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.TOAST_FRAME -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.TOAST_TEXT -> skColor(SkThemeSlot.TEXT)

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
    SkThemeSlot.FILE_BADGE -> skColor(SkThemeSlot.FILE_ICONS)
    SkThemeSlot.GRID_OUTLINE -> skColor(SkThemeSlot.LIST_DIVIDER)

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

    // Audio mini-player
    SkThemeSlot.AUDIO_PLAYER_BACKGROUND -> skColor(SkThemeSlot.BACKGROUND)
    SkThemeSlot.AUDIO_PLAYER_TITLE -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.AUDIO_PLAYER_TIME -> skColor(SkThemeSlot.TEXT_SECONDARY)
    SkThemeSlot.AUDIO_PLAYER_CONTROLS -> skColor(SkThemeSlot.ACCENT)

    // Extraction progress dialog: an accent bar on a track a quarter of the way from the
    // background to the bar.
    SkThemeSlot.EXTRACT_PROGRESS_BAR -> skColor(SkThemeSlot.ACCENT)
    SkThemeSlot.EXTRACT_PROGRESS_TRACK -> ColorUtils.blendARGB(
        skColor(SkThemeSlot.BACKGROUND), skColor(SkThemeSlot.EXTRACT_PROGRESS_BAR), 0.25f
    ) or 0xFF000000.toInt()
    SkThemeSlot.EXTRACT_PROGRESS_NAME -> skColor(SkThemeSlot.TEXT)
    SkThemeSlot.EXTRACT_PROGRESS_INFO -> skColor(SkThemeSlot.TEXT_SECONDARY)
    SkThemeSlot.EXTRACT_PROGRESS_SPEED -> skColor(SkThemeSlot.ACCENT)
}

fun setSkColor(slot: SkThemeSlot, color: Int) = SkUi.setColorOverride(slot.key, color)

/** Revert a slot to its default (palette for the foundation slots, inherited otherwise). */
fun resetSkColor(slot: SkThemeSlot) = SkUi.clearColorOverride(slot.key)

@Suppress("unused")
fun Context.skColorOf(slot: SkThemeSlot): Int = skColor(slot)
