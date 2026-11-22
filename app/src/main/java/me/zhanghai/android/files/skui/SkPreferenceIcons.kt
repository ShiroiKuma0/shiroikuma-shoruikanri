/*
 * 白い熊 fork (skui): an icon for every row of the app's preference screens (Settings, FTP
 * server), coloured by what the row does. Upstream's rows carry none; the table is keyed by the
 * preference class and, failing that, by the kind of preference — so a row upstream adds later
 * still gets an icon.
 */

package me.zhanghai.android.files.skui

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R

object SkPreferenceIcons {
    private class Icon(val res: Int, val role: SkIconRole)

    private val BY_CLASS: Map<String, Icon> = mapOf(
        "SkUiPreference" to Icon(R.drawable.sk_ic_palette, SkIconRole.VIEW),
        "LocalePreference" to Icon(R.drawable.sk_ic_encoding, SkIconRole.VIEW),
        "ThemeColorPreference" to Icon(R.drawable.sk_ic_palette, SkIconRole.VIEW),
        "DefaultDirectoryPreference" to
            Icon(R.drawable.directory_icon_white_24dp, SkIconRole.OPEN),
        "StoragesPreference" to Icon(R.drawable.sd_card_icon_white_24dp, SkIconRole.OPEN),
        "StandardDirectoriesPreference" to
            Icon(R.drawable.directory_icon_white_24dp, SkIconRole.OPEN),
        "BookmarkDirectoriesPreference" to Icon(R.drawable.sk_ic_bookmark, SkIconRole.WRITE),
        "RootStrategyPreference" to Icon(R.drawable.lock_icon_white_24dp, SkIconRole.BLOCK),
        "CharsetPreference" to Icon(R.drawable.sk_ic_encoding, SkIconRole.VIEW),
        "FtpServerStatePreference" to Icon(R.drawable.sk_ic_play, SkIconRole.OPEN),
        "FtpServerUrlPreference" to
            Icon(R.drawable.copy_icon_control_normal_24dp, SkIconRole.SHARE),
        "FtpServerAddTilePreference" to Icon(R.drawable.add_icon_white_24dp, SkIconRole.WRITE),
        "DefaultIfEmptyEditTextPreference" to
            Icon(R.drawable.person_icon_white_24dp, SkIconRole.PERSON),
        "PasswordPreference" to Icon(R.drawable.sk_ic_key, SkIconRole.BLOCK),
        "NonNegativeIntegerPreference" to Icon(R.drawable.sk_ic_tune, SkIconRole.VIEW),
        "FtpServerHomeDirectoryPreference" to
            Icon(R.drawable.directory_icon_white_24dp, SkIconRole.OPEN)
    )

    /**
     * Icons on every row of the fragment's screen, and — once its list exists — every row that
     * opens something drawn as a pill that hugs its title, with a › at its end and its summary
     * beneath in the explanation colour. Rows with a switch stay plain.
     */
    fun apply(fragment: PreferenceFragmentCompat) {
        if (!SkUi.isSkThemeEnabled) {
            return
        }
        apply(fragment.preferenceScreen)
        fragment.viewLifecycleOwnerLiveData.observe(fragment) { owner ->
            if (owner != null) {
                fragment.listView?.let { stylePills(it) }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun stylePills(list: RecyclerView) {
        list.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val position = list.getChildAdapterPosition(view)
                    val preference = (list.adapter as? PreferenceGroupAdapter)
                        ?.takeIf { position != RecyclerView.NO_POSITION }?.getItem(position)
                        ?: return
                    styleRow(view, preference)
                }

                override fun onChildViewDetachedFromWindow(view: View) {}
            }
        )
    }

    private fun styleRow(row: View, preference: Preference) {
        if (preference is PreferenceCategory) {
            return
        }
        row.findViewById<TextView>(android.R.id.summary)
            ?.setTextColor(skColor(SkThemeSlot.NOTE))
        val title = row.findViewById<TextView>(android.R.id.title) ?: return
        val density = row.resources.displayMetrics.density
        fun dp(value: Int) = Math.round(value * density)
        val isPill = preference !is TwoStatePreference && preference.isSelectable
        if (!isPill) {
            // A row is recycled: take back what an earlier binding may have put on it.
            title.background = null
            title.setPadding(0, 0, 0, 0)
            title.setCompoundDrawablesRelative(null, null, null, null)
            title.setTextColor(skColor(SkThemeSlot.TEXT))
            return
        }
        val ink = skColor(SkThemeSlot.PILL_TEXT)
        val enabledInk = if (preference.isEnabled) ink else skDisabled(ink)
        val frame = skColor(SkThemeSlot.PILL_FRAME)
        title.setTextColor(enabledInk)
        title.background = skPillFrame(
            density, if (preference.isEnabled) frame else skDisabled(frame),
            skColor(SkThemeSlot.PILL_GROUND), false
        )
        title.setPaddingRelative(dp(14), dp(6), dp(10), dp(6))
        val chevron = AppCompatResources.getDrawable(row.context, R.drawable.sk_ic_chevron)
            ?.mutate()
        chevron?.setTint(enabledInk)
        chevron?.setBounds(0, 0, dp(18), dp(18))
        title.compoundDrawablePadding = dp(6)
        title.setCompoundDrawablesRelative(null, null, chevron, null)
    }

    fun apply(group: PreferenceGroup) {
        if (!SkUi.isSkThemeEnabled) {
            return
        }
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            if (preference is PreferenceGroup) {
                apply(preference)
            } else {
                setIcon(preference)
            }
        }
    }

    private fun setIcon(preference: Preference) {
        val icon = BY_CLASS[preference.javaClass.simpleName] ?: when (preference) {
            is TwoStatePreference -> Icon(R.drawable.sk_ic_toggle, SkIconRole.VIEW)
            else -> Icon(R.drawable.sk_ic_list, SkIconRole.VIEW)
        }
        val drawable = AppCompatResources.getDrawable(preference.context, icon.res)?.mutate()
            ?: return
        drawable.setTint(skColor(icon.role))
        preference.icon = drawable
    }
}
