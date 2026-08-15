/*
 * 白い熊 fork: cover thumbnails for EPUB books. An EPUB is a ZIP, so the cover is pulled out of
 * the book the same way the XAPK icon is, walking the three steps the format prescribes:
 * "META-INF/container.xml" names the OPF package document, the OPF manifest names the cover
 * image, and that entry is then read and handed to Coil. Each step needs the name found by the
 * previous one, so this is three single-entry passes over the archive rather than one.
 */

package me.zhanghai.android.files.coil

import android.net.Uri
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import java8.nio.file.Path
import me.zhanghai.android.files.provider.archive.archiver.ArchiveReader
import okio.buffer
import okio.source
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class EpubCoverFetcher(
    private val path: Path,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val coverBytes = readCoverBytes() ?: return null
        return SourceResult(
            ImageSource(coverBytes.inputStream().source().buffer(), options.context), null,
            path.dataSource
        )
    }

    private fun readCoverBytes(): ByteArray? {
        val containerBytes = readEntryBytes(CONTAINER_ENTRY_NAME, MAX_XML_SIZE) ?: return null
        val packageEntryName = parseContainer(containerBytes) ?: return null
        val packageBytes = readEntryBytes(packageEntryName, MAX_XML_SIZE) ?: return null
        val coverHref = parseCoverHref(packageBytes) ?: return null
        val coverEntryName = resolveEntryName(packageEntryName, coverHref) ?: return null
        return readEntryBytes(coverEntryName, MAX_COVER_SIZE)
    }

    private fun readEntryBytes(entryName: String, maxSize: Int): ByteArray? =
        ArchiveReader.readEntryBytes(path, emptyList(), maxSize) {
            it.normalizedEntryName.equals(entryName, true)
        }

    /** @return the entry name of the OPF package document, or null if there is none. */
    private fun parseContainer(bytes: ByteArray): String? {
        val parser = newPullParser(bytes)
        var fullPath: String? = null
        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_DOCUMENT) {
                break
            }
            if (eventType != XmlPullParser.START_TAG || !parser.name.equals(ROOT_FILE_TAG, true)) {
                continue
            }
            val path = parser.getAttributeValue(null, FULL_PATH_ATTRIBUTE)
            if (path.isNullOrEmpty()) {
                continue
            }
            // There can be several root files; the package document is the one we want, but take
            // the first one as a fallback in case no media type says so.
            val isPackageDocument =
                parser.getAttributeValue(null, MEDIA_TYPE_ATTRIBUTE) == PACKAGE_MEDIA_TYPE
            if (isPackageDocument || fullPath == null) {
                fullPath = path
            }
            if (isPackageDocument) {
                break
            }
        }
        return fullPath?.let { Uri.decode(it).normalizedEntryName }
    }

    /** @return the cover image href, relative to the package document. */
    private fun parseCoverHref(bytes: ByteArray): String? {
        val parser = newPullParser(bytes)
        val items = mutableListOf<ManifestItem>()
        var coverId: String? = null
        while (true) {
            val eventType = parser.next()
            if (eventType == XmlPullParser.END_DOCUMENT) {
                break
            }
            if (eventType != XmlPullParser.START_TAG) {
                continue
            }
            when {
                parser.name.equals(ITEM_TAG, true) -> {
                    val href = parser.getAttributeValue(null, HREF_ATTRIBUTE)
                    if (!href.isNullOrEmpty()) {
                        items += ManifestItem(
                            parser.getAttributeValue(null, ID_ATTRIBUTE), href,
                            parser.getAttributeValue(null, MEDIA_TYPE_ATTRIBUTE),
                            parser.getAttributeValue(null, PROPERTIES_ATTRIBUTE)
                        )
                    }
                }
                // EPUB 2 points at the cover from the metadata instead of marking the item.
                parser.name.equals(META_TAG, true) -> {
                    if (parser.getAttributeValue(null, NAME_ATTRIBUTE).equals(COVER_META_NAME, true)
                    ) {
                        coverId = parser.getAttributeValue(null, CONTENT_ATTRIBUTE)
                    }
                }
            }
        }
        val coverItem = items.find { it.isImage && it.hasCoverImageProperty }
            ?: items.find { it.isImage && it.id != null && it.id == coverId }
            // Neither convention was followed, so settle for an image that calls itself a cover.
            ?: items.find { it.isImage && it.isNamedCover }
        return coverItem?.href
    }

    /** @return the entry name a package document relative [href] resolves to. */
    private fun resolveEntryName(packageEntryName: String, href: String): String? {
        if (href.contains("://")) {
            // A remote cover isn't ours to fetch.
            return null
        }
        val decodedHref = Uri.decode(href.substringBefore('#').substringBefore('?'))
        val segments = mutableListOf<String>()
        if (!decodedHref.startsWith('/')) {
            segments += packageEntryName.split('/').dropLast(1)
        }
        for (segment in decodedHref.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex) else
                    return null
                else -> segments += segment
            }
        }
        return segments.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    // Namespace aware so that a prefixed document (<opf:manifest>) still yields plain tag names,
    // and without document declaration processing, since the file is untrusted input.
    private fun newPullParser(bytes: ByteArray): XmlPullParser =
        XmlPullParserFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newPullParser()
            .apply { setInput(bytes.inputStream(), null) }

    private class ManifestItem(
        val id: String?,
        val href: String,
        val mediaType: String?,
        val properties: String?
    ) {
        val isImage: Boolean
            get() = mediaType?.startsWith("image/", true)
                ?: (href.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS)

        val hasCoverImageProperty: Boolean
            get() = properties?.split(WHITESPACE_REGEX)
                ?.any { it.equals(COVER_IMAGE_PROPERTY, true) } == true

        val isNamedCover: Boolean
            get() = id?.contains(COVER_META_NAME, true) == true
                || href.substringAfterLast('/').contains(COVER_META_NAME, true)
    }

    companion object {
        private const val CONTAINER_ENTRY_NAME = "META-INF/container.xml"
        private const val PACKAGE_MEDIA_TYPE = "application/oebps-package+xml"
        private const val ROOT_FILE_TAG = "rootfile"
        private const val ITEM_TAG = "item"
        private const val META_TAG = "meta"
        private const val FULL_PATH_ATTRIBUTE = "full-path"
        private const val MEDIA_TYPE_ATTRIBUTE = "media-type"
        private const val HREF_ATTRIBUTE = "href"
        private const val ID_ATTRIBUTE = "id"
        private const val PROPERTIES_ATTRIBUTE = "properties"
        private const val NAME_ATTRIBUTE = "name"
        private const val CONTENT_ATTRIBUTE = "content"
        private const val COVER_META_NAME = "cover"
        private const val COVER_IMAGE_PROPERTY = "cover-image"

        private val WHITESPACE_REGEX = Regex("\\s+")

        private val IMAGE_EXTENSIONS =
            setOf("avif", "bmp", "gif", "jpe", "jpeg", "jpg", "png", "svg", "webp")

        // The manifest of a large book can get long, but never this long.
        private const val MAX_XML_SIZE = 4 * 1024 * 1024

        // A cover is a single page image; anything bigger than this isn't one, and we don't want
        // to pull a big entry into memory for a list thumbnail.
        private const val MAX_COVER_SIZE = 8 * 1024 * 1024

        private val String.normalizedEntryName: String
            get() = removePrefix("./").removePrefix("/")
    }
}
