/*
 * 白い熊 fork: gocryptfs provider — native attribute struct.
 *
 * Returned by GocryptfsVolume.native_get_attr. The JNI glue in libgocryptfs_jni.so
 * CONSTRUCTS this object directly, so this class's fully-qualified name and the
 * (Int, Long, Long) constructor signature are part of the binary contract with the
 * gcfs-side native build — do not rename/repackage without updating the glue.
 *
 * `mTime` is whole seconds since the Unix epoch (the glue fills it from st_mtime).
 */

package me.zhanghai.android.files.provider.gocryptfs.client

class GocryptfsStat(val mode: Int, val size: Long, val mTime: Long) {
    val type: Int
        get() = mode and S_IFMT
    val isDirectory: Boolean
        get() = type == S_IFDIR
    val isRegularFile: Boolean
        get() = type == S_IFREG
    val isSymbolicLink: Boolean
        get() = type == S_IFLNK

    companion object {
        const val S_IFMT = 0xF000
        const val S_IFDIR = 0x4000
        const val S_IFREG = 0x8000
        const val S_IFLNK = 0xA000
    }
}
