/*
 * 白い熊 fork (skui): the sister-app state-export automation contract — the same
 * wire shape every 白い熊 app exposes, so 自由作業盤's 保存復元 project can back
 * them all up headlessly in one run (reference implementations: renrakusaki's
 * BackupContactsReceiver, the EMUI-proven reply round-trip, and 自由作業盤's own
 * StateExportReceiver over its category ZIP).
 *
 * - EXPORT_STATE: run the category-ZIP export (SkEximport, the very thing the
 *   Export / Import panel runs) without any UI. Extras, all String: `token`
 *   (required — SkAutomation), `path` (optional absolute directory, wins over
 *   the app's configured export directory), `items` (optional comma list of
 *   SkEximport.Cat ids; absent/empty = everything), `progress_action`
 *   (optional), plus the reply trio `reply_action` / `reply_package` /
 *   `reply_id`.
 * - LIST_CATEGORIES: token-gated category enumeration for the caller's picker.
 *
 * Reply: a FRESH broadcast to `reply_package` with action `reply_action`, extras
 * `reply_id` (echoed verbatim) + `result` = `OK:<path>|<bytes>|<human size>|<n>
 * categories` (EXPORT_STATE), `OK:` + `id<TAB>label` lines (LIST_CATEGORIES), or
 * `ERROR:<reason>`. Exactly one terminal reply, single-fire guarded. NO binders
 * (ResultReceiver / PendingIntent / Messenger) and NO reliance on the ordered
 * result — EMUI severs both between third-party apps (verified 2026-07-23); the
 * plain reply broadcast is the only channel that works. FLAG_INCLUDE_STOPPED_-
 * PACKAGES so a stopped caller still hears us.
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

        // Gate first, and keep the two failures distinct (the family convention).
        if (!SkAutomation.isEnabled) {
            reply("ERROR:automation disabled")
            return
        }
        if (!SkAutomation.isTokenValid(token)) {
            reply("ERROR:bad token")
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
                        val (path, bytes) = writeExport(app, cats, pathOverride, ::sendProgress)
                        reply("OK:$path|$bytes|${humanSize(bytes)}|${cats.size} categories")
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "Export failed", e)
                        reply("ERROR:${e.message ?: e.javaClass.simpleName}")
                    } finally {
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
     */
    private fun writeExport(
        context: Context,
        cats: Set<SkEximport.Cat>,
        pathOverride: String,
        onProgress: (Int, Int, String) -> Unit
    ): Pair<String, Long> {
        val fileName = SkEximport.newExportFileName()
        if (pathOverride.isNotEmpty() && context.hasAllFilesAccess()) {
            // Plain java.io.File — the normal automation route, and why this app
            // declares MANAGE_EXTERNAL_STORAGE.
            val dir = File(pathOverride)
            dir.mkdirs()
            check(dir.isDirectory) { "not a directory: $pathOverride" }
            val file = File(dir, fileName)
            val written = file.outputStream().use { exportCounting(cats, it, onProgress) }
            return file.absolutePath to (file.length().takeIf { it > 0L } ?: written)
        }
        // An unusable `path` is only ignored when we do have a directory of our own.
        val dir =
            SkEximport.exportDirPath
                ?: error(if (pathOverride.isNotEmpty()) "no-storage-access" else "no-directory")
        val target = dir.resolve(fileName)
        val written = target.newOutputStream().use { exportCounting(cats, it, onProgress) }
        return target.toString() to
            (runCatching { target.size() }.getOrNull()?.takeIf { it > 0L } ?: written)
    }

    // The caller cannot stat the file, so we count every byte we write — the
    // authoritative size when the target cannot be stat'ed back.
    private fun exportCounting(
        cats: Set<SkEximport.Cat>,
        out: OutputStream,
        onProgress: (Int, Int, String) -> Unit
    ): Long {
        val countingOut = CountingOutputStream(out)
        SkEximport.export(cats, countingOut, onProgress)
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

        fun humanSize(bytes: Long): String =
            when {
                bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
                bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
                bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
                else -> "$bytes B"
            }
    }
}
