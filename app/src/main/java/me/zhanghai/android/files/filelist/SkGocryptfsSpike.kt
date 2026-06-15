/*
 * 白い熊 fork: TEMPORARY gocryptfs "open volume" trigger (Phases 1–2).
 *
 * Takes the current real (linux) path as a gocryptfs cipher dir, prompts for the password,
 * unlocks the volume via GocryptfsFileSystemProvider, and navigates the file list into the
 * decrypted root — so the rest of the MaterialFiles UI browses it through the read-only
 * gocryptfs provider. Stands in for the proper volume-open UI until Phase 4.
 *
 * Progress is appended to /sdcard/tmp/gocryptfs-spike.log so a process-killing native crash
 * still leaves a breadcrumb. DELETE this file, its menu item (action_sk_gocryptfs_spike), and
 * its string once the real volume-open UI lands.
 */

package me.zhanghai.android.files.filelist

import android.text.InputType
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import java.io.File
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.provider.gocryptfs.GocryptfsFileSystemProvider
import me.zhanghai.android.files.provider.linux.LinuxPath
import me.zhanghai.android.files.skui.SkMaterialAlertDialogBuilder

object SkGocryptfsSpike {
    fun run(fragment: FileListFragment, path: Path) {
        val context = fragment.requireContext()
        if (path !is LinuxPath) {
            SkMaterialAlertDialogBuilder(context)
                .setTitle("Open gocryptfs volume")
                .setMessage(
                    "Current path is not a real (linux) path:\n$path\n\n" +
                        "Navigate into the cipher dir on real storage first."
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val passwordEdit = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Volume password"
        }
        SkMaterialAlertDialogBuilder(context)
            .setTitle("Open gocryptfs volume")
            .setMessage("Cipher dir:\n$path")
            .setView(passwordEdit)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Unlock + browse") { _, _ ->
                unlockAndBrowse(fragment, path, passwordEdit.text.toString())
            }
            .show()
    }

    private fun unlockAndBrowse(fragment: FileListFragment, cipherDir: Path, password: String) {
        val context = fragment.requireContext()
        fragment.lifecycleScope.launch {
            log("==== unlock ====")
            log("cipherDir=$cipherDir")
            log("-> openFileSystem")
            val fileSystem = withContext(Dispatchers.IO) {
                try {
                    GocryptfsFileSystemProvider.openFileSystem(cipherDir, password.toByteArray())
                } catch (e: Throwable) {
                    log("openFileSystem threw: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
            if (fileSystem == null) {
                log("openFileSystem -> null (locked / wrong password / not a volume)")
                SkMaterialAlertDialogBuilder(context)
                    .setTitle("Open gocryptfs volume")
                    .setMessage("Failed to unlock — wrong password, or not a gocryptfs volume.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            log("openFileSystem OK -> navigating to volume root")
            fragment.navigateTo(fileSystem.rootDirectory)
        }
    }

    // Append (and flush) to a log file so a process-killing native crash leaves a breadcrumb.
    private val logFile = File("/sdcard/tmp/gocryptfs-spike.log")

    private fun log(line: String) {
        try {
            logFile.parentFile?.mkdirs()
            logFile.appendText(line + "\n")
        } catch (e: Throwable) {
            // Best-effort.
        }
    }
}
