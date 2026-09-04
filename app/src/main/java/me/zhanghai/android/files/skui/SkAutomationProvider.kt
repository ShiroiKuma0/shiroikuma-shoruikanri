/*
 * 白い熊 fork (skui): the automation data door — export this app's own state,
 * and put it back, for a caller we can identify. Contract v2 §2a; modelled on
 * 自由作業盤's core/automation/AutomationProvider.kt over this fork's own
 * category ZIP (SkEximport).
 *
 * It sits *alongside* SkStateExportReceiver, it does not replace it.
 */

package me.zhanghai.android.files.skui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import me.zhanghai.android.files.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared
 * secret, which cannot survive the wipe that this feature exists to recover from. A provider gets
 * the caller's identity from the framework for free — see [SkAutomationCallers] for what is
 * actually checked and why a package-name prefix would have been worse than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a file descriptor the caller
 * opened, and the terminal answer comes back on the broadcast the family already proved on EMUI.
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A
 * file this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing
 * (応用管理, 2026-09-04). A descriptor is also a capability that **expires when it is closed**.
 *
 * That matters more here than in most sister apps: this one is a **file manager**, so it holds
 * `MANAGE_EXTERNAL_STORAGE` for its own purpose and is the easiest place in the family to keep the
 * old path-based habit by accident. It does not. Nothing below resolves a path, creates a
 * directory, or writes anywhere except into the descriptor the caller handed us.
 *
 * ## Initialization order
 *
 * This app builds its entire global state in `AppProvider.onCreate()`, so this provider is declared
 * **after** `AppProvider` in the manifest. Android installs providers in manifest order and
 * publishes them only once every one of them has been created, so by the time any `call()` can
 * arrive here `application` and the app initializers are up.
 */
class SkAutomationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary
     * the broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = SkAutomationCallers.verify(context, callingPackage)) {
            is SkAutomationCallers.Verdict.Refused -> return fail(verdict.why)
            SkAutomationCallers.Verdict.Allowed -> Unit
        }
        // Then the app's own switches — a token is ignored unless this app asks for one.
        SkAutomation.refuse(extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(context))
            METHOD_EXPORT -> start(context, extras, importing = false)
            METHOD_IMPORT -> start(context, extras, importing = true)
            METHOD_CANCEL -> {
                SkAutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }
            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw
     * a row before an export exists, and at restore must judge compatibility **before** streaming
     * tens of megabytes into an app that would reject them — which it cannot do if the header is
     * buried inside an encrypted archive (応用管理, 2026-09-04).
     */
    private fun describe(context: Context): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = packageInfo.versionCode
        val header = JSONObject()
            .put("app_id", context.packageName)
            .put("version_code", versionCode)
            .put("version_name", packageInfo.versionName.orEmpty())
            .put("format", SkEximport.VERSION)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            // Every category merges per key into SharedPreferences, so an import lands correctly on
            // a package that has never been launched. Nothing here writes first-run defaults that a
            // restore would then have to fight.
            .put("requires_launch_first", false)
            .put(
                "contains",
                JSONArray(SkEximport.Cat.entries.map { context.getString(it.labelRes) })
            )
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed when `call()` returns; a service reading it afterwards
     * would find it shut. That is a bug you only see under load, so it is not left to the service
     * to remember.
     */
    private fun start(context: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION")
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val duplicate = runCatching { fd.dup() }.getOrNull()
            ?: return fail("ERROR:descriptor unusable")
        val jobId = SkAutomationJobs.begin()
        return runCatching {
            SkAutomationDataService.start(context, jobId, duplicate, importing, extras)
            ok("OK:$jobId")
        }.getOrElse {
            // Starting the service is the one thing here that can fail after we own a descriptor —
            // close it rather than leak the caller's file open, and take the job back down with it.
            runCatching { duplicate.close() }
            SkAutomationJobs.finish(jobId)
            fail("ERROR:${it.message ?: it.javaClass.simpleName}")
        }
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever `call()`ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong
    // door".
    override fun query(
        uri: Uri,
        projection: Array<String?>?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? = throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int = throw UnsupportedOperationException("automation is call() only")

    companion object {
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.automation"

        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed.
         *
         * The `format` this reports against is [SkEximport.VERSION] — the version already stamped
         * into every archive's `manifest.json`, so the header and the ZIP can never disagree.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
