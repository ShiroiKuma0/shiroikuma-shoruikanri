/*
 * 白い熊 fork (skui): the "白い熊 書類管理 UI" entry in Settings, opening the
 * SkUiActivity page.
 */

package me.zhanghai.android.files.skui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import me.zhanghai.android.files.util.createIntent

class SkUiPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    override fun onClick() {
        context.startActivity(SkUiActivity::class.createIntent())
    }
}
