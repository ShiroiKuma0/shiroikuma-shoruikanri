/*
 * 白い熊 fork (skui): drop-in replacement for MaterialAlertDialogBuilder that
 * gives every dialog the 白い熊 look — solid black background with a yellow
 * border (the text colors come from the theme overlay).
 */

package me.zhanghai.android.files.skui

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

class SkMaterialAlertDialogBuilder @JvmOverloads constructor(
    context: Context,
    overrideThemeResId: Int = 0
) : MaterialAlertDialogBuilder(context, overrideThemeResId) {
    init {
        // Material insets every dialog 80 dp from the top and the bottom of its window. With the
        // keyboard up on a short screen that leaves less height than title + text field + buttons
        // need, so the field's scroll view shrinks and cuts the bottom of the field off. A small
        // inset gives the room back to the content.
        val verticalInset = (VERTICAL_INSET_DP * context.resources.displayMetrics.density).toInt()
        setBackgroundInsetTop(verticalInset)
        setBackgroundInsetBottom(verticalInset)
        if (SkUi.isSkThemeEnabled) {
            setBackground(AppCompatResources.getDrawable(context, R.drawable.sk_dialog_background))
        }
    }

    companion object {
        private const val VERTICAL_INSET_DP = 16
    }
}
