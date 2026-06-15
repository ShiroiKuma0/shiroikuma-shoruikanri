/*
 * 白い熊 fork (skui): toasts ("flashes" like the editor's "Saved") get the 白い熊 look —
 * solid black background, yellow border, yellow text — to match dialogs and the rest of the UI.
 *
 * Uses a custom toast view (deprecated but functional in the foreground, which is when these
 * flashes appear). When the sk theme is disabled this returns null and the caller falls back to a
 * plain system toast.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import me.zhanghai.android.files.R

object SkToast {
    fun make(context: Context, text: CharSequence, duration: Int): Toast? {
        if (!SkUi.isSkThemeEnabled) {
            return null
        }
        val density = context.resources.displayMetrics.density
        val paddingHorizontal = (20 * density).toInt()
        val paddingVertical = (12 * density).toInt()
        val view = TextView(context).apply {
            this.text = text
            setTextColor(SkUi.PALETTE_YELLOW)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = AppCompatResources.getDrawable(context, R.drawable.sk_toast_background)
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        }
        @Suppress("DEPRECATION")
        return Toast(context).apply {
            this.duration = duration
            this.view = view
        }
    }
}
