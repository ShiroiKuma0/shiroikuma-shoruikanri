/*
 * 白い熊 fork: the extraction progress dialog. It shows what SkExtractProgress.Session reports —
 * progress bar, sizes, speed, time left, elapsed time — and has two buttons: Cancel stops the job,
 * Hide closes the dialog and leaves the job to its notification, which opens the dialog again.
 *
 * The name of the extracted item can be edited from the first moment. The job applies it as soon
 * as the item is complete; a name still pending when the job is over is applied by Done.
 */

package me.zhanghai.android.files.filejob

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.SkExtractProgressDialogBinding
import me.zhanghai.android.files.skui.SkMaterialAlertDialogBuilder
import me.zhanghai.android.files.skui.SkThemeSlot
import me.zhanghai.android.files.skui.SkUi
import me.zhanghai.android.files.skui.applySkSlot
import me.zhanghai.android.files.skui.skColor
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.layoutInflater

class SkExtractProgressDialogFragment : AppCompatDialogFragment() {
    private lateinit var binding: SkExtractProgressDialogBinding

    private var session: SkExtractProgress.Session? = null

    private var snapshot: SkExtractProgress.Snapshot? = null

    // 白い熊 has typed in the name field, so the job's own name no longer overwrites it.
    private var isNameEdited = false

    private var isSettingName = false

    // The job is over and the dialog stayed open for a name that is still being edited.
    private var isFinishedMode = false

    // Set by the activity when it swaps this dialog for another job's.
    var isReplaced = false

    private val jobId: Int
        get() = requireArguments().getInt(ARGUMENT_JOB_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = SkExtractProgress.get(jobId)?.also { it.onDialogAttached() }
        isNameEdited = savedInstanceState?.getBoolean(STATE_NAME_EDITED) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_NAME_EDITED, isNameEdited)
    }

    override fun onDestroy() {
        super.onDestroy()

        session?.onDialogDetached()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        SkMaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(session?.kind?.titleRes ?: R.string.sk_extract_progress_title)
            .apply {
                binding = SkExtractProgressDialogBinding.inflate(context.layoutInflater)
                setView(binding.root)
            }
            .setPositiveButton(R.string.sk_extract_progress_hide) { _, _ -> onPositive() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelJob() }
            .create()
            .apply {
                // A stray touch beside the dialog must not hide it; Back does.
                setCanceledOnTouchOutside(false)
                // The numbers are being watched: do not let the screen sleep under them.
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // The file list stays in full view behind the dialog: no dimming.
                window?.setDimAmount(0f)
                bindViews()
            }

    private fun bindViews() {
        val session = session
        if (session == null) {
            // The job is gone (a stale notification, or the process was restarted).
            return
        }
        applySkUi()
        binding.sourceText.text = getString(
            R.string.sk_extract_progress_source_format, session.sourceName
        )
        binding.targetText.text = getString(
            R.string.sk_extract_progress_target_format, session.targetDirectory.toString()
        )
        binding.nameLayout.isVisible = session.isRenamable
        binding.entryText.isVisible = false
        binding.entryProgress.isVisible = false
        binding.entrySizeText.isVisible = false
        binding.totalLabel.isVisible = false
        binding.nameEdit.doAfterTextChanged { onNameChanged() }
        binding.nameEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (isFinishedMode) {
                    onPositive()
                    finish()
                } else {
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }
        session.liveData.observe(this) { onSnapshot(it) }
        lifecycleScope.launchWhenStarted {
            while (true) {
                renderElapsed()
                delay(ELAPSED_INTERVAL_MILLIS)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (session == null) {
            finish()
        }
    }

    // Accent bar on a dim track, accent speed line — the colours of the download stage in
    // 白い熊 Universal installer. Without the 白い熊 theme the Material defaults stay.
    private fun applySkUi() {
        if (!SkUi.isSkThemeEnabled) {
            return
        }
        val bar = skColor(SkThemeSlot.EXTRACT_PROGRESS_BAR)
        val info = skColor(SkThemeSlot.EXTRACT_PROGRESS_INFO)
        binding.progress.setIndicatorColor(bar)
        binding.progress.trackColor = skColor(SkThemeSlot.EXTRACT_PROGRESS_TRACK)
        binding.entryProgress.setIndicatorColor(bar)
        binding.entryProgress.trackColor = skColor(SkThemeSlot.EXTRACT_PROGRESS_TRACK)
        for (infoText in listOf(
            binding.sourceText, binding.entryText, binding.sizeText, binding.targetText,
            binding.elapsedText, binding.entrySizeText, binding.totalLabel
        )) {
            infoText.applySkSlot(SkThemeSlot.EXTRACT_PROGRESS_INFO)
        }
        binding.speedText.applySkSlot(SkThemeSlot.EXTRACT_PROGRESS_SPEED)
        binding.nameEdit.applySkSlot(SkThemeSlot.EXTRACT_PROGRESS_NAME)
        binding.nameLayout.boxStrokeColor = bar
        binding.nameLayout.hintTextColor = ColorStateList.valueOf(bar)
        binding.nameLayout.defaultHintTextColor = ColorStateList.valueOf(info)
    }

    private fun onSnapshot(snapshot: SkExtractProgress.Snapshot) {
        this.snapshot = snapshot
        val context = requireContext()

        val name = snapshot.name
        if (name != null && !isNameEdited && binding.nameEdit.text?.toString() != name) {
            isSettingName = true
            binding.nameEdit.setText(name)
            isSettingName = false
        }

        when (snapshot.phase) {
            SkExtractProgress.Phase.PREPARING ->
                binding.sizeText.setText(R.string.sk_extract_progress_preparing)
            SkExtractProgress.Phase.EXTRACTING, SkExtractProgress.Phase.FINISHED -> {
                binding.progress.setProgressCompat(
                    (snapshot.fraction * PROGRESS_MAX).toInt(), true
                )
                binding.sizeText.text = getString(
                    R.string.sk_extract_progress_size_format,
                    SkExtractProgress.formatSize(snapshot.transferredSize, context),
                    SkExtractProgress.formatSize(snapshot.size, context),
                    (snapshot.fraction * 100).toInt()
                )
            }
            SkExtractProgress.Phase.CANCELLED -> {}
        }

        val showEntry = snapshot.phase == SkExtractProgress.Phase.EXTRACTING
            && snapshot.fileCount > 1 && snapshot.currentEntry != null
        binding.entryText.isVisible = showEntry
        // Several files: one bar for the file being written, one for the whole job.
        binding.entryProgress.isVisible = showEntry
        binding.entrySizeText.isVisible = showEntry
        binding.totalLabel.isVisible = showEntry
        if (showEntry) {
            binding.entryProgress.setProgressCompat(
                (snapshot.entryFraction * PROGRESS_MAX).toInt(), false
            )
            binding.entrySizeText.text = getString(
                R.string.sk_extract_progress_size_format,
                SkExtractProgress.formatSize(snapshot.entryTransferredSize, context),
                SkExtractProgress.formatSize(snapshot.entrySize, context),
                (snapshot.entryFraction * 100).toInt()
            )
        }
        if (showEntry) {
            binding.entryText.text = getString(
                R.string.sk_extract_progress_entry_format, snapshot.fileIndex,
                snapshot.fileCount, snapshot.currentEntry
            )
        }

        binding.speedText.text = when (snapshot.phase) {
            SkExtractProgress.Phase.EXTRACTING ->
                SkExtractProgress.formatSpeedAndEta(snapshot, context)
            SkExtractProgress.Phase.FINISHED -> if (snapshot.speed > 0) {
                getString(
                    R.string.sk_extract_progress_average_format,
                    SkExtractProgress.formatSpeed(snapshot.speed, context)
                )
            } else {
                null
            }
            else -> null
        }
        renderElapsed()

        when (snapshot.phase) {
            SkExtractProgress.Phase.FINISHED -> onJobFinished()
            SkExtractProgress.Phase.CANCELLED -> finish()
            else -> {}
        }
    }

    private fun renderElapsed() {
        val session = session ?: return
        binding.elapsedText.text = getString(
            R.string.sk_extract_progress_elapsed_format,
            SkExtractProgress.formatDuration(session.elapsedMillis / 1000)
        )
    }

    // The valid name in the field when it differs from the one the item has, else null.
    private val pendingName: String?
        get() {
            val session = session ?: return null
            if (!session.isRenamable || !isNameEdited) {
                return null
            }
            val name = binding.nameEdit.text?.toString() ?: return null
            if (name.asFileNameOrNull() == null || name == "." || name == "..") {
                return null
            }
            return name.takeIf { it != snapshot?.name }
        }

    private fun onNameChanged() {
        if (isSettingName) {
            return
        }
        isNameEdited = true
        val name = binding.nameEdit.text?.toString().orEmpty()
        val isValid = name.asFileNameOrNull() != null && name != "." && name != ".."
        binding.nameLayout.error = if (isValid) {
            null
        } else {
            getString(R.string.sk_extract_progress_name_invalid)
        }
        session?.requestedName = pendingName
    }

    private fun onJobFinished() {
        if (isFinishedMode) {
            return
        }
        // Nothing left to decide: show the full bar for a moment, then get out of the way.
        if (pendingName == null && !binding.nameEdit.hasFocus()) {
            lifecycleScope.launchWhenStarted {
                delay(AUTO_CLOSE_DELAY_MILLIS)
                if (pendingName == null && !binding.nameEdit.hasFocus()) {
                    finish()
                } else {
                    enterFinishedMode()
                }
            }
            return
        }
        enterFinishedMode()
    }

    // The job is over but the name is still being edited: stay, and let Done apply it.
    private fun enterFinishedMode() {
        isFinishedMode = true
        val dialog = dialog as? AlertDialog ?: return
        dialog.setTitle(session?.kind?.doneTitleRes ?: R.string.sk_extract_progress_title_done)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.isVisible = false
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            ?.setText(R.string.sk_extract_progress_done)
    }

    private fun onPositive() {
        val session = session ?: return
        if (!session.isFinished) {
            session.isHiddenByUser = true
            return
        }
        val path = session.finalPath ?: return
        val name = pendingName ?: return
        FileJobService.rename(path, name, requireContext())
    }

    private fun cancelJob() {
        FileJobService.cancelJob(jobId)
    }

    private fun hideKeyboard() {
        binding.nameEdit.clearFocus()
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.nameEdit.windowToken, 0)
    }

    // Back: the same as Hide.
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        onPositive()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        // Also called when a rotation tears the view down: the dialog comes back then.
        val activity = activity ?: return
        if (!isReplaced && !activity.isChangingConfigurations) {
            activity.finish()
        }
    }

    companion object {
        private const val ARGUMENT_JOB_ID = "jobId"

        private const val STATE_NAME_EDITED = "nameEdited"

        private const val PROGRESS_MAX = 10000

        private const val ELAPSED_INTERVAL_MILLIS = 500L

        private const val AUTO_CLOSE_DELAY_MILLIS = 700L

        fun newInstance(jobId: Int): SkExtractProgressDialogFragment =
            SkExtractProgressDialogFragment().apply {
                arguments = bundleOf(ARGUMENT_JOB_ID to jobId)
            }
    }
}
