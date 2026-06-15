/*
 * 白い熊 fork: path access diagnostic.
 *
 * Builds a human-readable report for a given nio Path so we can tell, unambiguously,
 * whether a location (e.g. the OTG USB) is reached via the real-POSIX linux provider
 * (file:) or via SAF (content://) — and, for a real path, what the app's own native
 * syscall layer sees (exists / type / read / write, with errno distinguished).
 *
 * Also enumerates every StorageManager volume (uuid / getDirectory() / path / state),
 * which is how one reads off the USB's /storage/XXXX-XXXX path even while sitting on a
 * SAF location, and reports whether All-Files-Access is held.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageVolume
import android.system.OsConstants
import java8.nio.file.Path
import me.zhanghai.android.files.compat.directoryCompat
import me.zhanghai.android.files.compat.getDescriptionCompat
import me.zhanghai.android.files.compat.isEmulatedCompat
import me.zhanghai.android.files.compat.isPrimaryCompat
import me.zhanghai.android.files.compat.isRemovableCompat
import me.zhanghai.android.files.compat.pathCompat
import me.zhanghai.android.files.compat.stateCompat
import me.zhanghai.android.files.compat.uuidCompat
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.linux.LinuxPath
import me.zhanghai.android.files.provider.linux.syscall.Syscall
import me.zhanghai.android.files.provider.linux.syscall.SyscallException
import me.zhanghai.android.files.storage.StorageVolumeListLiveData
import me.zhanghai.android.files.util.valueCompat

object SkPathDiagnostics {
    fun report(context: Context, path: Path): String {
        val sb = StringBuilder()

        val provider = path.fileSystem.provider()
        val scheme = try { provider.scheme } catch (e: Exception) { "?" }
        val uri = try { path.toUri().toString() } catch (e: Exception) { "<toUri failed: ${e.message}>" }
        sb.appendLine("Location: $path")
        sb.appendLine("URI:      $uri")
        sb.appendLine("Scheme:   $scheme")
        sb.appendLine("Provider: ${provider.javaClass.simpleName}")
        sb.appendLine("Backing:  ${backingLabel(scheme)}")
        sb.appendLine()

        if (path is LinuxPath) {
            sb.appendLine("Native probe (app syscall layer):")
            val bytes = path.toByteString()
            try {
                val st = Syscall.stat(bytes)
                sb.appendLine(
                    "  stat: OK  type=${fileTypeLabel(st.st_mode)}" +
                        "  size=${st.st_size}" +
                        "  mode=${Integer.toOctalString(st.st_mode and 0xFFF)}" +
                        "  uid=${st.st_uid} gid=${st.st_gid}"
                )
                sb.appendLine("  exists:          yes")
                sb.appendLine("  isDirectory:     ${OsConstants.S_ISDIR(st.st_mode)}")
            } catch (e: SyscallException) {
                sb.appendLine("  stat: ERROR ${errnoLabel(e.errno)}")
                sb.appendLine(
                    "  exists:          " +
                        if (e.errno == OsConstants.ENOENT) "no (ENOENT)"
                        else "unknown (${errnoLabel(e.errno)})"
                )
            }
            sb.appendLine("  readable (R_OK): ${accessLabel(bytes, OsConstants.R_OK)}")
            sb.appendLine("  writable (W_OK): ${accessLabel(bytes, OsConstants.W_OK)}")
            sb.appendLine()
        }

        sb.appendLine("Storage volumes (StorageManager):")
        val currentLinuxPath = (path as? LinuxPath)?.toString()
        val volumes = try {
            StorageVolumeListLiveData.valueCompat
        } catch (e: Exception) {
            emptyList<StorageVolume>()
        }
        if (volumes.isEmpty()) {
            sb.appendLine("  (none reported)")
        } else {
            for (volume in volumes) {
                val volumePath = try { volume.pathCompat } catch (e: Exception) { null }
                val isCurrent = volumePath != null && currentLinuxPath != null &&
                    (currentLinuxPath == volumePath || currentLinuxPath.startsWith("$volumePath/"))
                sb.appendLine(
                    "  • ${describeVolume(context, volume)}" + if (isCurrent) "  ← current" else ""
                )
            }
        }
        sb.appendLine()

        val allFilesAccess = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                if (Environment.isExternalStorageManager()) "granted" else "NOT granted"
            else -> "n/a (< Android 11)"
        }
        sb.appendLine("All files access (MANAGE_EXTERNAL_STORAGE): $allFilesAccess")
        sb.appendLine()

        sb.append("readdir(\"/storage\"): ").append(readdirStorage())

        return sb.toString().trimEnd()
    }

    private fun backingLabel(scheme: String): String =
        when (scheme) {
            "file" -> "linux / real POSIX (file:)"
            "document" -> "SAF document tree (content://)"
            "content" -> "single content:// document"
            "archive" -> "archive entry"
            else -> scheme
        }

    private fun describeVolume(context: Context, volume: StorageVolume): String {
        fun <T> safe(block: () -> T): T? = try { block() } catch (e: Exception) { null }
        val description = safe { volume.getDescriptionCompat(context) }
        val uuid = safe { volume.uuidCompat }
        val directory = safe { volume.directoryCompat?.path }
        val path = safe { volume.pathCompat }
        val state = safe { volume.stateCompat } ?: "?"
        val flags = buildList {
            if (safe { volume.isPrimaryCompat } == true) add("primary")
            if (safe { volume.isRemovableCompat } == true) add("removable")
            if (safe { volume.isEmulatedCompat } == true) add("emulated")
        }.joinToString(",").ifEmpty { "-" }
        return "desc=$description  uuid=$uuid  getDirectory()=$directory  " +
            "path=$path  state=$state  [$flags]"
    }

    private fun accessLabel(bytes: me.zhanghai.android.files.provider.common.ByteString, mode: Int): String =
        try {
            if (Syscall.access(bytes, mode)) "yes" else "no"
        } catch (e: SyscallException) {
            errnoLabel(e.errno)
        }

    private fun readdirStorage(): String {
        val dir = try {
            Syscall.opendir("/storage".toByteString())
        } catch (e: SyscallException) {
            return "ERROR ${errnoLabel(e.errno)}"
        } catch (e: Exception) {
            return "ERROR ${e.message}"
        }
        if (dir == 0L) {
            return "ERROR (opendir returned null)"
        }
        return try {
            val names = mutableListOf<String>()
            while (true) {
                val entry = Syscall.readdir(dir) ?: break
                val name = entry.d_name.toString()
                if (name == "." || name == "..") {
                    continue
                }
                names.add(name)
                if (names.size >= 50) {
                    names.add("…")
                    break
                }
            }
            if (names.isEmpty()) "(empty)" else "${names.size} entries: ${names.joinToString(", ")}"
        } catch (e: SyscallException) {
            "ERROR ${errnoLabel(e.errno)}"
        } catch (e: Exception) {
            "ERROR ${e.message}"
        } finally {
            try {
                Syscall.closedir(dir)
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }

    private fun fileTypeLabel(mode: Int): String =
        when {
            OsConstants.S_ISDIR(mode) -> "directory"
            OsConstants.S_ISREG(mode) -> "regular file"
            OsConstants.S_ISLNK(mode) -> "symlink"
            OsConstants.S_ISBLK(mode) -> "block device"
            OsConstants.S_ISCHR(mode) -> "char device"
            OsConstants.S_ISFIFO(mode) -> "fifo"
            OsConstants.S_ISSOCK(mode) -> "socket"
            else -> "unknown"
        }

    private fun errnoLabel(errno: Int): String =
        "${OsConstants.errnoName(errno) ?: "errno $errno"} (${Syscall.strerror(errno)})"
}
