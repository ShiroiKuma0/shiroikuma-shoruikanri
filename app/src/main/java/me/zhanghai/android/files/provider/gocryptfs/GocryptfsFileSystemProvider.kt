/*
 * 白い熊 fork: gocryptfs provider — read/write nio2 provider over libgocryptfs.
 *
 * Modeled on ArchiveFileSystemProvider (file-backed) but read/write (Phase 3). A volume is
 * identified by its cipher dir (a real Path) and must first be unlocked via [openFileSystem]
 * (password -> nativeInit -> sessionId), which registers a live GocryptfsVolume keyed by the
 * cipher dir. Provider/file-system operations then look that volume up; if it isn't unlocked
 * (e.g. after process death) they fail with an IOException.
 */

package me.zhanghai.android.files.provider.gocryptfs

import java8.nio.channels.FileChannel
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.ProviderMismatchException
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.FileSystemCache
import me.zhanghai.android.files.provider.common.PathListDirectoryStream
import me.zhanghai.android.files.provider.common.PathObservable
import me.zhanghai.android.files.provider.common.PathObservableProvider
import me.zhanghai.android.files.provider.common.Searchable
import me.zhanghai.android.files.provider.common.WalkFileTreeSearchable
import me.zhanghai.android.files.provider.common.WatchServicePathObservable
import me.zhanghai.android.files.provider.common.decodedPathByteString
import me.zhanghai.android.files.provider.common.decodedQueryByteString
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.common.toCopyOptions
import me.zhanghai.android.files.provider.common.toOpenOptions
import me.zhanghai.android.files.provider.gocryptfs.client.GocryptfsVolume
import java.io.IOException
import java.net.URI

object GocryptfsFileSystemProvider : FileSystemProvider(), PathObservableProvider, Searchable {
    private const val SCHEME = "gocryptfs"

    private val HIDDEN_FILE_NAME_PREFIX = ".".toByteString()

    private val fileSystems = FileSystemCache<Path, GocryptfsFileSystem>()

    private val volumes = mutableMapOf<Path, GocryptfsVolume>()

    private val lock = Any()

    override fun getScheme(): String = SCHEME

    /**
     * Unlock the gocryptfs volume at [cipherDir] (a real POSIX path) with [password], registering
     * its live session and returning the mounted file system. Returns null on failure (wrong
     * password, or not a gocryptfs volume).
     */
    internal fun openFileSystem(cipherDir: Path, password: ByteArray): GocryptfsFileSystem? {
        val volume = GocryptfsVolume.init(cipherDir.toString(), password) ?: return null
        synchronized(lock) {
            volumes.put(cipherDir, volume)?.let { previous ->
                try {
                    previous.close()
                } catch (e: Throwable) {
                    // Ignore.
                }
            }
        }
        return getOrNewFileSystem(cipherDir)
    }

    fun isFileSystemOpen(cipherDir: Path): Boolean =
        synchronized(lock) { volumes.containsKey(cipherDir) }

    fun closeFileSystem(cipherDir: Path) {
        val volume = synchronized(lock) { volumes.remove(cipherDir) }
        if (volume != null) {
            try {
                volume.close()
            } catch (e: Throwable) {
                // Ignore.
            }
        }
    }

    @Throws(IOException::class)
    internal fun getVolume(cipherDir: Path): GocryptfsVolume =
        synchronized(lock) { volumes[cipherDir] }
            ?: throw IOException("gocryptfs volume is locked: $cipherDir")

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val cipherDir = uri.cipherDir
        return fileSystems.create(cipherDir) { GocryptfsFileSystem(this, cipherDir) }
    }

    internal fun getOrNewFileSystem(cipherDir: Path): GocryptfsFileSystem =
        fileSystems.getOrCreate(cipherDir) { GocryptfsFileSystem(this, cipherDir) }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val cipherDir = uri.cipherDir
        return fileSystems[cipherDir]
    }

    internal fun removeFileSystem(fileSystem: GocryptfsFileSystem) {
        fileSystems.remove(fileSystem.cipherDir, fileSystem)
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val cipherDir = uri.cipherDir
        val path = uri.decodedQueryByteString
            ?: throw IllegalArgumentException("URI must have a query")
        return getOrNewFileSystem(cipherDir).getPath(path)
    }

    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme == SCHEME) { "URI scheme $scheme must be $SCHEME" }
    }

    private val URI.cipherDir: Path
        get() {
            val path = decodedPathByteString
                ?: throw IllegalArgumentException("URI must have a path")
            // Drop the leading slash, then resolve the embedded backing cipher-dir URI.
            val cipherUri = URI.create(path.toString().drop(1))
            return Paths.get(cipherUri)
        }

    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): SeekableByteChannel {
        file as? GocryptfsPath ?: throw ProviderMismatchException(file.toString())
        val openOptions = options.toOpenOptions()
        return file.fileSystem.newByteChannel(file, openOptions)
    }

    override fun newFileChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>
    ): FileChannel {
        file as? GocryptfsPath ?: throw ProviderMismatchException(file.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>
    ): DirectoryStream<Path> {
        directory as? GocryptfsPath ?: throw ProviderMismatchException(directory.toString())
        val children = directory.fileSystem.listDirectory(directory)
        return PathListDirectoryStream(children, filter)
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? GocryptfsPath ?: throw ProviderMismatchException(directory.toString())
        directory.fileSystem.createDirectory(directory)
    }

    @Throws(IOException::class)
    override fun createSymbolicLink(link: Path, target: Path, vararg attributes: FileAttribute<*>) {
        link as? GocryptfsPath ?: throw ProviderMismatchException(link.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun createLink(link: Path, existing: Path) {
        link as? GocryptfsPath ?: throw ProviderMismatchException(link.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        path.fileSystem.delete(path)
    }

    @Throws(IOException::class)
    override fun readSymbolicLink(link: Path): Path {
        link as? GocryptfsPath ?: throw ProviderMismatchException(link.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        source as? GocryptfsPath ?: throw ProviderMismatchException(source.toString())
        target as? GocryptfsPath ?: throw ProviderMismatchException(target.toString())
        GocryptfsCopyMove.copy(source, target, options.toCopyOptions())
    }

    @Throws(IOException::class)
    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        source as? GocryptfsPath ?: throw ProviderMismatchException(source.toString())
        target as? GocryptfsPath ?: throw ProviderMismatchException(target.toString())
        GocryptfsCopyMove.move(source, target, options.toCopyOptions())
    }

    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        return path == path2
    }

    override fun isHidden(path: Path): Boolean {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        val fileName = path.fileNameByteString ?: return false
        return fileName.startsWith(HIDDEN_FILE_NAME_PREFIX)
    }

    override fun getFileStore(path: Path): FileStore {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        // Existence check (throws NoSuchFileException if absent); read/write are always allowed.
        path.fileSystem.getStat(path)
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption
    ): V? {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        if (!supportsFileAttributeView(type)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path) as V
    }

    internal fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean =
        type.isAssignableFrom(GocryptfsFileAttributeView::class.java)

    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        if (!type.isAssignableFrom(GocryptfsFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return getFileAttributeView(path).readAttributes() as A
    }

    private fun getFileAttributeView(path: GocryptfsPath): GocryptfsFileAttributeView =
        GocryptfsFileAttributeView(path)

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): Map<String, Any> {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption
    ) {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun observe(path: Path, intervalMillis: Long): PathObservable {
        path as? GocryptfsPath ?: throw ProviderMismatchException(path.toString())
        return WatchServicePathObservable(path, intervalMillis)
    }

    @Throws(IOException::class)
    override fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        directory as? GocryptfsPath ?: throw ProviderMismatchException(directory.toString())
        WalkFileTreeSearchable.search(directory, query, intervalMillis, listener)
    }
}
