/*
 * 白い熊 fork: gocryptfs provider — a mounted (unlocked) volume as a file system.
 *
 * Modeled on ArchiveFileSystem (keyed by the backing cipher-dir Path) but read/write: operations
 * delegate to the live GocryptfsVolume (sessionId) held by the provider, looked up by cipherDir —
 * so a file system reconstructed from a URI/parcel stays valid while the volume is unlocked.
 */

package me.zhanghai.android.files.provider.gocryptfs

import android.os.Parcel
import android.os.Parcelable
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.NoSuchFileException
import java8.nio.file.NotDirectoryException
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchService
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringBuilder
import me.zhanghai.android.files.provider.common.ByteStringListPathCreator
import me.zhanghai.android.files.provider.common.IsDirectoryException
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.NotifyEntryModifiedSeekableByteChannel
import me.zhanghai.android.files.provider.common.OpenOptions
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.provider.gocryptfs.client.GocryptfsStat
import me.zhanghai.android.files.provider.gocryptfs.client.GocryptfsVolume
import me.zhanghai.android.files.util.readParcelable
import java.io.IOException

internal class GocryptfsFileSystem(
    private val provider: GocryptfsFileSystemProvider,
    val cipherDir: Path
) : FileSystem(), ByteStringListPathCreator, Parcelable {
    val rootDirectory = GocryptfsPath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute) {
            throw AssertionError("Root directory $rootDirectory must be absolute")
        }
        if (rootDirectory.nameCount != 0) {
            throw AssertionError("Root directory $rootDirectory must contain no names")
        }
    }

    val defaultDirectory: GocryptfsPath
        get() = rootDirectory

    private val lock = Any()

    private var isOpen = true

    private val volume: GocryptfsVolume
        @Throws(IOException::class)
        get() = provider.getVolume(cipherDir)

    @Throws(IOException::class)
    fun listDirectory(directory: GocryptfsPath): List<Path> {
        val entries = volume.listDir(directory.toString())
            ?: throw NotDirectoryException(directory.toString())
        return entries.map { directory.resolve(it.name) }
    }

    @Throws(IOException::class)
    fun getStat(path: GocryptfsPath): GocryptfsStat =
        volume.getAttr(path.toString()) ?: throw NoSuchFileException(path.toString())

    @Throws(IOException::class)
    fun newByteChannel(file: GocryptfsPath, openOptions: OpenOptions): SeekableByteChannel {
        val volume = volume
        val pathString = file.toString()
        if (!openOptions.write) {
            if (volume.getAttr(pathString) == null) {
                throw NoSuchFileException(pathString)
            }
            val handle = volume.openReadMode(pathString)
            if (handle < 0) {
                throw IOException("native_open_read_mode failed for $pathString")
            }
            return GocryptfsFileByteChannel(volume, handle, pathString, false)
        }
        val existing = volume.getAttr(pathString)
        if (existing != null && existing.isDirectory) {
            throw IsDirectoryException(pathString)
        }
        if (openOptions.createNew && existing != null) {
            throw FileAlreadyExistsException(pathString)
        }
        if (!openOptions.create && !openOptions.createNew && existing == null) {
            throw NoSuchFileException(pathString)
        }
        val handle = volume.openWriteMode(pathString)
        if (handle < 0) {
            throw IOException("native_open_write_mode failed for $pathString")
        }
        if (openOptions.truncateExisting && existing != null) {
            if (!volume.truncate(pathString, 0L)) {
                volume.closeFile(handle)
                throw IOException("native_truncate failed for $pathString")
            }
        }
        if (existing == null) {
            LocalWatchService.onEntryCreated(file)
        }
        // Notify watchers (and thereby auto-refresh the list) on write/truncate/close.
        return NotifyEntryModifiedSeekableByteChannel(
            GocryptfsFileByteChannel(volume, handle, pathString, openOptions.append), file
        )
    }

    @Throws(IOException::class)
    fun createDirectory(directory: GocryptfsPath) {
        val volume = volume
        val pathString = directory.toString()
        if (volume.getAttr(pathString) != null) {
            throw FileAlreadyExistsException(pathString)
        }
        if (!volume.mkdir(pathString)) {
            throw IOException("native_mkdir failed for $pathString")
        }
        LocalWatchService.onEntryCreated(directory)
    }

    @Throws(IOException::class)
    fun delete(path: GocryptfsPath) {
        val volume = volume
        val pathString = path.toString()
        val stat = volume.getAttr(pathString) ?: throw NoSuchFileException(pathString)
        val deleted = if (stat.isDirectory) {
            volume.rmdir(pathString)
        } else {
            volume.removeFile(pathString)
        }
        if (!deleted) {
            throw IOException("delete failed for $pathString")
        }
        LocalWatchService.onEntryDeleted(path)
    }

    @Throws(IOException::class)
    fun rename(source: GocryptfsPath, target: GocryptfsPath): Boolean =
        volume.rename(source.toString(), target.toString())

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) {
                return
            }
            provider.removeFileSystem(this)
            isOpen = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> =
        GocryptfsFileAttributeView.SUPPORTED_NAMES

    override fun getPath(first: String, vararg more: String): GocryptfsPath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return GocryptfsPath(this, path)
    }

    override fun getPath(first: ByteString, vararg more: ByteString): GocryptfsPath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return GocryptfsPath(this, path)
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw UnsupportedOperationException()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun newWatchService(): WatchService = LocalWatchService()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as GocryptfsFileSystem
        return cipherDir == other.cipherDir
    }

    override fun hashCode(): Int = cipherDir.hashCode()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(cipherDir as Parcelable, flags)
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()
        private val SEPARATOR_BYTE_STRING = SEPARATOR.toByteString()
        private const val SEPARATOR_STRING = "/"

        @JvmField
        val CREATOR = object : Parcelable.Creator<GocryptfsFileSystem> {
            override fun createFromParcel(source: Parcel): GocryptfsFileSystem {
                val cipherDir = source.readParcelable<Parcelable>(Path::class.java.classLoader)
                    as Path
                return GocryptfsFileSystemProvider.getOrNewFileSystem(cipherDir)
            }

            override fun newArray(size: Int): Array<GocryptfsFileSystem?> = arrayOfNulls(size)
        }
    }
}
