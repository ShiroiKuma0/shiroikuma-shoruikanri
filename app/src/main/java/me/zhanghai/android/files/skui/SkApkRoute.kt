/*
 * 白い熊 fork (skui): what a tap on a package file (APK / XAPK / APKS / APKM) does.
 *
 * SCAN (the default) hands the file to 応用管理's VirusTotal report screen by explicit
 * component, whose Install pill then passes it on to universal-installer. INSTALL is the
 * behaviour from before (a remembered open-with default, else the upstream install/view
 * setting). ASK offers both. The scan request follows the contract shared by the three apps
 * of the APK-vetting chain (~/.claude/plans/apk-vetting-*.md) — do not rename any of its
 * names here alone. If the scanner's component does not resolve (not installed, an older
 * build, or its VirusTotal component switched off), every caller falls back to INSTALL.
 */

package me.zhanghai.android.files.skui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.StringRes
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isApkBundle
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.util.extraPath

object SkApkRoute {
    // --- The shared contract (identical in all three sub-plans) ---
    const val SCANNER_PACKAGE = "shiroikuma.oyokanri"
    const val SCANNER_ACTIVITY = "io.github.muntashirakon.AppManager.scanner.vt.VtReportActivity"
    const val ACTION_VT_SCAN = "shiroikuma.oyokanri.action.VT_SCAN"
    const val EXTRA_REAL_PATH = "shiroikuma.extra.REAL_PATH"

    enum class Mode(val id: String, @StringRes val labelRes: Int) {
        SCAN("scan", R.string.sk_apk_route_scan),
        INSTALL("install", R.string.sk_apk_route_install),
        ASK("ask", R.string.sk_apk_route_ask);

        companion object {
            fun byId(id: String?): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    // SkEximport carries this store whole (Cat.APK_ROUTE).
    const val PREFS_NAME = "sk_apk_route"
    private const val KEY_MODE = "mode"

    private val prefs: SharedPreferences by lazy {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var mode: Mode
        get() = Mode.byId(prefs.getString(KEY_MODE, null)) ?: Mode.SCAN
        set(value) {
            prefs.edit().putString(KEY_MODE, value.id).apply()
        }

    /** A file this routing applies to: a plain APK or a ZIP of APKs (XAPK / APKS / APKM). */
    fun isPackageFile(mimeType: MimeType): Boolean = mimeType.isApk || mimeType.isApkBundle

    private val scannerComponent = ComponentName(SCANNER_PACKAGE, SCANNER_ACTIVITY)

    /**
     * The scan request for [path], or null when the scanner's component does not resolve — the
     * caller then behaves as if this routing did not exist.
     *
     * Data is our FileProvider URI with the file's own media type (the explicit component skips
     * filter matching, so our made-up bundle types need no any-type alias here). Local files also
     * carry their real path, which the scanner prefers when it can read it; archive entries and
     * remote files go as the URI only, which the scanner copies while our proxy is alive.
     */
    fun createScanIntent(context: Context, path: Path, mimeType: MimeType): Intent? {
        val intent = Intent(ACTION_VT_SCAN)
            .setDataAndType(path.fileProviderUri, mimeType.value)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setComponent(scannerComponent)
            .apply {
                extraPath = path
                if (path.isLinuxPath) {
                    putExtra(EXTRA_REAL_PATH, path.toFile().path)
                }
            }
        return intent.takeIf { isScannerAvailable(context) }
    }

    /** Whether the scanner's entry activity is installed, enabled and exported to us. */
    fun isScannerAvailable(context: Context): Boolean =
        try {
            val info = context.packageManager.getActivityInfo(scannerComponent, 0)
            info.enabled && info.applicationInfo.enabled && info.exported
        } catch (e: Exception) {
            false
        }
}
