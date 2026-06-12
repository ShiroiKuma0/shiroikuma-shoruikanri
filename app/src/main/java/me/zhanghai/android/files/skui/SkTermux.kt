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
import android.content.SharedPreferences
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

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_termux", Context.MODE_PRIVATE)
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
