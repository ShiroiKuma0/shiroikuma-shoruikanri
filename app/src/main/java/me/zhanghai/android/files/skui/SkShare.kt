/*
 * 白い熊 fork (skui): the custom share dialog's data — pinned top candidates,
 * plus the AutoShare command-receiver coordinates used to jump straight into
 * AutoShare's command chooser.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.SharedPreferences
import me.zhanghai.android.files.app.application
import org.json.JSONArray

object SkShare {
    const val AUTOSHARE_PACKAGE = "com.joaomgcd.autoshare"
    const val AUTOSHARE_COMMAND_ACTIVITY =
        "com.joaomgcd.autoshare.activity.ActivityReceiveShareCommand"

    private const val KEY_PINNED = "pinned"

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_share", Context.MODE_PRIVATE)
    }

    var pinnedComponents: List<String>
        get() =
            try {
                val array = JSONArray(prefs.getString(KEY_PINNED, "[]"))
                (0 until array.length()).map { array.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }
        private set(value) {
            prefs.edit()
                .putString(KEY_PINNED, JSONArray().apply { value.forEach { put(it) } }.toString())
                .apply()
        }

    fun isPinned(component: String): Boolean = component in pinnedComponents

    fun togglePinned(component: String) {
        pinnedComponents = if (isPinned(component)) {
            pinnedComponents - component
        } else {
            pinnedComponents + component
        }
    }
}
