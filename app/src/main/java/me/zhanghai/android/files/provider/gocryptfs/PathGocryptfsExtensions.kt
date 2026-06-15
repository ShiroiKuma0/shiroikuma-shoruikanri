/*
 * 白い熊 fork: gocryptfs provider — navigate to an unlocked volume's root.
 *
 * Mirrors PathArchiveExtensions.createArchiveRootPath. The volume must already be unlocked via
 * GocryptfsFileSystemProvider.openFileSystem; otherwise operations on the returned path fail with
 * a "volume is locked" IOException.
 */

package me.zhanghai.android.files.provider.gocryptfs

import java8.nio.file.Path

fun Path.createGocryptfsRootPath(): Path =
    GocryptfsFileSystemProvider.getOrNewFileSystem(this).rootDirectory

/** The backing cipher dir of a gocryptfs path, or null if this isn't one. */
val Path.gocryptfsCipherDirOrNull: Path?
    get() = (this as? GocryptfsPath)?.cipherDir
