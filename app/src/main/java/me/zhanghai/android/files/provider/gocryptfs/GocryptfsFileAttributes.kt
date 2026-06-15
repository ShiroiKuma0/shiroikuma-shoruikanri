/*
 * 白い熊 fork: gocryptfs provider — POSIX attributes from a native Stat.
 *
 * Modeled on SftpFileAttributes. `mTime` from the native layer is whole seconds; size is the
 * plaintext size. Owner/group/SELinux context are not exposed by gocryptfs.
 */

package me.zhanghai.android.files.provider.gocryptfs

import android.os.Parcelable
import java.time.Instant
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.provider.common.AbstractPosixFileAttributes
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.FileTimeParceler
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.provider.common.PosixGroup
import me.zhanghai.android.files.provider.common.PosixUser
import me.zhanghai.android.files.provider.gocryptfs.client.GocryptfsStat

@Parcelize
internal data class GocryptfsFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: PosixFileType,
    override val size: Long,
    override val fileKey: Parcelable,
    override val owner: PosixUser?,
    override val group: PosixGroup?,
    override val mode: Set<PosixFileModeBit>?,
    override val seLinuxContext: ByteString?
) : AbstractPosixFileAttributes() {
    companion object {
        fun from(stat: GocryptfsStat, path: GocryptfsPath): GocryptfsFileAttributes {
            val lastModifiedTime = FileTime.from(Instant.ofEpochSecond(stat.mTime))
            val lastAccessTime = lastModifiedTime
            val creationTime = lastModifiedTime
            val type = PosixFileType.fromMode(stat.mode)
            val size = stat.size
            val fileKey = path
            val mode = PosixFileMode.fromInt(stat.mode)
            return GocryptfsFileAttributes(
                lastModifiedTime, lastAccessTime, creationTime, type, size, fileKey, null, null,
                mode, null
            )
        }
    }
}
