/*
 * 白い熊 fork (skui): the Export / Import panel, opened from the top of the
 * 白い熊 UI page — the Kōjiki flow. A bottom sheet with the persisted export
 * directory (queried on open for the latest export), one checklist of every
 * settable category driving both directions, and an ArcaneChat-style pill
 * button row (Cancel alone on the left, Import / Export on the right).
 * Success ends in a yellow-bordered info dialog whose acknowledgement closes
 * the whole chain — dialog, panel and the UI page; failures toast and leave
 * the panel open.
 */

package me.zhanghai.android.files.skui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream
import me.zhanghai.android.files.util.showToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WARN_COLOR = 0xFFFF5252.toInt()

class SkEximportSheet(
    private val activity: Activity,
    private val onPickDirectory: () -> Unit,
    private val onPickImportFile: () -> Unit,
    private val onDismissed: () -> Unit
) {
    private val density = activity.resources.displayMetrics.density
    private val accent = skColor(SkThemeSlot.ACCENT)
    private val textColor = skColor(SkThemeSlot.TEXT)
    private val backgroundColor = skColor(SkThemeSlot.BACKGROUND)

    private lateinit var dialog: BottomSheetDialog
    private lateinit var folderValueView: TextView
    private lateinit var statusView: TextView
    private val checks = LinkedHashMap<SkEximport.Cat, CheckBox>()

    private fun dp(value: Int): Int = (value * density).toInt()

    fun show() {
        dialog = BottomSheetDialog(activity)

        // The bordered box itself — inset from the sheet edges so all four
        // sides of its border are visible.
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(backgroundColor)
                setStroke(dp(2), accent)
            }
        }

        root.addView(
            text(activity.getString(R.string.sk_eximport_title), 18f, accent, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(6))
            }
        )
        root.addView(
            text(activity.getString(R.string.sk_eximport_desc), 13f, textColor).apply {
                alpha = 0.85f
                setPadding(0, 0, 0, dp(10))
            }
        )

        // The persisted export directory — a bordered, clearly-tappable box.
        val dirBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(backgroundColor)
                setStroke(dp(2), accent)
            }
            setOnClickListener { onPickDirectory() }
        }
        dirBox.addView(text(activity.getString(R.string.sk_eximport_dir), 12f, accent))
        folderValueView = text("", 15f, textColor, bold = true)
        dirBox.addView(folderValueView)
        root.addView(
            dirBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        )
        statusView = text("", 14f, textColor).apply { setPadding(dp(2), 0, 0, dp(8)) }
        root.addView(statusView)

        root.addView(divider())

        val selectAll = checkbox(activity.getString(R.string.sk_eximport_select_all), bold = true)
            .apply { isChecked = true }
        root.addView(selectAll)
        for (cat in SkEximport.Cat.entries) {
            val check = checkbox(activity.getString(cat.labelRes)).apply { isChecked = true }
            checks[cat] = check
            root.addView(check)
        }
        selectAll.setOnCheckedChangeListener { _, isChecked ->
            checks.values.forEach { it.isChecked = isChecked }
        }

        root.addView(divider(topGap = dp(8)))

        // ArcaneChat-style button row: round pills, Cancel alone on the left,
        // Import / Export grouped on the right.
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        buttons.addView(
            pillButton(activity.getString(android.R.string.cancel)).apply {
                setOnClickListener { dialog.dismiss() }
            }
        )
        buttons.addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f))
        buttons.addView(
            pillButton(activity.getString(R.string.sk_eximport_import)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { onImportClicked() }
            }
        )
        buttons.addView(
            pillButton(activity.getString(R.string.sk_eximport_export)).apply {
                setOnClickListener { onExportClicked() }
            }
        )
        root.addView(buttons)

        val scroll = NestedScrollView(activity).apply {
            val margin = dp(10)
            addView(
                root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(margin, margin, margin, margin) }
            )
        }
        dialog.setContentView(scroll)
        // Let our bordered background show instead of the sheet's own.
        (scroll.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.setOnDismissListener { onDismissed() }
        dialog.show()
        refreshStatus()
    }

    // --- The directory + last-export status ---

    fun onDirectoryPicked(path: Path) {
        SkEximport.exportDirPath = path
        refreshStatus()
    }

    private fun refreshStatus() {
        val dir = SkEximport.exportDirPath
        folderValueView.text =
            dir?.toUserFriendlyString() ?: activity.getString(R.string.sk_eximport_dir_unset)
        folderValueView.setTextColor(if (dir == null) WARN_COLOR else textColor)
        val newest = dir?.let { SkEximport.latestExport(it) }
        val (message, isWarning) = when {
            dir == null -> activity.getString(R.string.sk_eximport_warn_nodir) to true
            newest == null -> activity.getString(R.string.sk_eximport_warn_none) to true
            else ->
                activity.getString(R.string.sk_eximport_last, formatTimestamp(newest.second)) to
                    false
        }
        statusView.text = message
        statusView.setTextColor(if (isWarning) WARN_COLOR else textColor)
        statusView.alpha = if (isWarning) 1f else 0.8f
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))

    // --- Export ---

    private fun selectedCats(): Set<SkEximport.Cat> =
        checks.filterValues { it.isChecked }.keys

    private fun onExportClicked() {
        if (selectedCats().isEmpty()) {
            activity.showToast(R.string.sk_eximport_none_selected)
            return
        }
        val dir = SkEximport.exportDirPath
        if (dir == null) {
            onPickDirectory()
            return
        }
        val cats = selectedCats()
        activity.showToast(R.string.sk_eximport_exporting)
        Thread {
            val result = runCatching {
                val name = SkEximport.newExportFileName()
                dir.resolve(name).newOutputStream().use { SkEximport.export(cats, it) }
                name
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                result
                    .onSuccess { name ->
                        refreshStatus()
                        showExportDone(name)
                    }
                    .onFailure { e ->
                        activity.showToast(
                            activity.getString(R.string.sk_eximport_export_fail, e.toMessage())
                        )
                    }
            }
        }.start()
    }

    // --- Import ---

    private fun onImportClicked() {
        if (selectedCats().isEmpty()) {
            activity.showToast(R.string.sk_eximport_none_selected)
            return
        }
        onPickImportFile()
    }

    fun onImportFilePicked(file: Path) {
        val cats = selectedCats()
        activity.showToast(R.string.sk_eximport_importing)
        Thread {
            val result = runCatching {
                val bytes = file.newInputStream().use { it.readBytes() }
                val files = SkEximport.readZip(bytes)
                check(SkEximport.categoriesIn(files).isNotEmpty()) {
                    activity.getString(R.string.sk_eximport_import_none)
                }
                SkEximport.import(activity, files, cats)
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                result
                    .onSuccess { summary -> showImportDone(summary) }
                    .onFailure { e ->
                        activity.showToast(
                            activity.getString(R.string.sk_eximport_import_fail, e.toMessage())
                        )
                    }
            }
        }.start()
    }

    private fun Throwable.toMessage(): String = message ?: javaClass.simpleName

    // --- The result dialogs (yellow-bordered, closing the whole chain) ---

    private fun showExportDone(fileName: String) {
        showResultDialog(
            activity.getString(R.string.sk_eximport_export_done_title),
            activity.getString(R.string.sk_eximport_export_done_body, fileName)
        ) { infoDialog, buttons ->
            buttons.addView(
                pillButton(activity.getString(android.R.string.ok)).apply {
                    setOnClickListener {
                        infoDialog.dismiss()
                        closeChain()
                    }
                }
            )
        }
    }

    private fun showImportDone(summary: String) {
        showResultDialog(
            activity.getString(R.string.sk_eximport_import_done_title),
            activity.getString(R.string.sk_eximport_import_done_body, summary)
        ) { infoDialog, buttons ->
            buttons.addView(
                pillButton(activity.getString(R.string.sk_eximport_later)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(10) }
                    setOnClickListener {
                        infoDialog.dismiss()
                        closeChain()
                    }
                }
            )
            buttons.addView(
                pillButton(activity.getString(R.string.sk_eximport_restart_now)).apply {
                    setOnClickListener { SkEximport.restartApp(activity) }
                }
            )
        }
    }

    // Acknowledging a result closes the info dialog, the panel beneath it and
    // the UI page itself.
    private fun closeChain() {
        dialog.dismiss()
        activity.finish()
    }

    // A fully custom bordered box handed to an AlertDialog whose own window is
    // made transparent — Material's dialog surface can't render our border.
    private fun showResultDialog(
        title: String,
        body: String,
        addButtons: (AlertDialog, LinearLayout) -> Unit
    ) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(backgroundColor)
                setStroke(dp(2), accent)
            }
        }
        box.addView(text(title, 19f, accent, bold = true))
        box.addView(text(body, 14f, accent).apply { setPadding(0, dp(10), 0, 0) })
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(16), 0, 0)
        }
        val infoDialog = AlertDialog.Builder(activity)
            .setView(NestedScrollView(activity).apply { addView(box) })
            .setCancelable(false)
            .create()
        addButtons(infoDialog, buttons)
        box.addView(buttons)
        infoDialog.show()
        infoDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    // --- View helpers ---

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
        }

    private fun divider(topGap: Int = 0): View =
        View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = topGap }
            setBackgroundColor(accent)
            alpha = 0.4f
        }

    private fun checkbox(label: String, bold: Boolean = false): CheckBox =
        CheckBox(activity).apply {
            text = label
            textSize = 15f
            setTextColor(textColor)
            if (bold) {
                setTypeface(typeface, Typeface.BOLD)
            }
            buttonTintList = ColorStateList.valueOf(accent)
            setPadding(dp(8), dp(7), 0, dp(7))
        }

    // An ArcaneChat-style round pill: black fill, thin accent stroke, accent
    // text, accent ripple.
    private fun pillButton(label: String): Button =
        Button(activity).apply {
            text = label
            isAllCaps = false
            setTextColor(accent)
            background = RippleDrawable(
                ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
                GradientDrawable().apply {
                    cornerRadius = 50 * density
                    setColor(backgroundColor)
                    setStroke((1.5f * density).toInt(), accent)
                },
                null
            )
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
}
