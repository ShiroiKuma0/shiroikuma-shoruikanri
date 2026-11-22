/*
 * 白い熊 fork (skui): how a colour role reaches the XML this fork does not draw itself.
 *
 * The fork follows upstream's releases, so upstream's layouts are not edited for colour (the
 * "mixed way", decided by 白い熊 on 2026-11-22). Instead the 白い熊 theme overlay
 * (ThemeOverlay.Sk in sk_theme.xml) points every colour attribute those layouts read at a role
 * colour resource, `@color/sk_role_…`, and this object lays the stored colours over those
 * resources at run time with a ResourcesLoader (Android 11 and later). Below Android 11 there
 * is no loader: the values written in sk_roles.xml show, which are each role's default — so an
 * older system has the theme, but not the colours 白い熊 set for those surfaces.
 *
 * - apply() must run on a context before anything is inflated from it: AppActivity does it from
 *   getTheme(), the application at start.
 * - changed() is called whenever a colour is stored. Each Resources then gets a fresh loader the
 *   next time apply() sees it, and an activity built from the old colours recreates itself.
 *
 * Everything the fork draws itself — pills, switches, menu rows, dialog panels, the UI page —
 * reads its role directly through skColor() and needs none of this.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.material.color.SkColorLoaderBridge
import me.zhanghai.android.files.R
import java.util.WeakHashMap

object SkPalette {
    private const val TAG = "SkPalette"

    private class Applied(val loader: ResourcesLoader, val generation: Int)

    private val applied = WeakHashMap<Resources, Applied>()

    @Volatile
    var generation = 1
        private set

    fun changed() {
        ++generation
    }

    /**
     * Lays the roles over the context's resources, and over the application's as well — those
     * are a separate object, read by toasts, notifications and services.
     */
    @Synchronized
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !SkUi.isSkThemeEnabled) {
            return
        }
        applyTo(context)
        val application = context.applicationContext
        if (application != null && application !== context) {
            applyTo(application)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyTo(context: Context) {
        val resources = context.resources
        val current = applied[resources]
        if (current != null && current.generation == generation) {
            return
        }
        try {
            val loader = SkColorLoaderBridge.create(context, resourceColors()) ?: return
            if (current != null) {
                resources.removeLoaders(current.loader)
            }
            resources.addLoaders(loader)
            applied[resources] = Applied(loader, generation)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not lay the colour roles over the resources", e)
        }
    }

    // Which role feeds which colour resource. A new `sk_role_…` resource gets a line here.
    private fun resourceColors(): Map<Int, Int> = mapOf(
        R.color.sk_role_background to skColor(SkThemeSlot.BACKGROUND),
        R.color.sk_role_surface to skColor(SkThemeSlot.BACKGROUND),
        R.color.sk_role_accent to skColor(SkThemeSlot.ACCENT),
        R.color.sk_role_on_accent to skColor(SkThemeSlot.BACKGROUND),
        R.color.sk_role_text to skColor(SkThemeSlot.TEXT),
        R.color.sk_role_text_disabled to skColor(SkThemeSlot.TEXT_DISABLED),
        R.color.sk_role_text_secondary to skColor(SkThemeSlot.TEXT_SECONDARY),
        R.color.sk_role_text_secondary_disabled to skColor(SkThemeSlot.TEXT_SECONDARY_DISABLED),
        R.color.sk_role_text_tertiary to skColor(SkThemeSlot.TEXT_TERTIARY),
        R.color.sk_role_text_hint to skColor(SkThemeSlot.TEXT_HINT),
        R.color.sk_role_text_selection to skColor(SkThemeSlot.TEXT_SELECTION),
        R.color.sk_role_link to skColor(SkThemeSlot.LINK),
        R.color.sk_role_icon to skColor(SkThemeSlot.ICON),
        R.color.sk_role_icon_active to skColor(SkThemeSlot.ICON_ACTIVE),
        R.color.sk_role_pressed to skColor(SkThemeSlot.LIST_PRESSED),
        R.color.sk_role_outline to skColor(SkThemeSlot.FIELD_FRAME),
        R.color.sk_role_field_text to skColor(SkThemeSlot.FIELD_TEXT),
        R.color.sk_role_list_divider to skColor(SkThemeSlot.LIST_DIVIDER),
        R.color.sk_role_selected to skColor(SkThemeSlot.LIST_SELECTED),
        R.color.sk_role_on_selected to skColor(SkThemeSlot.LIST_SELECTED_TEXT),
        R.color.sk_role_app_bar to skColor(SkThemeSlot.TOOLBAR_BACKGROUND),
        R.color.sk_role_danger to skColor(SkThemeSlot.DANGER),
        R.color.sk_role_dialog_ground to skColor(SkThemeSlot.DIALOG_GROUND),
        R.color.sk_role_dialog_frame to skColor(SkThemeSlot.DIALOG_FRAME),
        R.color.sk_role_dialog_title to skColor(SkThemeSlot.DIALOG_TITLE),
        R.color.sk_role_dialog_text to skColor(SkThemeSlot.DIALOG_TEXT),
        R.color.sk_role_menu_ground to skColor(SkThemeSlot.MENU_GROUND),
        R.color.sk_role_menu_frame to skColor(SkThemeSlot.MENU_FRAME),
        R.color.sk_role_menu_label to skColor(SkThemeSlot.MENU_LABEL)
    )
}
