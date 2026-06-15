/*
 * 白い熊 fork: gocryptfs provider — one directory entry.
 *
 * The elements of the list returned by GocryptfsVolume.native_list_dir. The JNI glue
 * creates each one via the static [new] factory (mirroring DroidFS, which used a factory
 * to avoid JNI trouble with default-valued constructor args). This class's
 * fully-qualified name and the `new(String, Int, Long, Long)` signature are part of the
 * binary contract with libgocryptfs_jni.so — keep in sync with the gcfs-side glue.
 *
 * `mTime` is whole seconds since the Unix epoch.
 */

package me.zhanghai.android.files.provider.gocryptfs.client

class GocryptfsEntry(
    val name: String,
    val mode: Int,
    val size: Long,
    val mTime: Long,
    val parentPath: String
) {
    val type: Int
        get() = mode and GocryptfsStat.S_IFMT
    val isDirectory: Boolean
        get() = type == GocryptfsStat.S_IFDIR
    val isRegularFile: Boolean
        get() = type == GocryptfsStat.S_IFREG
    val isSymbolicLink: Boolean
        get() = type == GocryptfsStat.S_IFLNK

    fun toStat(): GocryptfsStat = GocryptfsStat(mode, size, mTime)

    companion object {
        // Signature mirrors DroidFS's ExplorerElement.new exactly so the JNI glue ports with
        // zero argument changes: (Ljava/lang/String;IJJLjava/lang/String;)L…/GocryptfsEntry;.
        // The glue passes the bare entry name, mode (type bits), size + mTime (seconds, NOT
        // *1000), and the listed directory as parentPath.
        @JvmStatic
        fun new(name: String, mode: Int, size: Long, mTime: Long, parentPath: String): GocryptfsEntry =
            GocryptfsEntry(name, mode, size, mTime, parentPath)
    }
}
