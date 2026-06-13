/*
 * 白い熊 fork (skui): publishes the inbound system-share targets.
 *
 * Each user-defined Termux script (SkTermux) is mirrored as a dynamic "sharing
 * shortcut" so it shows up as its own one-tap entry in the Direct-Share row of
 * the Android system share sheet (e.g. screenshot → Share → «script label»).
 * Picking one launches SkShareReceiverActivity with the shortcut id, which runs
 * that script on the shared file(s). The shortcuts are excluded from the
 * launcher surface so they don't clutter the app's long-press menu.
 *
 * Direct Share is system-ranked and some launchers (notably EMUI) never surface
 * it, and EMUI also groups every SEND target of one app under a single tile with
 * a disambiguation popup. So for a genuine one-tap tile we keep the app exposing
 * exactly ONE plain SEND tile, toggled via PackageManager (syncShareTargets):
 * one-target mode enables SkShareSlot1 (fires the first script directly), multi
 * mode enables the SkShareChooser alias (opens the in-app chooser). The upstream
 * «保存» Save-as tile is hidden whenever we have our own targets so it can't add a
 * second tile and reintroduce grouping.
 */

package me.zhanghai.android.files.skui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import me.zhanghai.android.files.R

object SkShareShortcuts {
    // Must match the <category> in res/xml/shortcuts.xml's <share-target>.
    const val CATEGORY = "me.zhanghai.android.files.skui.category.TERMUX_TARGET"

    // Static share slots: SkShareSlot1..SLOT_COUNT activity-aliases (AndroidManifest).
    // Slot N (1-based) maps to the Nth Termux script. Must match the manifest count.
    const val SLOT_COUNT = 5
    const val SLOT_CLASS_PREFIX = "SkShareSlot"
    private const val SLOT_PACKAGE = "me.zhanghai.android.files.skui"

    // The «白い熊 書類管理» chooser tile (multi-target mode) and the upstream «保存»
    // Save-as activity, both toggled so the app exposes exactly one SEND tile.
    private const val CHOOSER_CLASS = "$SLOT_PACKAGE.SkShareChooser"
    private const val SAVE_AS_CLASS = "me.zhanghai.android.files.viewer.saveas.SaveAsActivity"

    private fun slotClassName(slot: Int): String = "$SLOT_PACKAGE.$SLOT_CLASS_PREFIX$slot"

    private const val ID_PREFIX = "sk_termux_"

    fun shortcutId(index: Int): String = "$ID_PREFIX$index"

    fun scriptIndexForShortcutId(id: String?): Int? =
        id?.removePrefix(ID_PREFIX)?.toIntOrNull()

    // Re-publish the dynamic sharing shortcuts to match the current script list.
    // Runs off the main thread (the ShortcutManager calls are IPC).
    fun sync(context: Context) {
        val appContext = context.applicationContext
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            try {
                syncBlocking(appContext)
            } catch (e: Exception) {
                // Best effort; the manifest SEND filter still provides the fallback entry.
            }
        }
    }

    private fun syncBlocking(context: Context) {
        syncShareTargets(context)
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            .coerceAtLeast(1)
        val scripts = SkTermux.scripts.take(max)
        if (scripts.isEmpty()) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            return
        }
        val icon = IconCompat.createWithResource(context, R.mipmap.launcher_icon)
        val shortcuts = scripts.mapIndexed { index, script ->
            val label = script.label.ifEmpty { context.getString(R.string.sk_termux_title) }
            val intent = Intent(context, SkShareReceiverActivity::class.java).apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SHORTCUT_ID, shortcutId(index))
            }
            ShortcutInfoCompat.Builder(context, shortcutId(index))
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .setLongLived(true)
                .setCategories(setOf(CATEGORY))
                .setExcludedFromSurfaces(ShortcutInfoCompat.SURFACE_LAUNCHER)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    // Keep the app exposing exactly ONE SEND tile (EMUI groups same-package tiles):
    //   no scripts  → no tile of ours; leave the upstream «保存» Save-as tile.
    //   one-target  → just SkShareSlot1 (fires the first script in one tap).
    //   multi       → just the chooser tile (opens the in-app script chooser).
    // «保存» is hidden whenever we have our own targets, so it never causes grouping.
    private fun syncShareTargets(context: Context) {
        val count = SkTermux.scripts.size
        val chooserEnabled: Boolean
        val enabledSlot: Int // 1-based slot to enable, 0 = none
        val saveAsEnabled: Boolean
        when {
            count == 0 -> { chooserEnabled = false; enabledSlot = 0; saveAsEnabled = true }
            SkTermux.oneTargetMode -> { chooserEnabled = false; enabledSlot = 1; saveAsEnabled = false }
            else -> { chooserEnabled = true; enabledSlot = 0; saveAsEnabled = false }
        }
        setComponentEnabled(context, CHOOSER_CLASS, chooserEnabled)
        for (slot in 1..SLOT_COUNT) {
            setComponentEnabled(context, slotClassName(slot), slot == enabledSlot)
        }
        setComponentEnabled(context, SAVE_AS_CLASS, saveAsEnabled)
    }

    // Set an explicit enabled/disabled state, but only when it actually changes
    // (avoids needless PackageManager churn on every sync).
    private fun setComponentEnabled(context: Context, className: String, enabled: Boolean) {
        val component = ComponentName(context, className)
        val packageManager = context.packageManager
        val desired = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(component) != desired) {
            packageManager.setComponentEnabledSetting(
                component, desired, PackageManager.DONT_KILL_APP
            )
        }
    }
}
