/*
 * 白い熊 fork (skui): the internal "Open with" dialog, replacing the system chooser — also on a
 * plain tap, whenever the system would otherwise ask. From top to bottom:
 * - the title, with the file type as a tag line beneath it;
 * - "Always use for this type": switched on before an app is chosen, the app is remembered for
 *   the type and used for every plain open from then on. Left off, the choice opens the file
 *   once and no default — ours or the system's — is touched;
 * - the apps, each an outlined pill with the app's own icon: the remembered default first, thick
 *   and full width, the others in two columns where the screen is wide and in one where not;
 * - "Open as" — pick a different type and choose from the apps that handle that type instead.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.startActivitySafe

class SkOpenWithDialog(
    private val activity: Activity,
    private val path: Path,
    private val mimeType: MimeType
) {
    private val density = activity.resources.displayMetrics.density

    private fun dp(value: Int): Int = Math.round(value * density)

    init {
        val baseIntent = path.fileProviderUri.createViewIntent(mimeType)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { extraPath = path }
        val packageManager = activity.packageManager
        val currentDefault = SkOpenWith.getDefault(mimeType)
        fun component(info: ResolveInfo) =
            ComponentName(info.activityInfo.packageName, info.activityInfo.name)
        // The remembered default first, the others by name.
        val resolveInfos = packageManager.queryIntentActivities(baseIntent, 0)
            .sortedWith(
                compareBy(
                    { component(it) != currentDefault },
                    { it.loadLabel(packageManager).toString().lowercase() }
                )
            )

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        fun fullWidth(top: Int = 3, bottom: Int = 3) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

        // The type, as the tag line of the title.
        holder.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(6), 0, dp(6), dp(8))
                addView(
                    TextView(activity).apply {
                        text = mimeType.value
                        textSize = 13f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        setTextColor(skColor(SkThemeSlot.DIALOG_TEXT))
                    }
                )
                addView(
                    TextView(activity).apply {
                        text = "  ·  " + activity.getString(R.string.sk_open_with_type_qualifier)
                        textSize = 13f
                        setTextColor(skColor(SkThemeSlot.NOTE))
                    }
                )
            }
        )

        // "Always use for this type" — on top, because it is set before an app is tapped.
        val rememberSwitch = SkSwitch(activity).apply {
            minHeight = 0
            minimumHeight = 0
        }
        val switchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(18), dp(2), dp(6), dp(2))
            foreground = SkLeftRule(
                dp(6).toFloat(), dp(2).toFloat(), dp(2).toFloat(), skColor(SkThemeSlot.NOTE)
            )
            setOnClickListener { rememberSwitch.toggle() }
        }
        switchRow.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(activity).apply {
                        text = activity.getString(R.string.sk_open_with_always)
                        textSize = 16f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(skColor(SkThemeSlot.DIALOG_TEXT))
                    }
                )
                addView(
                    TextView(activity).apply {
                        text = activity.getString(R.string.sk_open_with_always_desc)
                        textSize = 13f
                        setTextColor(skColor(SkThemeSlot.NOTE))
                        setPadding(0, dp(1), dp(8), 0)
                    }
                )
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        switchRow.addView(rememberSwitch)
        holder.addView(switchRow)
        holder.addView(divider(), fullWidth(10, 8))

        lateinit var dialog: AlertDialog

        if (resolveInfos.isEmpty()) {
            holder.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.sk_open_with_no_apps)
                    textSize = 15f
                    setTextColor(skColor(SkThemeSlot.NOTE))
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
            )
        }
        fun appPill(info: ResolveInfo): View {
            val component = component(info)
            return pill(
                info.loadIcon(packageManager), null, info.loadLabel(packageManager),
                main = component == currentDefault,
                badge = if (component == currentDefault) {
                    activity.getString(R.string.sk_open_with_default_badge)
                } else {
                    null
                },
                opens = false
            ) {
                if (rememberSwitch.isChecked) {
                    SkOpenWith.setDefault(mimeType, component)
                }
                dialog.dismiss()
                activity.startActivitySafe(Intent(baseIntent).setComponent(component))
            }
        }
        var others = resolveInfos
        if (currentDefault != null && resolveInfos.isNotEmpty() &&
            component(resolveInfos.first()) == currentDefault) {
            holder.addView(appPill(resolveInfos.first()), fullWidth())
            others = resolveInfos.drop(1)
        }
        // Two columns of equal pills where the screen is wide, one column where it is not.
        val columns = if (activity.resources.configuration.screenWidthDp >= 600) 2 else 1
        others.chunked(columns).forEach { chunk ->
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (index in 0 until columns) {
                val cell = chunk.getOrNull(index)?.let { appPill(it) } ?: View(activity)
                line.addView(
                    cell, LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { if (index > 0) marginStart = dp(6) }
                )
            }
            holder.addView(line, fullWidth())
        }

        // Open as — pick another type and choose among its handlers instead.
        holder.addView(divider(), fullWidth(8, 8))
        holder.addView(
            pill(
                AppCompatResources.getDrawable(activity, R.drawable.sk_ic_open_with),
                skColor(SkIconRole.OPEN), activity.getString(R.string.sk_open_with_open_as),
                main = false, badge = null, opens = true
            ) {
                dialog.dismiss()
                showOpenAsTypes()
            },
            fullWidth()
        )

        val builder = SkMaterialAlertDialogBuilder(activity)
            .setCustomTitle(
                skDialogTitle(activity, R.string.sk_open_with_title, R.drawable.sk_ic_open_with)
            )
            .setView(ScrollView(activity).apply { addView(holder) })
            .setNegativeButton(android.R.string.cancel, null)
        if (currentDefault != null) {
            builder.setNeutralButton(R.string.sk_open_with_clear_default) { _, _ ->
                SkOpenWith.clearDefault(mimeType)
            }
        }
        dialog = builder.create()
        dialog.show()
    }

    private fun divider(): View =
        View(activity).apply {
            setBackgroundColor(skColor(SkThemeSlot.MENU_DIVIDER))
            minimumHeight = maxOf(1, dp(1))
        }

    // A full-width outlined pill: an icon (an app's own, or a glyph tinted by its role), the
    // label, and at the right a dim-gold badge or the › of a row that opens something.
    private fun pill(
        icon: Drawable?,
        iconTint: Int?,
        label: CharSequence,
        main: Boolean,
        badge: String?,
        opens: Boolean,
        onClick: () -> Unit
    ): View {
        val ink = skColor(SkThemeSlot.MENU_LABEL)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(14), dp(7))
            background = skPillBackground(
                density, skColor(SkThemeSlot.MENU_ROW_FRAME), skColor(SkThemeSlot.PILL_GROUND), main
            )
            setOnClickListener { onClick() }
        }
        row.addView(
            ImageView(activity).apply {
                setImageDrawable(icon)
                if (iconTint != null) imageTintList = ColorStateList.valueOf(iconTint)
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) }
        )
        row.addView(
            TextView(activity).apply {
                text = label
                textSize = 16f
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(ink)
                if (main) setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (badge != null) {
            row.addView(
                TextView(activity).apply {
                    text = badge
                    textSize = 13f
                    setTextColor(skColor(SkThemeSlot.NOTE))
                    setPaddingRelative(dp(8), 0, 0, 0)
                }
            )
        }
        if (opens) {
            row.addView(
                ImageView(activity).apply {
                    setImageDrawable(
                        AppCompatResources.getDrawable(activity, R.drawable.sk_ic_chevron)
                    )
                    imageTintList = ColorStateList.valueOf(ink)
                }
            )
        }
        return row
    }

    private fun showOpenAsTypes() {
        SkMaterialAlertDialogBuilder(activity)
            .setTitle(R.string.file_open_as_title_format.let {
                activity.getString(it, path.fileName?.toString() ?: "")
            })
            .setNegativeButton(android.R.string.cancel, null)
            .showActions(
                OPEN_AS_TYPES.map { (labelRes, openAsType) ->
                    SkAction(
                        activity.getString(labelRes), R.drawable.sk_ic_open_with,
                        SkIconRole.OPEN, opens = true
                    ) { SkOpenWithDialog(activity, path, openAsType) }
                }
            )
    }

    companion object {
        private val OPEN_AS_TYPES = listOf(
            R.string.file_open_as_type_text to "text/plain",
            R.string.file_open_as_type_image to "image/*",
            R.string.file_open_as_type_audio to "audio/*",
            R.string.file_open_as_type_video to "video/*",
            R.string.file_open_as_type_directory to MimeType.DIRECTORY.value,
            R.string.file_open_as_type_any to "*/*"
        ).map { it.first to it.second.asMimeType() }

        /**
         * Whether a plain view intent would land in the system's chooser: several apps can take
         * it and the system has no preferred one. The fork's own dialog is shown instead, so a
         * default is asked for — and kept — in one place.
         */
        fun wouldAskSystem(activity: Activity, intent: Intent): Boolean {
            val packageManager = activity.packageManager
            val candidates = packageManager.queryIntentActivities(
                intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            if (candidates.size < 2) {
                return false
            }
            val resolved = packageManager.resolveActivity(
                intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo ?: return false
            return candidates.none {
                it.activityInfo.packageName == resolved.packageName &&
                    it.activityInfo.name == resolved.name
            }
        }
    }
}
