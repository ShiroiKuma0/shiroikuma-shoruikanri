/*
 * 白い熊 fork (skui): line separators between files, per listing view —
 * thickness (0 = none) and color, with global defaults (UI page) and
 * per-folder overrides (the address-line sheet), persisted by path.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.filelist.FileViewType

class SkSeparatorEffective(val thicknessDp: Int, val color: Int)

object SkSeparators {
    // Shaded pure yellow, matching the rest of the 白い熊 look.
    const val DEFAULT_COLOR = 0x33FFFF00

    const val UNSET = -1

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences("sk_separators", Context.MODE_PRIVATE)
    }

    private fun globalThicknessKey(viewType: FileViewType) = "g_t|${viewType.name}"
    private fun globalColorKey(viewType: FileViewType) = "g_c|${viewType.name}"
    private fun folderThicknessKey(path: Path, viewType: FileViewType) =
        "f_t|${viewType.name}|${path.toUri()}"
    private fun folderColorKey(path: Path, viewType: FileViewType) =
        "f_c|${viewType.name}|${path.toUri()}"

    fun getGlobalThickness(viewType: FileViewType): Int =
        prefs.getInt(globalThicknessKey(viewType), 0)

    fun setGlobalThickness(viewType: FileViewType, thicknessDp: Int) {
        prefs.edit().putInt(globalThicknessKey(viewType), thicknessDp).apply()
        SkUi.notifyChanged()
    }

    fun getGlobalColor(viewType: FileViewType): Int =
        prefs.getInt(globalColorKey(viewType), DEFAULT_COLOR)

    fun setGlobalColor(viewType: FileViewType, color: Int?) {
        prefs.edit().apply {
            if (color != null) {
                putInt(globalColorKey(viewType), color)
            } else {
                remove(globalColorKey(viewType))
            }
        }.apply()
        SkUi.notifyChanged()
    }

    fun setFolderThickness(path: Path, viewType: FileViewType, thicknessDp: Int) {
        prefs.edit().putInt(folderThicknessKey(path, viewType), thicknessDp).apply()
        SkUi.notifyChanged()
    }

    fun setFolderColor(path: Path, viewType: FileViewType, color: Int?) {
        prefs.edit().apply {
            if (color != null) {
                putInt(folderColorKey(path, viewType), color)
            } else {
                remove(folderColorKey(path, viewType))
            }
        }.apply()
        SkUi.notifyChanged()
    }

    fun clearFolder(path: Path, viewType: FileViewType) {
        prefs.edit()
            .remove(folderThicknessKey(path, viewType))
            .remove(folderColorKey(path, viewType))
            .apply()
        SkUi.notifyChanged()
    }

    fun effective(path: Path?, viewType: FileViewType): SkSeparatorEffective {
        val thickness = path
            ?.let { prefs.getInt(folderThicknessKey(it, viewType), UNSET) }
            ?.takeIf { it != UNSET }
            ?: getGlobalThickness(viewType)
        val color = path
            ?.takeIf { prefs.contains(folderColorKey(it, viewType)) }
            ?.let { prefs.getInt(folderColorKey(it, viewType), DEFAULT_COLOR) }
            ?: getGlobalColor(viewType)
        return SkSeparatorEffective(thickness, color)
    }

    fun effectiveFolderThickness(path: Path, viewType: FileViewType): Int =
        effective(path, viewType).thicknessDp

    fun effectiveFolderColor(path: Path, viewType: FileViewType): Int =
        effective(path, viewType).color
}

/**
 * Draws the configured separator line under every file row/cell, plus a
 * vertical line between columns when the view has more than one (Wrapped).
 */
class SkSeparatorDecoration : RecyclerView.ItemDecoration() {
    var style: SkSeparatorEffective? = null

    private val paint = Paint()

    private fun spanInfoOf(view: View, parent: RecyclerView): Pair<Int, Int>? {
        val layoutManager = parent.layoutManager as? GridLayoutManager ?: return null
        val layoutParams = view.layoutParams as? GridLayoutManager.LayoutParams ?: return null
        return layoutParams.spanIndex to layoutManager.spanCount
    }

    override fun getItemOffsets(
        outRect: android.graphics.Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val style = style?.takeIf { it.thicknessDp > 0 } ?: return
        val thicknessPx = (style.thicknessDp * view.resources.displayMetrics.density).toInt()
        outRect.bottom = thicknessPx
        // Room for the vertical line between columns.
        val (spanIndex, spanCount) = spanInfoOf(view, parent) ?: return
        if (spanCount > 1 && spanIndex < spanCount - 1) {
            outRect.right = thicknessPx
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val style = style?.takeIf { it.thicknessDp > 0 } ?: return
        val thicknessPx = style.thicknessDp * parent.resources.displayMetrics.density
        paint.color = style.color
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val top = child.bottom + child.translationY
            canvas.drawRect(
                child.left.toFloat(), top, child.right + thicknessPx, top + thicknessPx, paint
            )
            // The vertical line at the right edge of every column but the last.
            val spanInfo = spanInfoOf(child, parent)
            if (spanInfo != null && spanInfo.second > 1 && spanInfo.first < spanInfo.second - 1) {
                canvas.drawRect(
                    child.right.toFloat(), child.top + child.translationY,
                    child.right + thicknessPx, top + thicknessPx, paint
                )
            }
        }
    }
}
