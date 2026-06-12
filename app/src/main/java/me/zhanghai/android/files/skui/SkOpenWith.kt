/*
 * 白い熊 fork (skui): remembered "open with" defaults per file type, used by
 * the internal open-with dialog and consulted on every plain open.
 */

package me.zhanghai.android.files.skui

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.MimeType

object SkOpenWith {
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_open_with", Context.MODE_PRIVATE)
    }

    fun getDefault(mimeType: MimeType): ComponentName? =
        prefs.getString(mimeType.value, null)?.let { ComponentName.unflattenFromString(it) }

    fun setDefault(mimeType: MimeType, component: ComponentName) {
        prefs.edit().putString(mimeType.value, component.flattenToString()).apply()
    }

    fun clearDefault(mimeType: MimeType) {
        prefs.edit().remove(mimeType.value).apply()
    }
}
