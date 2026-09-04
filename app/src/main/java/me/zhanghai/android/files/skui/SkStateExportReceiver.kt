/*
 * 白い熊 fork (skui): the sister-app state-export automation contract — the same
 * wire shape every 白い熊 app exposes, so 自由作業盤's 保存復元 project can back
 * them all up headlessly in one run (reference implementations: renrakusaki's
 * BackupContactsReceiver, the EMUI-proven reply round-trip, and 自由作業盤's own
 * StateExportReceiver over its category ZIP).
 *
 * - EXPORT_STATE: run the category-ZIP export (SkEximport, the very thing the
 *   Export / Import panel runs) without any UI. Extras, all String: `token`
 *   (optional — only checked when 白い熊 switched 「Use authorization token?」
 *   on; see SkAutomation), `path` (optional absolute directory, wins over the
 *   app's configured export directory), `items` (optional comma list of
 *   SkEximport.Cat ids; absent/empty = our default set, which here is
 *   everything), `progress_action` (optional), plus the reply trio
 *   `reply_action` / `reply_package` / `reply_id`.
 * - LIST_CATEGORIES: category enumeration for the caller's picker.
 * - CANCEL_EXPORT: stop the running export, delete its partial file, and answer
 *   the ORIGINAL request with ERROR:cancelled. Sends no reply of its own.
 *
 * This receiver is the **unauthenticated** half of the surface in contract v2,
 * and that is deliberate: it only ever writes where it was told to and reports
 * what it did. Everything that moves data through a caller-supplied descriptor
 * lives behind SkAutomationProvider, which knows who is calling.
 *
 * Reply: a FRESH broadcast to `reply_package` with action `reply_action`, extras
 * `reply_id` (echoed verbatim) + `result` = `OK:<path>|<bytes>|<human size>|<n>
 * categories` (EXPORT_STATE), `OK:` + `id<TAB>label` lines (LIST_CATEGORIES), or
 * `ERROR:<reason>`. Exactly one terminal reply, single-fire guarded. NO binders
 * (ResultReceiver / PendingIntent / Messenger) and NO reliance on the ordered
 * result — EMUI severs both between third-party apps (verified 2026-07-23); the
 * plain reply broadcast is the only channel that works. FLAG_INCLUDE_STOPPED_-
 * PACKAGES so a stopped caller still hears us, and a <queries> element in the
 * manifest so setPackage() resolves at all.
 *
 * Progress: while exporting, plain broadcasts to `reply_package` with action
 * `progress_action` — `reply_id`, `app` (display label), `text` (numbers first,
 * never a percentage), and structured `current` / `total` (long) + `unit`.
 */

package me.zhanghai.android.files.skui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.provider.common.deleteIfExists
import me.zhanghai.android.files.provider.common.moveTo
import me.zhanghai.android.files.provider.common.newOutputStream
import me.zhanghai.android.files.provider.common.size
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SkStateExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN)
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()
        val pathOverride = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
        val items = intent.getStringExtra(EXTRA_ITEMS)?.trim().orEmpty()

        // Cancel is fire-and-forget and has no reply of its own — the one terminal reply belongs to
        // the export request it stops. It must also be safe to send at any time: arriving when
        // nothing is running, or after the export already finished, is a silent no-op, because
        // 自由作業盤 fires it whenever 白い熊 presses 中止 without knowing how far we got.
        if (action == ACTION_CANCEL_EXPORT) {
            if (SkAutomation.refuse(token) != null) {
                return
            }
            requestCancel(replyId)
            return
        }

        val replied = AtomicBoolean(false)
        // Set once the work went async — the ordered result must then be filled
        // through the PendingResult instead of the receiver.
        var pendingResult: PendingResult? = null

        fun reply(result: String) {
            if (!replied.compareAndSet(false, true)) {
                return
            }
            Log.i(LOG_TAG, "$action [$replyId] → $result")
            // Filling the ordered result too is correct AOSP behaviour (and what
            // `am broadcast` prints), but it is never our only channel.
            runCatching {
                val pending = pendingResult
                if (pending != null) {
                    pending.setResultData(result)
                } else if (isOrderedBroadcast) {
                    resultData = result
                }
            }
            if (replyAction.isEmpty() || replyPackage.isEmpty()) {
                return
            }
            app.sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(EXTRA_REPLY_ID, replyId)
                    putExtra(EXTRA_RESULT, result)
                }
            )
        }

        // The whole gate in one call, so "disabled" and "bad token" cannot drift apart — and so a
        // token sent to an app that does not require one is ignored rather than refused.
        SkAutomation.refuse(token)?.let {
            reply(it)
            return
        }

        when (action) {
            ACTION_LIST_CATEGORIES ->
                reply(
                    "OK:" +
                        SkEximport.Cat.entries.joinToString("\n") {
                            "${it.id}\t${app.getString(it.labelRes)}"
                        }
                )
            ACTION_EXPORT_STATE -> {
                val cats =
                    if (items.isEmpty()) {
                        SkEximport.Cat.entries.toSet()
                    } else {
                        val ids = items.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val resolved = ids.mapNotNull { SkEximport.Cat.byId(it) }
                        if (resolved.size != ids.size) {
                            reply("ERROR:unknown category in items: $items")
                            return
                        }
                        resolved.toSet()
                    }
                // Process-local, never persisted, and released in the finally below: persist it and
                // a single crash wedges the app for good. It also makes CANCEL_EXPORT's "absent
                // reply_id = the export you are running" unambiguous.
                if (!exportRunning.compareAndSet(false, true)) {
                    reply("ERROR:export already running")
                    return
                }
                cancelRequested = false
                runningReplyId = replyId
                val appLabel =
                    app.packageManager.getApplicationLabel(app.applicationInfo).toString()
                var lastProgressMillis = 0L

                fun sendProgress(done: Int, total: Int, catLabel: String) {
                    if (progressAction.isEmpty() || replyPackage.isEmpty()) {
                        return
                    }
                    val now = System.currentTimeMillis()
                    if (done < total && now - lastProgressMillis < PROGRESS_INTERVAL_MILLIS) {
                        return
                    }
                    lastProgressMillis = now
                    app.sendBroadcast(
                        Intent(progressAction).apply {
                            setPackage(replyPackage)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            putExtra(EXTRA_REPLY_ID, replyId)
                            putExtra(EXTRA_PROGRESS_APP, appLabel)
                            putExtra(EXTRA_PROGRESS_TEXT, "区分 $done/$total — $catLabel")
                            putExtra(EXTRA_PROGRESS_CURRENT, done.toLong())
                            putExtra(EXTRA_PROGRESS_TOTAL, total.toLong())
                            putExtra(EXTRA_PROGRESS_UNIT, PROGRESS_UNIT)
                        }
                    )
                }

                // The export walks every prefs store and copies the font files —
                // hold the broadcast open and finish from a worker thread.
                pendingResult = goAsync()
                val pending = pendingResult
                Thread {
                    try {
                        val (path, bytes) =
                            writeExport(app, cats, pathOverride, ::sendProgress) { cancelRequested }
                        reply("OK:$path|$bytes|${humanSize(bytes)}|${cats.size} categories")
                    } catch (e: SkEximport.CancelledException) {
                        // The partial file is already gone — writeExport deletes it on the way out.
                        // Send this even though nobody may still be listening: 自由作業盤 stops
                        // waiting the moment it presses 中止, and the reply is what proves the run
                        // really ended rather than continuing unseen.
                        Log.i(LOG_TAG, "Export cancelled [$replyId]")
                        reply("ERROR:cancelled")
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Export failed", e)
                        reply("ERROR:${e.message ?: e.javaClass.simpleName}")
                    } finally {
                        runningReplyId = null
                        cancelRequested = false
                        exportRunning.set(false)
                        pending?.finish()
                    }
                }.start()
            }
            else -> reply("ERROR:unknown action: $action")
        }
    }

    /**
     * Write the one ZIP and return its (path, byte length). Directory precedence is the
     * contract's: the `path` extra, then the app's configured export directory, then an error.
     *
     * Written to `<final-name>.part` and renamed only once the archive is closed and complete, and
     * the partial is deleted on **any** way out — a failure, or a cancel. A killed or cancelled
     * export otherwise leaves a file indistinguishable from a real backup until someone tries to
     * restore it, and 白い熊 keeps every app's backups in one directory sorted by date, so a
     * truncated one silently becomes "the latest backup" of this app.
     */
    private fun writeExport(
        context: Context,
        cats: Set<SkEximport.Cat>,
        pathOverride: String,
        onProgress: (Int, Int, String) -> Unit,
        isCancelled: () -> Boolean
    ): Pair<String, Long> {
        val fileName = SkEximport.newExportFileName()
        val partName = fileName + PART_SUFFIX
        if (pathOverride.isNotEmpty() && context.hasAllFilesAccess()) {
            // Plain java.io.File — the normal automation route, and why this app
            // declares MANAGE_EXTERNAL_STORAGE.
            val dir = File(pathOverride)
            dir.mkdirs()
            check(dir.isDirectory) { "not a directory: $pathOverride" }
            val part = File(dir, partName)
            val file = File(dir, fileName)
            try {
                val written = part.outputStream().use {
                    exportCounting(cats, it, onProgress, isCancelled)
                }
                check(part.renameTo(file)) { "could not rename $partName" }
                return file.absolutePath to (file.length().takeIf { it > 0L } ?: written)
            } catch (e: Throwable) {
                part.delete()
                throw e
            }
        }
        // An unusable `path` is only ignored when we do have a directory of our own.
        val dir =
            SkEximport.exportDirPath
                ?: error(if (pathOverride.isNotEmpty()) "no-storage-access" else "no-directory")
        val part = dir.resolve(partName)
        val target = dir.resolve(fileName)
        try {
            val written = part.newOutputStream().use {
                exportCounting(cats, it, onProgress, isCancelled)
            }
            part.moveTo(target)
            return target.toString() to
                (runCatching { target.size() }.getOrNull()?.takeIf { it > 0L } ?: written)
        } catch (e: Throwable) {
            runCatching { part.deleteIfExists() }
            throw e
        }
    }

    // The caller cannot stat the file, so we count every byte we write — the
    // authoritative size when the target cannot be stat'ed back.
    private fun exportCounting(
        cats: Set<SkEximport.Cat>,
        out: OutputStream,
        onProgress: (Int, Int, String) -> Unit,
        isCancelled: () -> Boolean
    ): Long {
        val countingOut = CountingOutputStream(out)
        SkEximport.export(cats, countingOut, onProgress, isCancelled)
        countingOut.flush()
        return countingOut.count
    }

    private fun Context.hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            ++count
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() {
            out.flush()
        }

        override fun close() {
            out.close()
        }
    }

    companion object {
        private const val LOG_TAG = "SkStateExport"

        val ACTION_EXPORT_STATE = "${BuildConfig.APPLICATION_ID}.action.EXPORT_STATE"
        val ACTION_LIST_CATEGORIES = "${BuildConfig.APPLICATION_ID}.action.LIST_CATEGORIES"
        val ACTION_CANCEL_EXPORT = "${BuildConfig.APPLICATION_ID}.action.CANCEL_EXPORT"

        // Contract extras — deliberately bare names, shared verbatim by every sister app.
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ITEMS = "items"
        private const val EXTRA_PROGRESS_ACTION = "progress_action"
        private const val EXTRA_REPLY_ACTION = "reply_action"
        private const val EXTRA_REPLY_PACKAGE = "reply_package"
        private const val EXTRA_REPLY_ID = "reply_id"
        private const val EXTRA_RESULT = "result"
        private const val EXTRA_PROGRESS_APP = "app"
        private const val EXTRA_PROGRESS_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "current"
        private const val EXTRA_PROGRESS_TOTAL = "total"
        private const val EXTRA_PROGRESS_UNIT = "unit"

        private const val PROGRESS_INTERVAL_MILLIS = 500L
        private const val PROGRESS_UNIT = "区分"

        /** The half-written archive's name, never left behind. */
        private const val PART_SUFFIX = ".part"

        // Process-local, never persisted. §1 forbids two exports at once, which is what makes a
        // cancel without a reply_id unambiguous.
        private val exportRunning = AtomicBoolean(false)

        @Volatile
        private var runningReplyId: String? = null

        @Volatile
        private var cancelRequested = false

        /**
         * Ask the running export to stop at its next write boundary.
         *
         * A no-op when nothing is running, or when [replyId] names a run that is not the current
         * one — a cancel for a run that already finished is the normal race, not an error. The flag
         * is only read between whole entries, so the export unwinds rather than being torn down
         * mid-`write()`; nothing here interrupts a thread or exits the process.
         */
        fun requestCancel(replyId: String?) {
            if (!exportRunning.get()) {
                return
            }
            if (!replyId.isNullOrEmpty() && replyId != runningReplyId) {
                return
            }
            cancelRequested = true
        }

        fun humanSize(bytes: Long): String =
            when {
                bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
                bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
                bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
                else -> "$bytes B"
            }
    }
}
