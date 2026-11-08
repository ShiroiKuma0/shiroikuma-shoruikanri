/*
 * 白い熊 fork: icon thumbnails for APKS bundles. An APKS (bundletool / SAI) is a ZIP of
 * base.apk plus split APKs, with no icon.png beside them as an XAPK has, so PackageManager has
 * to read the icon out of base.apk - the launcher mipmaps stay in the base and are not moved
 * into a density split. PackageManager needs a real file, so base.apk is streamed into a
 * temporary file in the cache, and that file is deleted again once the icon is loaded.
 */

package me.zhanghai.android.files.coil

import android.content.Context
import android.content.pm.ApplicationInfo
import java8.nio.file.Path
import me.zhanghai.android.files.compat.getPackageArchiveInfoCompat
import me.zhanghai.android.files.provider.archive.archiver.ArchiveReader
import java.io.Closeable
import java.io.File
import java.io.IOException

class ApksIconFetcherFactory(
    iconSize: Int,
    private val context: Context
) : AppIconFetcher.Factory<Path>(iconSize, context) {
    override fun getApplicationInfo(data: Path): Pair<ApplicationInfo, Closeable?> {
        val baseApkFile = File.createTempFile("sk_apks_base_", ".apk", context.cacheDir)
        var successful = false
        try {
            val found = ArchiveReader.copyEntryTo(data, emptyList(), baseApkFile) {
                it.equals(BASE_APK_ENTRY_NAME, true)
            }
            if (!found) {
                throw IOException("No $BASE_APK_ENTRY_NAME in $data")
            }
            val baseApkPath = baseApkFile.path
            val applicationInfo = context.packageManager
                .getPackageArchiveInfoCompat(baseApkPath, 0)?.applicationInfo
                ?: throw IOException("ApplicationInfo is null")
            applicationInfo.sourceDir = baseApkPath
            applicationInfo.publicSourceDir = baseApkPath
            successful = true
            return applicationInfo to Closeable { baseApkFile.delete() }
        } finally {
            if (!successful) {
                baseApkFile.delete()
            }
        }
    }

    companion object {
        private const val BASE_APK_ENTRY_NAME = "base.apk"
    }
}
