/*
 * 白い熊 fork: gocryptfs provider — same-provider copy/move.
 *
 * Called by Path.copyTo/moveTo when both paths share this provider (gocryptfs -> gocryptfs);
 * cross-provider transfers (e.g. internal storage -> volume) go through the generic stream path
 * using newByteChannel instead. Per-entry (not recursive) — the file job walks the tree. Modeled
 * on SftpCopyMove, simplified: gocryptfs exposes only files and directories (no symlinks /
 * owner / group).
 */

package me.zhanghai.android.files.provider.gocryptfs

import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.StandardCopyOption
import java8.nio.file.StandardOpenOption
import me.zhanghai.android.files.provider.common.CopyOptions
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.copyTo
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream
import me.zhanghai.android.files.provider.common.toOpenOptions
import java.io.IOException

internal object GocryptfsCopyMove {
    private val READ_OPTIONS =
        setOf<OpenOption>(StandardOpenOption.READ).toOpenOptions()
    private val WRITE_OPTIONS =
        setOf<OpenOption>(
            StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
        ).toOpenOptions()

    @Throws(IOException::class)
    fun copy(source: GocryptfsPath, target: GocryptfsPath, copyOptions: CopyOptions) {
        if (copyOptions.atomicMove) {
            throw UnsupportedOperationException(StandardCopyOption.ATOMIC_MOVE.toString())
        }
        val sourceStat = source.fileSystem.getStat(source)
        val targetStat = try {
            target.fileSystem.getStat(target)
        } catch (e: NoSuchFileException) {
            null
        }
        val sourceSize = sourceStat.size
        if (targetStat != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(sourceSize)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
        }
        if (sourceStat.isDirectory) {
            when {
                targetStat == null -> target.fileSystem.createDirectory(target)
                !targetStat.isDirectory -> {
                    target.fileSystem.delete(target)
                    target.fileSystem.createDirectory(target)
                }
                else -> {
                    // Target directory already exists — merge.
                }
            }
            copyOptions.progressListener?.invoke(sourceSize)
        } else {
            if (targetStat != null) {
                target.fileSystem.delete(target)
            }
            source.fileSystem.newByteChannel(source, READ_OPTIONS).newInputStream()
                .use { inputStream ->
                    target.fileSystem.newByteChannel(target, WRITE_OPTIONS).newOutputStream()
                        .use { outputStream ->
                            inputStream.copyTo(
                                outputStream, copyOptions.progressIntervalMillis,
                                copyOptions.progressListener
                            )
                        }
                }
        }
    }

    @Throws(IOException::class)
    fun move(source: GocryptfsPath, target: GocryptfsPath, copyOptions: CopyOptions) {
        val sourceFileSystem = source.fileSystem
        val targetFileSystem = target.fileSystem
        val targetStat = try {
            targetFileSystem.getStat(target)
        } catch (e: NoSuchFileException) {
            null
        }
        if (targetStat != null) {
            if (source == target) {
                copyOptions.progressListener?.invoke(0)
                return
            }
            if (!copyOptions.replaceExisting) {
                throw FileAlreadyExistsException(source.toString(), target.toString(), null)
            }
            targetFileSystem.delete(target)
        }
        if (sourceFileSystem == targetFileSystem && sourceFileSystem.rename(source, target)) {
            LocalWatchService.onEntryDeleted(source)
            LocalWatchService.onEntryCreated(target)
            copyOptions.progressListener?.invoke(0)
            return
        }
        if (copyOptions.atomicMove) {
            throw IOException("Atomic move is not supported across gocryptfs volumes")
        }
        // Fallback: copy + delete (not recursive — same-volume dir moves use native_rename above).
        copy(source, target, copyOptions)
        sourceFileSystem.delete(source)
    }
}
