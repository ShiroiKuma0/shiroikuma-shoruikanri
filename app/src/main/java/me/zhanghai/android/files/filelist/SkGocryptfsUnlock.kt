/*
 * 白い熊 fork: gocryptfs volume-open flow.
 *
 * Invoked by the lock button (shown only when the current directory is a gocryptfs cipher dir):
 * prompt for the password, unlock the volume via GocryptfsFileSystemProvider, and navigate the
 * file list into the decrypted root.
 */

package me.zhanghai.android.files.filelist

import android.text.InputType
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.gocryptfs.GocryptfsFileSystemProvider
import me.zhanghai.android.files.provider.gocryptfs.gocryptfsCipherDirOrNull
import me.zhanghai.android.files.provider.linux.LinuxPath
import me.zhanghai.android.files.skui.SkMaterialAlertDialogBuilder

object SkGocryptfsUnlock {
    fun prompt(fragment: FileListFragment, cipherDir: Path) {
        // The button is only shown for real (linux) cipher dirs, but guard anyway.
        if (cipherDir !is LinuxPath) {
            return
        }
        val context = fragment.requireContext()
        val passwordEdit = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = context.getString(R.string.sk_gocryptfs_unlock_hint)
        }
        SkMaterialAlertDialogBuilder(context)
            .setTitle(R.string.sk_gocryptfs_unlock_title)
            .setMessage(cipherDir.fileName?.toString() ?: cipherDir.toString())
            .setView(passwordEdit)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sk_gocryptfs_unlock_action) { _, _ ->
                unlock(fragment, cipherDir, passwordEdit.text.toString())
            }
            .show()
    }

    private fun unlock(fragment: FileListFragment, cipherDir: Path, password: String) {
        val context = fragment.requireContext()
        fragment.lifecycleScope.launch {
            val fileSystem = withContext(Dispatchers.IO) {
                try {
                    GocryptfsFileSystemProvider.openFileSystem(cipherDir, password.toByteArray())
                } catch (e: Throwable) {
                    null
                }
            }
            if (fileSystem == null) {
                SkMaterialAlertDialogBuilder(context)
                    .setTitle(R.string.sk_gocryptfs_unlock_title)
                    .setMessage(R.string.sk_gocryptfs_unlock_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            fragment.navigateTo(fileSystem.rootDirectory)
        }
    }

    /** Lock (close) the volume that [volumePath] belongs to and navigate back out to its cipher dir. */
    fun lock(fragment: FileListFragment, volumePath: Path) {
        val cipherDir = volumePath.gocryptfsCipherDirOrNull ?: return
        // Leave the decrypted view first, then release the native session.
        fragment.navigateTo(cipherDir)
        GocryptfsFileSystemProvider.closeFileSystem(cipherDir)
    }
}
