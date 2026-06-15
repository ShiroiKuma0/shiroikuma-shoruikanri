/*
 * 白い熊 fork: gocryptfs provider — JNI wrapper for one unlocked volume.
 *
 * Adapted from DroidFS's GocryptfsVolume.kt (GPLv3) — same `native_*` surface, trimmed of
 * DroidFS-specific helpers (EncryptedVolume base, ExplorerElement, InitResult, ObjRef, R).
 * A live volume is keyed by an opaque `sessionId` returned from nativeInit. In-volume paths
 * are ABSOLUTE ("/sub/file"); the volume root is "/".
 *
 * BINARY CONTRACT with libgocryptfs_jni.so (built in the shiroikuma-gcfs repo):
 *   - This class's FQN drives the JNI symbol names
 *     (Java_me_zhanghai_android_files_provider_gocryptfs_client_GocryptfsVolume_native_*).
 *   - native_get_attr returns a [GocryptfsStat]; native_list_dir returns a list whose
 *     elements the glue builds via GocryptfsEntry.new(...). Those two FQNs/signatures are
 *     also part of the contract.
 *   - The library name is "gocryptfs_jni" (libgocryptfs_jni.so), arm64-v8a.
 * Keep this surface and the gcfs-side glue in lock-step.
 */

package me.zhanghai.android.files.provider.gocryptfs.client

class GocryptfsVolume private constructor(val sessionId: Int) {
    private external fun native_close(sessionID: Int)
    private external fun native_is_closed(sessionID: Int): Boolean
    private external fun native_list_dir(sessionID: Int, dirPath: String): MutableList<GocryptfsEntry>?
    private external fun native_open_read_mode(sessionID: Int, filePath: String): Int
    private external fun native_open_write_mode(sessionID: Int, filePath: String, mode: Int): Int
    private external fun native_read_file(
        sessionID: Int, handleID: Int, fileOffset: Long, buff: ByteArray, dstOffset: Long, length: Int
    ): Int
    private external fun native_write_file(
        sessionID: Int, handleID: Int, fileOffset: Long, buff: ByteArray, srcOffset: Long, length: Int
    ): Int
    private external fun native_truncate(sessionID: Int, path: String, offset: Long): Boolean
    private external fun native_close_file(sessionID: Int, handleID: Int)
    private external fun native_remove_file(sessionID: Int, filePath: String): Boolean
    private external fun native_mkdir(sessionID: Int, dirPath: String, mode: Int): Boolean
    private external fun native_rmdir(sessionID: Int, dirPath: String): Boolean
    private external fun native_get_attr(sessionID: Int, filePath: String): GocryptfsStat?
    private external fun native_rename(sessionID: Int, oldPath: String, newPath: String): Boolean

    fun listDir(path: String): MutableList<GocryptfsEntry>? = native_list_dir(sessionId, path)

    fun getAttr(path: String): GocryptfsStat? = native_get_attr(sessionId, path)

    fun pathExists(path: String): Boolean = getAttr(path) != null

    fun openReadMode(path: String): Int = native_open_read_mode(sessionId, path)

    fun openWriteMode(path: String, mode: Int = DEFAULT_FILE_MODE): Int =
        native_open_write_mode(sessionId, path, mode)

    fun readFile(handle: Int, fileOffset: Long, buffer: ByteArray, dstOffset: Long, length: Int): Int =
        native_read_file(sessionId, handle, fileOffset, buffer, dstOffset, length)

    fun writeFile(handle: Int, fileOffset: Long, buffer: ByteArray, srcOffset: Long, length: Int): Int =
        native_write_file(sessionId, handle, fileOffset, buffer, srcOffset, length)

    fun truncate(path: String, size: Long): Boolean = native_truncate(sessionId, path, size)

    fun closeFile(handle: Int) = native_close_file(sessionId, handle)

    fun removeFile(path: String): Boolean = native_remove_file(sessionId, path)

    fun mkdir(path: String, mode: Int = DEFAULT_DIR_MODE): Boolean = native_mkdir(sessionId, path, mode)

    fun rmdir(path: String): Boolean = native_rmdir(sessionId, path)

    fun rename(oldPath: String, newPath: String): Boolean = native_rename(sessionId, oldPath, newPath)

    fun isClosed(): Boolean = native_is_closed(sessionId)

    fun close() = native_close(sessionId)

    companion object {
        const val CONFIG_FILE_NAME = "gocryptfs.conf"
        const val KEY_LEN = 32
        const val DEFAULT_FILE_MODE = 384 // 0600
        const val DEFAULT_DIR_MODE = 448 // 0700
        const val MAX_KERNEL_WRITE = 128 * 1024

        // Returns sessionId >= 0 on success, or a negative error code:
        //   -1 = config load error (not a gocryptfs volume / unreadable conf)
        //   -2 = wrong password
        private external fun nativeInit(
            rootCipherDir: String, password: ByteArray?, givenHash: ByteArray?, returnedHash: ByteArray?
        ): Int

        private external fun nativeCreateVolume(
            rootCipherDir: String, password: ByteArray, plainTextNames: Boolean, xchacha: Int,
            logN: Int, creator: String, returnedHash: ByteArray?
        ): Int

        /** Unlock an existing volume; returns null on failure (wrong password / not a volume). */
        fun init(
            rootCipherDir: String,
            password: ByteArray?,
            givenHash: ByteArray? = null,
            returnedHash: ByteArray? = null
        ): GocryptfsVolume? {
            val sessionId = nativeInit(rootCipherDir, password, givenHash, returnedHash)
            return if (sessionId < 0) null else GocryptfsVolume(sessionId)
        }

        init {
            // libgocryptfs_jni.so depends on the engine libgocryptfs.so (DT_NEEDED). Load the
            // engine first so older linkers resolve it; ignore if the engine was instead
            // statically linked into the JNI lib (single-.so build).
            try {
                System.loadLibrary("gocryptfs")
            } catch (e: UnsatisfiedLinkError) {
                // Engine statically linked into libgocryptfs_jni.so — nothing to do.
            }
            System.loadLibrary("gocryptfs_jni")
        }
    }
}
