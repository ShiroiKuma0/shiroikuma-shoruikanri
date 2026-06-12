/*
 * 白い熊 fork (skui): the internal "Open with" dialog, replacing the system
 * chooser. Lists every app that can handle the file, with:
 * - a "set as default" checkbox — the chosen app is remembered for the file
 *   type and used for every plain open from then on;
 * - an "Open as…" entry — pick a different type and choose from the apps that
 *   handle that type instead.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private fun dp(value: Int): Int = (value * density).toInt()

    init {
        val baseIntent = path.fileProviderUri.createViewIntent(mimeType)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { extraPath = path }
        val packageManager = activity.packageManager
        val resolveInfos = packageManager.queryIntentActivities(baseIntent, 0)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val currentDefault = SkOpenWith.getDefault(mimeType)

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        val rememberBox = CheckBox(activity).apply {
            text = activity.getString(R.string.sk_open_with_set_default, mimeType.value)
            textSize = 14f
            setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
            setPadding(dp(8), dp(4), dp(16), dp(8))
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        if (resolveInfos.isEmpty()) {
            holder.addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.sk_open_with_no_apps)
                    textSize = 15f
                    setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
                    setPadding(dp(24), dp(12), dp(24), dp(12))
                }
            )
        }
        resolveInfos.forEach { resolveInfo ->
            val component = ComponentName(
                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name
            )
            val isDefault = component == currentDefault
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(10), dp(24), dp(10))
                setBackgroundResource(android.R.drawable.list_selector_background)
            }
            row.addView(
                ImageView(activity).apply {
                    setImageDrawable(resolveInfo.loadIcon(packageManager))
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                }
            )
            row.addView(
                TextView(activity).apply {
                    text = buildString {
                        append(resolveInfo.loadLabel(packageManager))
                        if (isDefault) {
                            append(activity.getString(R.string.sk_open_with_default_suffix))
                        }
                    }
                    textSize = 16f
                    setTextColor(
                        skColor(if (isDefault) SkThemeSlot.ACCENT else SkThemeSlot.TEXT)
                    )
                    setPaddingRelative(dp(16), 0, 0, 0)
                }
            )
            row.setOnClickListener {
                if (rememberBox.isChecked) {
                    SkOpenWith.setDefault(mimeType, component)
                }
                dialog?.dismiss()
                activity.startActivitySafe(Intent(baseIntent).setComponent(component))
            }
            holder.addView(row)
        }

        // Open as… — pick another type and choose among its handlers instead.
        holder.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.sk_open_with_open_as)
                textSize = 16f
                setTextColor(skColor(SkThemeSlot.ACCENT))
                setPadding(dp(24), dp(12), dp(24), dp(12))
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    dialog?.dismiss()
                    showOpenAsTypes()
                }
            }
        )
        holder.addView(rememberBox.apply { setPadding(dp(24), dp(8), dp(24), dp(4)) })

        val builder = SkMaterialAlertDialogBuilder(activity)
            .setTitle(
                activity.getString(R.string.sk_open_with_title_format, mimeType.value)
            )
            .setView(ScrollView(activity).apply { addView(holder) })
            .setNegativeButton(android.R.string.cancel, null)
        if (currentDefault != null) {
            builder.setNeutralButton(R.string.sk_open_with_clear_default) { _, _ ->
                SkOpenWith.clearDefault(mimeType)
            }
        }
        dialog = builder.show()
    }

    private fun showOpenAsTypes() {
        val labels = OPEN_AS_TYPES.map { activity.getString(it.first) }.toTypedArray<CharSequence>()
        SkMaterialAlertDialogBuilder(activity)
            .setTitle(R.string.file_open_as_title_format.let {
                activity.getString(it, path.fileName?.toString() ?: "")
            })
            .setItems(labels) { _, which ->
                SkOpenWithDialog(activity, path, OPEN_AS_TYPES[which].second)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
    }
}
