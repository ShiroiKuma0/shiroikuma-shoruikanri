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
        if (SkUi.isSkThemeEnabled) {
            setBackground(AppCompatResources.getDrawable(context, R.drawable.sk_dialog_background))
        }
    }
}
