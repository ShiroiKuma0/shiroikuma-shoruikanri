/*
 * 白い熊 fork (skui): the custom share dialog's data — the manual row order,
 * plus the AutoShare command-receiver coordinates used to jump straight into
 * AutoShare's command chooser.
 *
 * Order: every row of the share dialog (Termux script targets, the AutoShare
 * row, every share activity) has a stable key, and [order] holds those keys in
 * the order they were dragged into. A row whose key isn't in the list — a newly
 * installed app — keeps its natural place and follows the ordered ones.
 *
 * The stored order spans every file type, while one dialog only ever shows the
 * handlers of the type being shared, so [saveOrder] merges rather than
 * overwrites: keys that aren't on screen right now stay anchored behind the row
 * they used to follow.
 *
 * [pinnedComponents] is the legacy pin-to-top list from before rows could be
 * dragged. It is still honoured as the *default* order, so old pins keep their
 * place until the first drag, but nothing writes it any more.
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
    private const val KEY_ORDER = "order"

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_share", Context.MODE_PRIVATE)
    }

    private fun getList(key: String): List<String> =
        try {
            val array = JSONArray(prefs.getString(key, "[]"))
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }

    private fun putList(key: String, value: List<String>) {
        prefs.edit()
            .putString(key, JSONArray().apply { value.forEach { put(it) } }.toString())
            .apply()
    }

    val pinnedComponents: List<String>
        get() = getList(KEY_PINNED)

    val order: List<String>
        get() = getList(KEY_ORDER)

    fun clearOrder() {
        prefs.edit().remove(KEY_ORDER).apply()
    }

    // Sort [items] into the manual order. Anything unknown keeps its natural
    // relative position, after the ordered rows (sortedBy is stable).
    fun <T> sortByOrder(items: List<T>, key: (T) -> String): List<T> {
        val positions = order.withIndex().associate { (index, orderKey) -> orderKey to index }
        if (positions.isEmpty()) {
            return items
        }
        return items.sortedBy { positions[key(it)] ?: Int.MAX_VALUE }
    }

    // Store [visible] — the rows of the dialog that just got rearranged — while
    // keeping every key that isn't showing right now (a handler of some other
    // file type) attached to the row it used to follow.
    fun saveOrder(visible: List<String>) {
        val visibleKeys = visible.toSet()
        val leading = mutableListOf<String>()
        val trailing = mutableMapOf<String, MutableList<String>>()
        var anchor: String? = null
        order.forEach { key ->
            if (key in visibleKeys) {
                anchor = key
            } else {
                val currentAnchor = anchor
                if (currentAnchor == null) {
                    leading += key
                } else {
                    trailing.getOrPut(currentAnchor) { mutableListOf() } += key
                }
            }
        }
        val merged = mutableListOf<String>()
        merged += leading
        visible.forEach { key ->
            merged += key
            trailing[key]?.let { merged += it }
        }
        putList(KEY_ORDER, merged)
    }
}
