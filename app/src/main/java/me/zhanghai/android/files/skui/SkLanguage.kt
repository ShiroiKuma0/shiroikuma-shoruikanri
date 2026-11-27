/*
 * 白い熊 fork (skui): the app's language — the first section of the UI page (black-yellow theme,
 * "App language").
 *
 * - The languages offered are the short list of res/xml/sk_locales_config.xml: Japanese and
 *   English, then the languages upstream translates to four fifths or more, one row per
 *   language. The manifest declares that file, so upstream's own picker in Settings and the
 *   system's per-app language screen offer the same list.
 * - The choice is applied through AppCompat's per-app locales, like upstream's picker. The tag
 *   is kept in the UI preferences as well and asserted again at every start: EMUI does not keep
 *   the system's per-app locale across a sideload update. It travels in the export.
 * - Below Android 13 AppCompat wraps activities only, so the application's own resources —
 *   read by notifications, services and toasts — are given the language here.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

object SkLanguage {
    // Always on top of the picker, outside the box that scrolls, in this order.
    private const val PINNED_JAPANESE = "ja"
    private const val PINNED_ENGLISH = "en-US"

    private class Entry(val tag: String) {
        val locale: Locale = Locale.forLanguageTag(tag)

        // A language declared without a country shows the flag, the country and the tag of its
        // likely country; what is stored and applied stays the declared tag.
        val country: String = locale.country.ifEmpty {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ULocale.addLikelySubtags(ULocale.forLocale(locale)).country
            } else {
                ""
            }
        }

        val name: String
            get() {
                val language = locale.getDisplayLanguage(locale)
                    .replaceFirstChar { it.titlecase(locale) }
                // The two Chinese rows differ by script.
                return when (tag) {
                    "zh-CN" -> "$language (简体)"
                    "zh-TW" -> "$language (繁體)"
                    else -> language
                }
            }

        val countryName: String
            get() = if (country.isEmpty()) "" else Locale("", country).getDisplayCountry(locale)

        val shownTag: String
            get() = if (country.isEmpty()) locale.language else "${locale.language}-$country"
    }

    /** The tag in force, empty for the system's language. */
    val tag: String
        get() = SkUi.appLanguage

    /** The declared languages, in the order of the file. */
    private fun listedTags(context: Context): List<String> {
        val tags = mutableListOf<String>()
        context.resources.getXml(R.xml.sk_locales_config).use { parser ->
            while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.START_TAG -> if (parser.name == "locale") {
                        parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name"
                        )?.let { tags += it }
                    }
                }
            }
        }
        return tags
    }

    /** The name of the language in force, for the row on the UI page. */
    fun currentName(context: Context): String =
        if (tag.isEmpty()) context.getString(R.string.sk_ui_language_system) else Entry(tag).name

    /** Called by every picker that sets the language, upstream's included. */
    fun store(tag: String) {
        SkUi.appLanguage = tag
        applyToApplication()
    }

    fun set(tag: String) {
        store(tag)
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /** At application start: put the stored language back in force where the system lost it. */
    fun reassert() {
        val stored = tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val system = LocaleManagerCompat.getApplicationLocales(application).toLanguageTags()
            if (system.isNotEmpty()) {
                // Set since through the system's own screen: that is the choice now.
                if (system.substringBefore(',') != stored) {
                    SkUi.appLanguage = system.substringBefore(',')
                }
            } else if (stored.isNotEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(stored))
            }
            return
        }
        if (stored.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(stored))
        }
        applyToApplication()
        application.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applyToApplication()
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
        })
    }

    // Below Android 13 only: from 13 on the system gives the application its language.
    @Suppress("DEPRECATION")
    private fun applyToApplication() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return
        }
        val resources = application.resources
        val wanted = if (tag.isEmpty()) {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList(Locale.forLanguageTag(tag))
        }
        if (resources.configuration.locales == wanted) {
            return
        }
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(wanted)
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    /**
     * The picker: 日本語 and English as two pills that never scroll, under them one framed box
     * that scrolls by itself — "System default" first, then the other languages by their own
     * name — and a Cancel pill below. Choosing applies at once.
     */
    fun showPicker(activity: Activity) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = Math.round(value * density)
        val current = tag
        val listed = listedTags(activity)
        val pinned = listOf(PINNED_JAPANESE, PINNED_ENGLISH).filter { it in listed }
        // A language chosen earlier that is no longer listed stays in force and is shown.
        val others = (listed - pinned.toSet() +
            listOfNotNull(current.takeIf { it.isNotEmpty() && it !in listed }))
            .map { Entry(it) }.sortedWith(compareBy({ it.name.lowercase(it.locale) }, { it.tag }))

        lateinit var dialog: AlertDialog
        fun choose(tag: String) {
            dialog.dismiss()
            if (tag != current) {
                set(tag)
            }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), 0)
        }
        for (pinnedTag in pinned) {
            val entry = Entry(pinnedTag)
            content.addView(
                row(activity, entry, pinnedTag == current, pill = true) { choose(pinnedTag) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6f) }
            )
        }

        val rows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }
        var currentRow: View? = null
        // "System default" has no flag, no country and no tag: the app follows the phone.
        val entries: List<Entry?> = listOf<Entry?>(null) + others
        entries.forEachIndexed { index, entry ->
            val rowTag = entry?.tag ?: ""
            val isCurrent = rowTag == current
            val previousIsCurrent = index > 0 && (entries[index - 1]?.tag ?: "") == current
            // A hairline between two rows, but none touching the current one's pill.
            if (index > 0 && !isCurrent && !previousIsCurrent) {
                rows.addView(
                    View(activity).apply { setBackgroundColor(skColor(SkThemeSlot.MENU_DIVIDER)) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = dp(10f)
                        marginEnd = dp(10f)
                    }
                )
            }
            val view = row(activity, entry, isCurrent, pill = isCurrent) { choose(rowTag) }
            if (isCurrent) {
                currentRow = view
            }
            rows.addView(view)
        }
        val scroll = ScrollView(activity).apply {
            addView(rows)
            isVerticalScrollBarEnabled = false
        }
        content.addView(
            LinearLayout(activity).apply {
                background = skPanel(density, frame = skColor(SkThemeSlot.DIALOG_FRAME))
                addView(
                    scroll, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(4f)
            }
        )

        dialog = SkMaterialAlertDialogBuilder(activity)
            .setCustomTitle(skDialogTitle(activity, R.string.sk_ui_group_language, R.drawable.sk_ic_globe))
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        // Open with the current row in view.
        currentRow?.let { view -> scroll.post { scroll.scrollTo(0, maxOf(0, view.top - dp(48f))) } }
    }

    // One language: selection mark, flag, its name in its own language over its country in
    // dim gold, and its tag at the right. A null entry is "System default", with the globe.
    private fun row(
        activity: Activity,
        entry: Entry?,
        isCurrent: Boolean,
        pill: Boolean,
        onClick: () -> Unit
    ): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = Math.round(value * density)
        val text = skColor(SkThemeSlot.DIALOG_TEXT)
        val note = skColor(SkThemeSlot.NOTE)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(7f), dp(16f), dp(7f))
            background = if (pill) {
                skPillBackground(
                    density, skColor(SkThemeSlot.PILL_FRAME), skColor(SkThemeSlot.PILL_GROUND),
                    isCurrent
                )
            } else {
                AppCompatResources.getDrawable(activity, android.R.drawable.list_selector_background)
            }
            setOnClickListener { onClick() }
        }
        row.addView(
            View(activity).apply { background = mark(density, isCurrent) },
            LinearLayout.LayoutParams(dp(18f), dp(18f))
        )
        val flag = entry?.country?.takeIf { it.isNotEmpty() }?.let {
            activity.resources.getIdentifier(
                "sk_flag_" + it.lowercase(Locale.ROOT), "drawable", activity.packageName
            )
        }?.takeIf { it != 0 } ?: R.drawable.sk_flag_globe
        row.addView(
            ImageView(activity).apply { setImageResource(flag) },
            LinearLayout.LayoutParams(dp(27f), dp(25.5f)).apply {
                marginStart = dp(12f)
                marginEnd = dp(12f)
            }
        )
        val names = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        names.addView(
            TextView(activity).apply {
                this.text = entry?.name ?: activity.getString(R.string.sk_ui_language_system)
                textSize = 16f
                setTextColor(text)
                if (isCurrent) setTypeface(typeface, Typeface.BOLD)
            }
        )
        entry?.countryName?.takeIf { it.isNotEmpty() }?.let { country ->
            names.addView(
                TextView(activity).apply {
                    this.text = country
                    textSize = 12f
                    setTextColor(note)
                }
            )
        }
        row.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (entry != null) {
            row.addView(
                TextView(activity).apply {
                    this.text = entry.shownTag
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setTextColor(note)
                    // The tag column is at the right also for a right-to-left language.
                    textDirection = View.TEXT_DIRECTION_LTR
                }
            )
        }
        return row
    }

    // A radio ring: the mark-on colour with a dot on the current row, the dim one elsewhere.
    private fun mark(density: Float, on: Boolean): LayerDrawable {
        val color = skColor(if (on) SkThemeSlot.MARK_ON else SkThemeSlot.MARK_OFF)
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(Math.round(2 * density), color)
        }
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) color else 0)
        }
        return LayerDrawable(arrayOf(ring, dot)).apply {
            val inset = Math.round(5 * density)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }
}

/** The title of a dialog built in code: its icon in the accent, then its words. */
fun skDialogTitle(context: Context, titleRes: Int, iconRes: Int): TextView =
    TextView(context).apply {
        val density = resources.displayMetrics.density
        text = context.getString(titleRes)
        textSize = 20f
        setTextColor(skColor(SkThemeSlot.DIALOG_TITLE))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            Math.round(22 * density), Math.round(20 * density), Math.round(22 * density),
            Math.round(6 * density)
        )
        AppCompatResources.getDrawable(context, iconRes)?.mutate()?.let { icon ->
            val size = Math.round(textSize * 1.25f)
            icon.setTint(skColor(SkThemeSlot.ICON))
            icon.setBounds(0, 0, size, size)
            compoundDrawablePadding = Math.round(10 * density)
            setCompoundDrawablesRelative(icon, null, null, null)
        }
    }
