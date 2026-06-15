/*
 * 白い熊 fork: gocryptfs provider — read-only POSIX attribute view.
 *
 * readAttributes() comes from native_get_attr; all setters reject (the Phase-2 provider is
 * read-only).
 */

package me.zhanghai.android.files.provider.gocryptfs

import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.PosixFileAttributeView
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import me.zhanghai.android.files.provider.common.ReadOnlyFileSystemException
import java.io.IOException

internal class GocryptfsFileAttributeView(
    private val path: GocryptfsPath
) : PosixFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): GocryptfsFileAttributes {
        val stat = path.fileSystem.getStat(path)
        return GocryptfsFileAttributes.from(stat, path)
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        throw ReadOnlyFileSystemException(path.toString())
    }

    override fun setOwner(owner: PosixUser) {
        throw ReadOnlyFileSystemException(path.toString())
    }

    override fun setGroup(group: PosixGroup) {
        throw ReadOnlyFileSystemException(path.toString())
    }

    override fun setMode(mode: Set<PosixFileModeBit>) {
        throw ReadOnlyFileSystemException(path.toString())
    }

    override fun setSeLinuxContext(context: ByteString) {
        throw ReadOnlyFileSystemException(path.toString())
    }

    override fun restoreSeLinuxContext() {
        throw ReadOnlyFileSystemException(path.toString())
    }

    companion object {
        private val NAME = GocryptfsFileSystemProvider.scheme

        val SUPPORTED_NAMES = setOf("basic", "posix", NAME)
    }
}
