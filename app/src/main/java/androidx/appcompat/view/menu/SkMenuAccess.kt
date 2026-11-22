package androidx.appcompat.view.menu

import android.annotation.SuppressLint

/**
 * 白い熊 fork: the menu a menu item belongs to. MenuItemImpl keeps it in a package-private
 * field and offers no getter, and a pill-menu row has to know whether any of its neighbours
 * carries an icon — hence this one object in AppCompat's package (see
 * me.zhanghai.android.files.skui.SkPillMenuItemView).
 */
@SuppressLint("RestrictedApi")
object SkMenuAccess {
    fun menuOf(item: MenuItemImpl?): MenuBuilder? = try {
        item?.mMenu
    } catch (ignored: Throwable) {
        null
    }
}
