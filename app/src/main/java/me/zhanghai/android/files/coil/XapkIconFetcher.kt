/*
 * 白い熊 fork: icon thumbnails for XAPK bundles. An XAPK is a ZIP bundling a base APK, split
 * APKs and OBB files, so PackageManager cannot parse it and the AppIconFetcher path used for
 * plain APKs does not apply. The bundle carries the app icon as "icon.png" next to
 * "manifest.json" at its root, so read that single entry out of the archive instead.
 */

package me.zhanghai.android.files.coil

import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import java8.nio.file.Path
import me.zhanghai.android.files.provider.archive.archiver.ArchiveReader
import okio.buffer
import okio.source

class XapkIconFetcher(
    private val path: Path,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val iconBytes = ArchiveReader.readEntryBytes(path, emptyList(), MAX_ICON_SIZE) {
            it.equals(ICON_ENTRY_NAME, true)
        } ?: return null
        return SourceResult(
            ImageSource(iconBytes.inputStream().source().buffer(), options.context), null,
            path.dataSource
        )
    }

    companion object {
        private const val ICON_ENTRY_NAME = "icon.png"

        // It's a launcher icon; anything larger than this isn't one, and we don't want to pull
        // a big entry into memory for a list thumbnail.
        private const val MAX_ICON_SIZE = 4 * 1024 * 1024
    }
}
