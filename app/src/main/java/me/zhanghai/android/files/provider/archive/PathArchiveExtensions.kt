/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive

import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException

fun Path.archiveAddPassword(password: String) {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    fileSystem.addPassword(password)
}

val Path.archiveFile: Path
    get() {
        this as? ArchivePath ?: throw ProviderMismatchException(toString())
        return fileSystem.archiveFile
    }

fun Path.archiveRefresh() {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    fileSystem.refresh()
}

fun Path.createArchiveRootPath(): Path =
    ArchiveFileSystemProvider.getOrNewFileSystem(this).rootDirectory

// 白い熊 fork: for a one-pass extraction (see ArchiveReader.beginExtraction).

/** The name this path's entry has inside the archive. */
fun Path.skArchiveEntryName(): String {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    return fileSystem.getEntry(this).name
}

/** The position of every entry of this path's archive, by entry name. */
fun Path.skArchiveEntryOrder(): Map<String, Int> {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    return fileSystem.skEntryOrder()
}

/** Read this path's archive in one pass on the calling thread; close the result when done. */
fun Path.skBeginArchiveExtraction(): java.io.Closeable {
    this as? ArchivePath ?: throw ProviderMismatchException(toString())
    return me.zhanghai.android.files.provider.archive.archiver.ArchiveReader
        .beginExtraction(fileSystem.archiveFile)
}
