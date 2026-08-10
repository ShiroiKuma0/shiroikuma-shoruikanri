/*
 * 白い熊 fork (skui): the custom share dialog, replacing the system share
 * sheet. Same format as the open-with dialog: black, yellow-bordered, all
 * share handlers with icons.
 *
 * The rows are manually sortable: long-press one and drag it where it belongs —
 * the arrangement is remembered (SkShare.order) and used for every later share.
 * A long-press released *without* moving opens the row menu instead (move to
 * top/bottom, reset the order, plus edit/delete on a Termux target) — the same
 * idiom as the drawer's favorites.
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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    // The rows, in the order they are shown — dragging rewrites this list.
    private val entries = mutableListOf<Entry>()
    private val adapter = EntryAdapter()
    private var recyclerView: RecyclerView? = null

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun isInstalled(packageName: String): Boolean =
        try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }

    init {
        entries += SkShare.sortByOrder(buildEntries()) { it.key }

        val content: View =
            if (entries.isEmpty()) {
                TextView(activity).apply {
                    text = activity.getString(R.string.sk_open_with_no_apps)
                    textSize = 15f
                    setTextColor(skColor(SkThemeSlot.TEXT_SECONDARY))
                    setPadding(dp(24), dp(12), dp(24), dp(12))
                }
            } else {
                val list = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                    adapter = this@SkShareDialog.adapter
                    setPadding(0, dp(8), 0, dp(8))
                    clipToPadding = false
                }
                ItemTouchHelper(DragCallback()).attachToRecyclerView(list)
                recyclerView = list
                list
            }

        dialog = SkMaterialAlertDialogBuilder(activity)
            .setTitle(R.string.share)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Rows ---

    // One row of the dialog. [key] identifies it across dialogs and app starts,
    // so the manual order can be stored and re-applied.
    private class Entry(
        val key: String,
        val label: String,
        val icon: Drawable?,
        val textColor: Int,
        val onClick: () -> Unit,
        // Row-specific entries of the long-press menu, after the move actions.
        val extraActions: List<Pair<String, () -> Unit>> = emptyList()
    )

    private fun buildEntries(): List<Entry> {
        val packageManager = activity.packageManager
        val built = mutableListOf<Entry>()

        // Termux one-click script targets (only meaningful with real file paths).
        val termuxIcon = appIconOrNull(TERMUX_PACKAGE)
        if (termuxIcon != null && filePaths.isNotEmpty()) {
            SkTermux.scripts.forEach { script ->
                built += Entry(
                    key = termuxKey(script),
                    label = script.label,
                    icon = termuxIcon,
                    textColor = skColor(SkThemeSlot.ACCENT),
                    onClick = { runTermuxScript(script) },
                    extraActions = listOf(
                        activity.getString(R.string.sk_share_edit) to
                            { showTermuxScriptEditor(script) },
                        activity.getString(R.string.delete) to
                            { removeTermuxScript(script) }
                    )
                )
            }
            built += Entry(
                key = KEY_TERMUX_ADD,
                label = activity.getString(R.string.sk_termux_add),
                icon = null,
                textColor = skColor(SkThemeSlot.ACCENT),
                onClick = { showTermuxScriptEditor(null) }
            )
        }

        // AutoShare commands: one tap into AutoShare's command chooser, then pick.
        appIconOrNull(SkShare.AUTOSHARE_PACKAGE)?.let { autoShareIcon ->
            built += Entry(
                key = KEY_AUTOSHARE,
                label = activity.getString(R.string.sk_share_autoshare_command),
                icon = autoShareIcon,
                textColor = skColor(SkThemeSlot.ACCENT),
                onClick = { launchAutoShareCommandChooser() }
            )
        }

        // Then the apps, in their default arrangement: the legacy pinned ones
        // first, everything else alphabetically. A stored order overrides this.
        val pinned = SkShare.pinnedComponents
        val (pinnedInfos, otherInfos) = packageManager.queryIntentActivities(baseIntent, 0)
            .filter { it.activityInfo.packageName != activity.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .partition {
                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    .flattenToString() in pinned
            }
        (pinnedInfos + otherInfos).forEach { resolveInfo ->
            val component = ComponentName(
                resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name
            )
            built += Entry(
                key = component.flattenToString(),
                label = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager),
                textColor = skColor(SkThemeSlot.TEXT),
                onClick = {
                    grantSharedUrisTo(component.packageName)
                    dialog?.dismiss()
                    activity.startActivitySafe(Intent(baseIntent).setComponent(component))
                }
            )
        }
        return built
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

    // --- Manual order ---

    private fun moveEntry(from: Int, to: Int) {
        if (from !in entries.indices || to !in entries.indices || from == to) {
            return
        }
        entries.add(to, entries.removeAt(from))
        adapter.notifyItemMoved(from, to)
        recyclerView?.scrollToPosition(to)
        persistOrder()
    }

    private fun persistOrder() {
        SkShare.saveOrder(entries.map { it.key })
        // The Termux script list also drives the Direct-Share tiles and the
        // one-tap target, so keep it in the order the rows now show.
        val scripts = SkTermux.scripts
        val scriptsByKey = scripts.associateBy { termuxKey(it) }
        val reordered = entries.mapNotNull { scriptsByKey[it.key] }
        if (scriptsByKey.size == scripts.size && reordered.size == scripts.size &&
            reordered != scripts) {
            SkTermux.reorder(reordered)
        }
    }

    // A long-press released without dragging: the row's own menu.
    private fun showEntryMenu(entry: Entry) {
        val index = entries.indexOf(entry)
        if (index < 0) {
            return
        }
        val labels = mutableListOf<CharSequence>()
        val actions = mutableListOf<() -> Unit>()
        if (index > 0) {
            labels += activity.getString(R.string.sk_share_move_to_top)
            actions += { moveEntry(index, 0) }
        }
        if (index < entries.size - 1) {
            labels += activity.getString(R.string.sk_share_move_to_bottom)
            actions += { moveEntry(index, entries.size - 1) }
        }
        entry.extraActions.forEach { (label, action) ->
            labels += label
            actions += action
        }
        if (SkShare.order.isNotEmpty()) {
            labels += activity.getString(R.string.sk_share_reset_order)
            actions += {
                SkShare.clearOrder()
                reopen()
            }
        }
        if (labels.isEmpty()) {
            return
        }
        SkMaterialAlertDialogBuilder(activity)
            .setTitle(entry.label)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Termux ---

    private fun termuxKey(script: SkTermuxScript): String = "termux:${script.scriptPath}"

    // The stored scripts are re-parsed on every read, so a row's captured script
    // has to be found again by value rather than by identity or by position.
    private fun termuxIndexOf(script: SkTermuxScript): Int =
        SkTermux.scripts.indexOfFirst {
            it.scriptPath == script.scriptPath && it.label == script.label
        }

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

    private fun removeTermuxScript(script: SkTermuxScript) {
        val index = termuxIndexOf(script)
        if (index >= 0) {
            SkTermux.remove(index)
        }
        reopen()
    }

    private fun showTermuxScriptEditor(script: SkTermuxScript?) {
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
                    val index = script?.let { termuxIndexOf(it) } ?: -1
                    if (index >= 0) SkTermux.update(index, newScript) else SkTermux.add(newScript)
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

    // --- List ---

    private class EntryHolder(
        itemView: View,
        val iconView: ImageView,
        val labelView: TextView
    ) : RecyclerView.ViewHolder(itemView)

    private inner class EntryAdapter : RecyclerView.Adapter<EntryHolder>() {
        override fun getItemCount(): Int = entries.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryHolder {
            val iconView = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            }
            val labelView = TextView(activity).apply {
                textSize = 16f
                setPaddingRelative(dp(16), 0, 0, 0)
            }
            val row = LinearLayout(activity).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(10), dp(24), dp(10))
                setBackgroundResource(android.R.drawable.list_selector_background)
                addView(iconView)
                addView(labelView)
            }
            return EntryHolder(row, iconView, labelView)
        }

        override fun onBindViewHolder(holder: EntryHolder, position: Int) {
            val entry = entries[position]
            holder.iconView.setImageDrawable(entry.icon)
            holder.labelView.text = entry.label
            holder.labelView.setTextColor(entry.textColor)
            holder.itemView.setOnClickListener { entry.onClick() }
        }
    }

    // Long-press to drag a row into place; a long-press released without moving
    // opens the row menu instead (same as the drawer's favorites).
    private inner class DragCallback : ItemTouchHelper.Callback() {
        private var hasMoved = false
        private var draggedEntry: Entry? = null

        override fun isLongPressDragEnabled(): Boolean = true

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            if (fromPosition !in entries.indices || toPosition !in entries.indices) {
                return false
            }
            entries.add(toPosition, entries.removeAt(fromPosition))
            adapter.notifyItemMoved(fromPosition, toPosition)
            hasMoved = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)

            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                hasMoved = false
                draggedEntry = entries.getOrNull(viewHolder.bindingAdapterPosition)
                viewHolder.itemView.setBackgroundColor(
                    ColorUtils.setAlphaComponent(skColor(SkThemeSlot.ACCENT), 0x40)
                )
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            viewHolder.itemView.setBackgroundResource(android.R.drawable.list_selector_background)
            val entry = draggedEntry ?: return
            draggedEntry = null
            if (hasMoved) {
                hasMoved = false
                persistOrder()
            } else {
                showEntryMenu(entry)
            }
        }
    }

    companion object {
        private const val KEY_AUTOSHARE = "autoshare"
        private const val KEY_TERMUX_ADD = "termux_add"
    }
}
