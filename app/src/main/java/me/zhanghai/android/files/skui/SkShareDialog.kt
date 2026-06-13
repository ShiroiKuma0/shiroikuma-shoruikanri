/*
 * 白い熊 fork (skui): the custom share dialog, replacing the system share
 * sheet. Same format as the open-with dialog: black, yellow-bordered, all
 * share handlers with icons. Long-press an app to pin/unpin it (pinned apps
 * sort to the top).
 *
 * Two integrations on top of the normal share targets:
 * - AutoShare: an "AutoShare command…" row opens AutoShare's own command
 *   chooser (AutoShare matches commands by an opaque internal id it doesn't
 *   expose, so a true one-tap to a specific command isn't possible).
 * - Termux: user-defined one-click script targets — each runs a chosen Termux
 *   script via RunCommandService with the selected file's real path(s) as
 *   command-line arguments (fully reliable: explicit path + arguments).
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe

class SkShareDialog(
    private val activity: Activity,
    private val baseIntent: Intent,
    // Real filesystem paths of the shared files (local files only), for Termux args.
    private val filePaths: List<String> = emptyList()
) {
    private val density = activity.resources.displayMetrics.density
    private var dialog: AlertDialog? = null

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun isInstalled(packageName: String): Boolean =
        try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }

    init {
        val packageManager = activity.packageManager
        val resolveInfos = packageManager.queryIntentActivities(baseIntent, 0)
            .filter { it.activityInfo.packageName != activity.packageName }
        val pinned = SkShare.pinnedComponents
        val (pinnedInfos, otherInfos) = resolveInfos
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .partition {
                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    .flattenToString() in pinned
            }

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        // Termux one-click script targets (only meaningful with real file paths).
        val termuxIcon = appIconOrNull(TERMUX_PACKAGE)
        if (termuxIcon != null && filePaths.isNotEmpty()) {
            SkTermux.scripts.forEachIndexed { index, script ->
                addRow(
                    holder, script.label, termuxIcon, skColor(SkThemeSlot.ACCENT),
                    onClick = { runTermuxScript(script) },
                    onLongClick = { showTermuxScriptOptions(index, script) }
                )
            }
            addRow(
                holder, activity.getString(R.string.sk_termux_add), null,
                skColor(SkThemeSlot.ACCENT),
                onClick = { showTermuxScriptEditor(null, null) },
                onLongClick = {}
            )
        }

        // AutoShare commands: one tap into AutoShare's command chooser, then pick.
        appIconOrNull(SkShare.AUTOSHARE_PACKAGE)?.let { autoShareIcon ->
            addRow(
                holder, activity.getString(R.string.sk_share_autoshare_command), autoShareIcon,
                skColor(SkThemeSlot.ACCENT),
                onClick = { launchAutoShareCommandChooser() },
                onLongClick = {}
            )
        }

        // Then the pinned apps, then everything else.
        (pinnedInfos + otherInfos).forEach { resolveInfo ->
            val component = ComponentName(
                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name
            )
            val isPinned = component.flattenToString() in pinned
            addRow(
                holder,
                resolveInfo.loadLabel(packageManager).toString(),
                resolveInfo.loadIcon(packageManager),
                skColor(if (isPinned) SkThemeSlot.ACCENT else SkThemeSlot.TEXT),
                onClick = {
                    grantSharedUrisTo(component.packageName)
                    dialog?.dismiss()
                    activity.startActivitySafe(Intent(baseIntent).setComponent(component))
                },
                onLongClick = {
                    SkShare.togglePinned(component.flattenToString())
                    reopen()
                }
            )
        }
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

        dialog = SkMaterialAlertDialogBuilder(activity)
            .setTitle(R.string.share)
            .setView(ScrollView(activity).apply { addView(holder) })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun appIconOrNull(packageName: String) =
        try {
            activity.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

    private fun reopen() {
        dialog?.dismiss()
        SkShareDialog(activity, baseIntent, filePaths)
    }

    // --- Termux ---

    private fun runTermuxScript(script: SkTermuxScript) {
        if (!isInstalled(TERMUX_PACKAGE)) {
            activity.showToast(R.string.sk_termux_missing)
            return
        }
        dialog?.dismiss()
        if (!SkTermux.run(activity, script, filePaths)) {
            // Most commonly: allow-external-apps not enabled, or permission denied.
            activity.showToast(R.string.sk_termux_failed)
        }
    }

    private fun showTermuxScriptOptions(index: Int, script: SkTermuxScript) {
        val count = SkTermux.scripts.size
        val labels = mutableListOf<CharSequence>()
        val actions = mutableListOf<() -> Unit>()
        if (index > 0) {
            labels += activity.getString(R.string.sk_share_move_up)
            actions += { SkTermux.move(index, index - 1); reopen() }
        }
        if (index < count - 1) {
            labels += activity.getString(R.string.sk_share_move_down)
            actions += { SkTermux.move(index, index + 1); reopen() }
        }
        labels += activity.getString(R.string.sk_share_edit)
        actions += { showTermuxScriptEditor(index, script) }
        labels += activity.getString(R.string.delete)
        actions += { SkTermux.remove(index); reopen() }
        SkMaterialAlertDialogBuilder(activity)
            .setTitle(script.label)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTermuxScriptEditor(index: Int?, script: SkTermuxScript?) {
        val labelEdit = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = activity.getString(R.string.sk_termux_label_hint)
            setText(script?.label.orEmpty())
        }
        val pathEdit = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = activity.getString(R.string.sk_termux_path_hint)
            setText(script?.scriptPath.orEmpty())
        }
        val terminalBox = CheckBox(activity).apply {
            text = activity.getString(R.string.sk_termux_run_in_terminal)
            isChecked = !(script?.background ?: false)
        }
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.sk_termux_help)
                    textSize = 13f
                    setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
                    setPadding(0, 0, 0, dp(8))
                }
            )
            addView(labelEdit)
            addView(pathEdit)
            addView(terminalBox)
        }
        SkMaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sk_termux_title)
            .setView(form)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val path = pathEdit.text.toString().trim()
                val label = labelEdit.text.toString().trim().ifEmpty {
                    path.substringAfterLast('/').ifEmpty { path }
                }
                if (path.isNotEmpty()) {
                    val newScript = SkTermuxScript(label, path, !terminalBox.isChecked)
                    if (index == null) SkTermux.add(newScript) else SkTermux.update(index, newScript)
                }
                reopen()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> reopen() }
            .show()
        dialog?.dismiss()
    }

    // --- AutoShare ---

    private fun launchAutoShareCommandChooser() {
        grantSharedUrisTo(SkShare.AUTOSHARE_PACKAGE)
        dialog?.dismiss()
        activity.startActivitySafe(
            Intent(baseIntent)
                .setClassName(SkShare.AUTOSHARE_PACKAGE, SkShare.AUTOSHARE_COMMAND_ACTIVITY)
        )
    }

    // --- URI grants ---

    private fun sharedUris(): List<Uri> {
        val uris = mutableListOf<Uri>()
        baseIntent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris += it }
            }
        }
        if (uris.isEmpty()) {
            @Suppress("DEPRECATION")
            baseIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
            @Suppress("DEPRECATION")
            baseIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        }
        return uris
    }

    private fun grantSharedUrisTo(packageName: String) {
        sharedUris().forEach {
            try {
                activity.grantUriPermission(
                    packageName, it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Best effort; the intent's own grant still applies for synchronous reads.
            }
        }
    }

    private fun addRow(
        holder: LinearLayout,
        label: String,
        icon: android.graphics.drawable.Drawable?,
        textColor: Int,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        row.addView(
            ImageView(activity).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            }
        )
        row.addView(
            TextView(activity).apply {
                text = label
                textSize = 16f
                setTextColor(textColor)
                setPaddingRelative(dp(16), 0, 0, 0)
            }
        )
        row.setOnClickListener { onClick() }
        row.setOnLongClickListener {
            onLongClick()
            true
        }
        holder.addView(row)
    }
}
