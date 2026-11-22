/*
 * 白い熊 fork (skui): what a menu row needs to know to be drawn by the black-yellow theme's
 * menu rule (SkPillMenuItemView): its icon when the menu resource gave it none, whether it
 * opens something (a › at the right end), and what it does — which colours its icon. Menu
 * items carry none of that, so this table supplies it by item id.
 *
 * Frame and label of a row stay in the menu colours — the danger role for a row that deletes —
 * while the icon tells what the row does: open / extract green, write / create blue, owner
 * cyan, copy / share orange, view / select the accent, hide / stop magenta, delete red. Each
 * is a settable role (UI page → Menus).
 *
 * A new menu item: add a line to ROWS. One without a line still becomes a pill, with a
 * view-coloured icon column and no ›.
 */

package me.zhanghai.android.files.skui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import me.zhanghai.android.files.R

@SuppressLint("RestrictedApi")
object SkMenuPills {
    const val TOGGLE_HEIGHT_DP = 36 // a toggle row: the switch and little more
    const val PILL_MARGIN_DP = 3 // above and below a pill
    const val SIDE_MARGIN_DP = 8 // from the pill to the edge of the menu

    class Row(val role: SkIconRole, val opens: Boolean = false, val icon: Int = 0)

    private val ROWS: Map<Int, Row> = mapOf(
        // a file's own menu (the ⋮ of a row, and a long press)
        R.id.action_select to Row(SkIconRole.VIEW, icon = R.drawable.sk_ic_check_box),
        R.id.action_open_with to Row(SkIconRole.OPEN, true, R.drawable.sk_ic_open_with),
        R.id.action_sk_scan_virustotal to Row(SkIconRole.BLOCK, true, R.drawable.sk_ic_shield),
        R.id.action_open_in_new_tab to Row(SkIconRole.OPEN, icon = R.drawable.sk_ic_tab),
        R.id.action_cut to Row(SkIconRole.WRITE, icon = R.drawable.cut_icon_control_normal_24dp),
        R.id.action_copy to Row(SkIconRole.SHARE, icon = R.drawable.copy_icon_control_normal_24dp),
        R.id.action_delete to
            Row(SkIconRole.DELETE, true, R.drawable.delete_icon_control_normal_24dp),
        R.id.action_rename to Row(SkIconRole.WRITE, true, R.drawable.sk_ic_edit),
        R.id.action_extract to
            Row(SkIconRole.OPEN, icon = R.drawable.extract_icon_control_normal_24dp),
        R.id.action_archive to Row(SkIconRole.WRITE, true, R.drawable.sk_ic_archive),
        R.id.action_share to
            Row(SkIconRole.SHARE, true, R.drawable.sk_share_icon_control_normal_24dp),
        R.id.action_copy_path to
            Row(SkIconRole.SHARE, icon = R.drawable.copy_icon_control_normal_24dp),
        R.id.action_add_bookmark to Row(SkIconRole.WRITE, icon = R.drawable.sk_ic_bookmark),
        R.id.action_create_shortcut to Row(SkIconRole.SHARE, icon = R.drawable.sk_ic_home),
        R.id.action_properties to
            Row(SkIconRole.VIEW, true, R.drawable.information_icon_white_24dp),
        // the file list's top bar and its ⋮
        R.id.action_search to Row(SkIconRole.VIEW, true),
        R.id.action_view_sort to Row(SkIconRole.VIEW, true),
        R.id.action_sk_view to Row(SkIconRole.VIEW, true),
        R.id.action_new_tab to Row(SkIconRole.WRITE, icon = R.drawable.sk_ic_tab),
        R.id.action_new_task to Row(SkIconRole.OPEN, true, R.drawable.sk_ic_window),
        R.id.action_navigate_up to Row(SkIconRole.VIEW, icon = R.drawable.sk_ic_arrow_up),
        R.id.action_navigate_to to
            Row(SkIconRole.OPEN, true, R.drawable.arrow_end_icon_white_24dp),
        R.id.action_refresh to Row(SkIconRole.OPEN, icon = R.drawable.sk_ic_refresh),
        R.id.action_select_all to
            Row(SkIconRole.VIEW, icon = R.drawable.sk_select_all_icon_control_normal_24dp),
        R.id.action_show_hidden_files to Row(SkIconRole.VIEW, icon = R.drawable.sk_ic_eye),
        R.id.action_open_in_terminal to Row(SkIconRole.OPEN, true, R.drawable.sk_ic_terminal),
        // a breadcrumb's long press
        R.id.action_open_in_new_task to Row(SkIconRole.OPEN, true, R.drawable.sk_ic_window),
        R.id.action_path_diagnostics to Row(SkIconRole.VIEW, true, R.drawable.sk_ic_bug),
        // the FTP server address
        R.id.action_sk_copy_url to
            Row(SkIconRole.SHARE, icon = R.drawable.copy_icon_control_normal_24dp),
        R.id.action_sk_copy_password to Row(SkIconRole.BLOCK, icon = R.drawable.sk_ic_key),
        // paste and pick bars
        R.id.action_paste to Row(SkIconRole.WRITE),
        R.id.action_open to Row(SkIconRole.OPEN),
        R.id.action_create to Row(SkIconRole.WRITE),
        // the text editor
        R.id.action_save to Row(SkIconRole.WRITE),
        R.id.action_reload to Row(SkIconRole.OPEN, icon = R.drawable.sk_ic_refresh),
        R.id.action_encoding to Row(SkIconRole.VIEW, true, R.drawable.sk_ic_encoding)
    )

    private fun row(item: MenuItem) = ROWS[item.itemId]

    fun label() = skColor(SkThemeSlot.MENU_LABEL)

    fun frame() = skColor(SkThemeSlot.MENU_ROW_FRAME)

    fun danger() = skColor(SkThemeSlot.DANGER)

    fun divider() = skColor(SkThemeSlot.MENU_DIVIDER)

    fun iconColor(item: MenuItem) = skColor(row(item)?.role ?: SkIconRole.VIEW)

    /** The table icon of an item that brought none, or 0. */
    fun iconFor(item: MenuItem) = row(item)?.icon ?: 0

    fun opens(item: MenuItem): Boolean =
        item.hasSubMenu() || row(item)?.opens == true
            || item.title?.toString()?.trim()?.endsWith("…") == true

    fun isDestructive(item: MenuItem) = row(item)?.role == SkIconRole.DELETE

    /** The disabled title line a menu opens with. */
    private fun isHeader(item: MenuItem) =
        !item.isEnabled && item.itemId == 0 && !item.hasSubMenu()

    /** A row with a switch: checkable, and not one of an exclusive (radio) group. */
    fun isToggle(item: MenuItemImpl) = item.isCheckable && !item.isExclusiveCheckable

    /** An action row: everything that is not a toggle, a radio choice or a header. */
    fun isPill(item: MenuItemImpl) = !item.isCheckable && !isHeader(item)

    /**
     * Whether the rows of this menu keep an icon column. A list of pure choices with no icon
     * anywhere stays iconless; as soon as one row has an icon every row keeps the column, so
     * the labels share one left edge.
     */
    fun anyIcon(menu: MenuBuilder?): Boolean {
        menu ?: return true
        return try {
            menu.visibleItems.any { it.icon != null || iconFor(it) != 0 }
        } catch (ignored: Throwable) {
            false
        }
    }

    /**
     * A menu never scrolls while the screen has room. A stock popup is given only the larger
     * of the room above and the room below its anchor; showing it shifted up by what is
     * missing below lets it use the whole height. Returns that shift (negative), or null when
     * the menu fits below its anchor. The height is estimated from the rows as
     * SkPillMenuItemView draws them, a little generously — an estimate that is too large only
     * moves the menu slightly higher.
     */
    fun verticalOffset(context: Context, anchor: View, menu: Menu): Int? {
        return try {
            val builder = menu as? MenuBuilder ?: return null
            val metrics = context.resources.displayMetrics
            val density = metrics.density
            val value = TypedValue()
            if (!context.theme.resolveAttribute(
                    androidx.appcompat.R.attr.dropdownListPreferredItemHeight, value, true
                )) {
                return null
            }
            val rowHeight = value.getDimension(metrics)
            var needed = 24 * density
            var group: Int? = null
            for (item in builder.visibleItems) {
                needed += when {
                    isToggle(item) -> TOGGLE_HEIGHT_DP * density
                    isPill(item) -> rowHeight + 2 * PILL_MARGIN_DP * density
                    else -> rowHeight
                }
                if (group != null && group != item.groupId && builder.isGroupDividerEnabled) {
                    needed += 9 * density
                }
                group = item.groupId
            }
            val attributes: TypedArray = context.obtainStyledAttributes(
                null, intArrayOf(androidx.appcompat.R.attr.overlapAnchor),
                androidx.appcompat.R.attr.popupMenuStyle, 0
            )
            val overlap = try {
                attributes.getBoolean(0, false)
            } finally {
                attributes.recycle()
            }
            val frame = Rect()
            anchor.getWindowVisibleDisplayFrame(frame)
            val position = IntArray(2)
            anchor.getLocationOnScreen(position)
            val base = position[1] + if (overlap) 0 else anchor.height
            val below = frame.bottom - base
            if (needed <= below) {
                null
            } else {
                Math.round(maxOf((frame.top - base).toFloat(), below - needed))
            }
        } catch (ignored: Throwable) {
            null
        }
    }
}
