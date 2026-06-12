/*
 * 白い熊 fork (skui): grid view styling — text size, image width/height,
 * horizontal/vertical padding, the gap between the image and the file name,
 * text-over-image overlay and text visibility. Global defaults live in SkUi;
 * on top of them a folder can carry its own override (keyed by path), so the
 * folder opens with its style in any tab. Unset fields (-1, or 0 for the text
 * size) inherit.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.SharedPreferences
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import org.json.JSONObject

const val SK_GRID_UNSET = -1

data class SkGridStyle(
    val textSizeSp: Int = 0,
    val imageWidthDp: Int = SK_GRID_UNSET,
    val imageHeightDp: Int = SK_GRID_UNSET,
    val paddingHDp: Int = SK_GRID_UNSET,
    val paddingVDp: Int = SK_GRID_UNSET,
    val textGapDp: Int = SK_GRID_UNSET,
    // Tri-state booleans: -1 = inherit, 0 = false, 1 = true.
    val textOverlay: Int = SK_GRID_UNSET,
    val textVisible: Int = SK_GRID_UNSET
) {
    val isEmpty: Boolean
        get() =
            textSizeSp <= 0 && imageWidthDp == SK_GRID_UNSET && imageHeightDp == SK_GRID_UNSET &&
                paddingHDp == SK_GRID_UNSET && paddingVDp == SK_GRID_UNSET &&
                textGapDp == SK_GRID_UNSET && textOverlay == SK_GRID_UNSET &&
                textVisible == SK_GRID_UNSET
}

// The effective, fully-resolved style the adapter renders with.
class SkGridEffective(
    val textSizeSp: Int, // 0 = leave the GRID_TEXT slot's own size
    val imageWidthDp: Int,
    val imageHeightDp: Int,
    val paddingHDp: Int,
    val paddingVDp: Int,
    val textGapDp: Int,
    val isTextOverlay: Boolean,
    val isTextVisible: Boolean
)

object SkGridStyles {
    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_grid_styles", Context.MODE_PRIVATE)
    }

    private fun keyOf(path: Path): String = path.toUri().toString()

    fun get(path: Path): SkGridStyle? {
        val json = prefs.getString(keyOf(path), null) ?: return null
        return try {
            val o = JSONObject(json)
            SkGridStyle(
                o.optInt("ts", 0),
                o.optInt("iw", SK_GRID_UNSET),
                o.optInt("ih", SK_GRID_UNSET),
                o.optInt("ph", SK_GRID_UNSET),
                o.optInt("pv", SK_GRID_UNSET),
                o.optInt("tg", SK_GRID_UNSET),
                o.optInt("to", SK_GRID_UNSET),
                o.optInt("tv", SK_GRID_UNSET)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun set(path: Path, style: SkGridStyle?) {
        if (style == null || style.isEmpty) {
            prefs.edit().remove(keyOf(path)).apply()
        } else {
            val o = JSONObject()
                .put("ts", style.textSizeSp)
                .put("iw", style.imageWidthDp)
                .put("ih", style.imageHeightDp)
                .put("ph", style.paddingHDp)
                .put("pv", style.paddingVDp)
                .put("tg", style.textGapDp)
                .put("to", style.textOverlay)
                .put("tv", style.textVisible)
            prefs.edit().putString(keyOf(path), o.toString()).apply()
        }
        SkUi.notifyChanged()
    }
}

fun skEffectiveGridStyle(path: Path?): SkGridEffective {
    val folder = path?.let { SkGridStyles.get(it) }
    fun resolve(folderValue: Int?, default: Int): Int =
        if (folderValue != null && folderValue != SK_GRID_UNSET) folderValue else default
    fun resolveBoolean(folderValue: Int?, default: Boolean): Boolean =
        when (folderValue) {
            0 -> false
            1 -> true
            else -> default
        }
    return SkGridEffective(
        if (folder != null && folder.textSizeSp > 0) {
            folder.textSizeSp
        } else {
            SkUi.getFontSize(SkThemeSlot.GRID_TEXT.key)
        },
        resolve(folder?.imageWidthDp, SkUi.gridImageWidthDp),
        resolve(folder?.imageHeightDp, SkUi.gridImageHeightDp),
        resolve(folder?.paddingHDp, SkUi.gridPaddingHDp),
        resolve(folder?.paddingVDp, SkUi.gridPaddingVDp),
        resolve(folder?.textGapDp, SkUi.gridTextGapDp),
        resolveBoolean(folder?.textOverlay, SkUi.isGridTextOverlay),
        resolveBoolean(folder?.textVisible, SkUi.isGridTextVisible)
    )
}
