/*
 * 白い熊 fork: gocryptfs provider — in-volume path.
 *
 * Modeled on ArchivePath: a path inside a file-backed read-only file system. The URI encodes
 * the backing cipher dir's URI in the path (with a leading slash, since the authority is empty)
 * and the in-volume path in the query, mirroring the archive provider.
 */

package me.zhanghai.android.files.provider.gocryptfs

import android.os.Parcel
import android.os.Parcelable
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.ByteStringListPath
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.toByteString
import me.zhanghai.android.files.util.readParcelable
import java.io.File
import java.io.IOException

internal class GocryptfsPath : ByteStringListPath<GocryptfsPath> {
    private val fileSystem: GocryptfsFileSystem

    constructor(fileSystem: GocryptfsFileSystem, path: ByteString) : super(
        GocryptfsFileSystem.SEPARATOR, path
    ) {
        this.fileSystem = fileSystem
    }

    private constructor(
        fileSystem: GocryptfsFileSystem,
        absolute: Boolean,
        segments: List<ByteString>
    ) : super(GocryptfsFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    override fun isPathAbsolute(path: ByteString): Boolean =
        !path.isEmpty() && path[0] == GocryptfsFileSystem.SEPARATOR

    override fun createPath(path: ByteString): GocryptfsPath = GocryptfsPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): GocryptfsPath =
        GocryptfsPath(fileSystem, absolute, segments)

    override val uriPath: ByteString
        // Prepend a slash to make a valid URI path, since we always have an (empty) authority.
        get() = ("/" + fileSystem.cipherDir.toUri().toString()).toByteString()

    override val uriQuery: ByteString?
        get() = super.uriPath

    override val defaultDirectory: GocryptfsPath
        get() = fileSystem.defaultDirectory

    override fun getFileSystem(): GocryptfsFileSystem = fileSystem

    override fun getRoot(): GocryptfsPath? = if (isAbsolute) fileSystem.rootDirectory else null

    @Throws(IOException::class)
    override fun toRealPath(vararg options: LinkOption): GocryptfsPath {
        throw UnsupportedOperationException()
    }

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun register(
        watcher: WatchService,
        events: Array<WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey {
        if (watcher !is LocalWatchService) {
            throw ProviderMismatchException(watcher.toString())
        }
        return watcher.register(this, events, *modifiers)
    }

    private constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable()!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<GocryptfsPath> {
            override fun createFromParcel(source: Parcel): GocryptfsPath = GocryptfsPath(source)

            override fun newArray(size: Int): Array<GocryptfsPath?> = arrayOfNulls(size)
        }
    }
}

val Path.isGocryptfsPath: Boolean
    get() = this is GocryptfsPath
