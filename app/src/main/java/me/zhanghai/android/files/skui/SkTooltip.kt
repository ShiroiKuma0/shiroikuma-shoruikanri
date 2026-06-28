/*
 * 白い熊 fork (skui): black/yellow long-press tooltips. The platform tooltip frame
 * (android:tooltipFrameBackground) is a private attribute and can't be themed in
 * XML — in our dark theme the framework draws it as a light box that clashes. So
 * we suppress the framework tooltip on toolbar action buttons and show our own
 * popup instead: solid black with a yellow border (sk_popup_background) and yellow
 * text, matching the popup menus.
 */

package me.zhanghai.android.files.skui

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.children
import me.zhanghai.android.files.R

object SkTooltip {
    private const val DURATION_MS = 2500L

    // Replace a single view's framework tooltip with the themed popup, using its
    // tooltip/content description as the text. No-op when there's nothing to show.
    fun replace(view: View): Boolean {
        val text = view.contentDescription?.takeIf { it.isNotBlank() } ?: return false
        // Drop the framework tooltip; our long-click handler (returning true) shows ours
        // and also keeps the framework one from firing.
        TooltipCompat.setTooltipText(view, null)
        view.setOnLongClickListener {
            show(view, text)
            true
        }
        return true
    }

    // Re-skin the tooltips of a toolbar's action items + overflow button. Only the
    // direct children of the ActionMenuView are touched — the action buttons and the
    // overflow button — so we never hijack the long-press of an expanded search field's
    // text box (it lives elsewhere in the toolbar) or the navigation icon (its own
    // long-press opens the UI page). Posted so the action views exist; safe to call
    // again whenever the menu is re-prepared.
    fun applyToToolbar(toolbar: Toolbar) {
        toolbar.post {
            val menuView = toolbar.children.firstOrNull { it is ActionMenuView } as? ViewGroup
                ?: return@post
            for (child in menuView.children) {
                replace(child)
            }
        }
    }

    private fun show(anchor: View, text: CharSequence) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val textView = TextView(context).apply {
            this.text = text
            setTextColor(skColor(SkThemeSlot.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = AppCompatResources.getDrawable(context, R.drawable.sk_popup_background)
            setPaddingRelative(dp(12), dp(6), dp(12), dp(6))
            maxLines = 1
        }
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popup = PopupWindow(
            textView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isClippingEnabled = false
            // A pure overlay — never grabs touches, like a real tooltip.
            isTouchable = false
        }
        val xOffset = (anchor.width - textView.measuredWidth) / 2
        try {
            popup.showAsDropDown(anchor, xOffset, dp(4))
        } catch (e: Exception) {
            return
        }
        anchor.postDelayed({ if (popup.isShowing) popup.dismiss() }, DURATION_MS)
    }
}
