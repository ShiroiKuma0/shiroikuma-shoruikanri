/*
 * 白い熊 fork (skui): one-click Termux script share targets.
 *
 * Unlike AutoShare (which matches commands by an opaque internal id), Termux's
 * RunCommandService takes an explicit script path plus a string-array of
 * arguments, so this is fully reliable: a target runs a chosen Termux script
 * with the selected file's real path(s) as command-line arguments.
 *
 * Requires Termux's `allow-external-apps=true` in ~/.termux/termux.properties
 * and our com.termux.permission.RUN_COMMAND.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.app.application
import org.json.JSONArray
import org.json.JSONObject

const val TERMUX_PACKAGE = "com.termux"
const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
const val TERMUX_ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
const val TERMUX_EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
const val TERMUX_EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
const val TERMUX_EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
const val TERMUX_EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
const val TERMUX_HOME = "/data/data/com.termux/files/home"

class SkTermuxScript(
    val label: String,
    val scriptPath: String,
    // false = run in a visible terminal session; true = run in the background.
    val background: Boolean
) {
    // Expand ~ / a bare or relative path to an absolute path under the Termux home.
    val absolutePath: String
        get() =
            when {
                scriptPath.startsWith("/") -> scriptPath
                scriptPath.startsWith("~/") -> "$TERMUX_HOME/${scriptPath.substring(2)}"
                scriptPath == "~" -> TERMUX_HOME
                else -> "$TERMUX_HOME/$scriptPath"
            }
}

object SkTermux {
    private const val KEY_SCRIPTS = "scripts"
    private const val KEY_STAGING_DIR = "staging_dir"
    private const val KEY_ONE_TARGET = "one_target"

    // Where inbound shares (SkShareReceiverActivity) stage a copy when the shared
    // item has no readable real path. Settable on the 白い熊 UI page. Termux reads
    // /storage/emulated/0/… directly, so this must be a plain shared-storage path.
    const val DEFAULT_STAGING_DIR = "/storage/emulated/0/tmp"

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_termux", Context.MODE_PRIVATE)
    }

    var stagingDir: String
        get() = prefs.getString(KEY_STAGING_DIR, DEFAULT_STAGING_DIR)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_STAGING_DIR
        set(value) {
            prefs.edit()
                .putString(KEY_STAGING_DIR, value.trim().ifBlank { DEFAULT_STAGING_DIR })
                .apply()
        }

    // true  = one share tile that fires the first script in one tap (default);
    // false = one share tile that opens the chooser of all scripts (by name).
    // Either way the app exposes a single SEND tile so EMUI doesn't group it.
    var oneTargetMode: Boolean
        get() = prefs.getBoolean(KEY_ONE_TARGET, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ONE_TARGET, value).apply()
            SkShareShortcuts.sync(application)
        }

    var scripts: List<SkTermuxScript>
        get() =
            try {
                val array = JSONArray(prefs.getString(KEY_SCRIPTS, "[]"))
                (0 until array.length()).map {
                    val o = array.getJSONObject(it)
                    SkTermuxScript(
                        o.getString("label"),
                        o.getString("path"),
                        o.optBoolean("background", false)
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        private set(value) {
            val array = JSONArray()
            value.forEach {
                array.put(
                    JSONObject()
                        .put("label", it.label)
                        .put("path", it.scriptPath)
                        .put("background", it.background)
                )
            }
            prefs.edit().putString(KEY_SCRIPTS, array.toString()).apply()
            // Keep the system-share Direct-Share targets in sync with the script list.
            SkShareShortcuts.sync(application)
        }

    fun isInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }

    // Fire RunCommandService for [script] with [paths] as its command-line arguments
    // ($1, $2…). Returns false if Termux couldn't be started (most commonly
    // allow-external-apps not enabled, or the RUN_COMMAND permission denied).
    fun run(context: Context, script: SkTermuxScript, paths: List<String>): Boolean {
        val intent = Intent(TERMUX_ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
            putExtra(TERMUX_EXTRA_COMMAND_PATH, script.absolutePath)
            putExtra(TERMUX_EXTRA_ARGUMENTS, paths.toTypedArray())
            putExtra(TERMUX_EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(TERMUX_EXTRA_BACKGROUND, script.background)
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun add(script: SkTermuxScript) {
        scripts = scripts + script
    }

    fun update(index: Int, script: SkTermuxScript) {
        scripts = scripts.toMutableList().also {
            if (index in it.indices) it[index] = script
        }
    }

    fun remove(index: Int) {
        scripts = scripts.toMutableList().also {
            if (index in it.indices) it.removeAt(index)
        }
    }

    fun move(from: Int, to: Int) {
        val list = scripts.toMutableList()
        if (from in list.indices && to in list.indices && from != to) {
            list.add(to, list.removeAt(from))
            scripts = list
        }
    }
}
