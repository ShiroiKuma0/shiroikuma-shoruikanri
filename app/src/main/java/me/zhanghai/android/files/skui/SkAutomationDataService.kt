/*
 * 白い熊 fork (skui): where an automation data export or import actually runs.
 * Contract v2 §2a; modelled on 自由作業盤's core/automation/AutomationDataService.kt
 * over this fork's own category ZIP (SkEximport).
 */

package me.zhanghai.android.files.skui

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.NotificationIds
import me.zhanghai.android.files.compat.stopForegroundCompat
import me.zhanghai.android.files.util.NotificationChannelTemplate
import me.zhanghai.android.files.util.NotificationTemplate
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

val skAutomationDataNotificationTemplate =
    NotificationTemplate(
        NotificationChannelTemplate(
            "sk_automation_data",
            R.string.sk_notification_channel_automation_data_name,
            NotificationManagerCompat.IMPORTANCE_LOW,
            descriptionRes = R.string.sk_notification_channel_automation_data_description,
            showBadge = false
        ),
        colorRes = R.color.color_primary,
        smallIcon = R.drawable.notification_icon,
        ongoing = true,
        onlyAlertOnce = true,
        category = NotificationCompat.CATEGORY_SERVICE,
        priority = NotificationCompat.PRIORITY_LOW
    )

/**
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress, and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [SkAutomationProvider] before it got here, because the original belongs to
 * the binder transaction and is closed the moment `call()` returns. This service owns the copy and
 * closes it in a `finally` — leaking one would hold the caller's file open indefinitely, and the
 * caller cannot checksum or encrypt a file that is still open.
 *
 * ## Nothing here touches a path
 *
 * The bytes go into the caller's descriptor and nowhere else. This app's own export directory, its
 * `MANAGE_EXTERNAL_STORAGE` grant and `SkEximport.exportDirPath` are all irrelevant on this route
 * and are deliberately not consulted — that is the point of the descriptor design, and a file
 * manager is the app most likely to forget it.
 */
class SkAutomationDataService : Service() {
    // Plain executor rather than a coroutine scope: nothing else in this fork's skui layer pulls in
    // kotlinx.coroutines, and the work is one long blocking stream either way.
    private val executorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true

        // startForeground FIRST, before any decision that could return.
        //
        // Once startForegroundService() has been called the platform requires this service to go
        // foreground *whatever it then decides*, and kills the whole process with
        // ForegroundServiceDidNotStartInTimeException if it does not. Both checks below can bail —
        // a start with no job id, or a job id whose descriptor has already been collected — so a
        // caller merely retrying with a stale job id would take this app down with it. The order is
        // the fix: satisfy the platform, then decide, then stopSelf if there is nothing to do.
        try {
            startForeground(NotificationIds.SK_AUTOMATION_DATA, notification(importing))
        } catch (e: Throwable) {
            // Nothing left to satisfy the platform with. Still hand back anything this start was
            // carrying rather than leak the caller's file open until the process dies.
            Log.w(LOG_TAG, "Could not go foreground", e)
            intent?.getStringExtra(EXTRA_JOB)?.let {
                handover.remove(it)?.let { fd -> runCatching { fd.close() } }
                SkAutomationJobs.finish(it)
            }
            return stop(startId)
        }

        val jobId = intent?.getStringExtra(EXTRA_JOB) ?: return stop(startId)
        val fd = handover.remove(jobId) ?: return stop(startId)
        val items = intent.getStringExtra(SkAutomationProvider.KEY_ITEMS)
        val progressAction = intent.getStringExtra(SkAutomationProvider.KEY_PROGRESS_ACTION)
        val replyAction = intent.getStringExtra(SkAutomationProvider.KEY_REPLY_ACTION)
        val replyPackage = intent.getStringExtra(SkAutomationProvider.KEY_REPLY_PACKAGE)

        val replied = AtomicBoolean(false)
        fun reply(result: String) {
            // Exactly one terminal answer per job, whatever path got here — a synchronous failure
            // and an asynchronous success must never both fire. The same guard the broadcast
            // contract has carried since the first sister app.
            if (!replied.compareAndSet(false, true)) {
                return
            }
            SkAutomationJobs.finish(jobId)
            if (replyAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) {
                return
            }
            sendBroadcast(
                Intent(replyAction).apply {
                    setPackage(replyPackage)
                    // Without this a caller that has been backgrounded never hears the answer, and
                    // on a clean phone the caller may not have been launched at all.
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtra(SkAutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(SkAutomationProvider.KEY_RESULT, result)
                }
            )
        }

        var lastProgressMillis = 0L
        fun sendProgress(done: Int, total: Int, catLabel: String) {
            // setPackage is not optional: since API 26 an implicit broadcast is not delivered to a
            // manifest-declared receiver at all, so a progress line without it is not weak
            // progress — it is none. A `progress_action` without a `reply_package` to aim it at is
            // therefore silently useless, and we simply do not send.
            if (progressAction.isNullOrEmpty() || replyPackage.isNullOrEmpty()) {
                return
            }
            val now = System.currentTimeMillis()
            if (done < total && now - lastProgressMillis < PROGRESS_INTERVAL_MILLIS) {
                return
            }
            lastProgressMillis = now
            sendBroadcast(
                Intent(progressAction).apply {
                    setPackage(replyPackage)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    // The job id under both names: this door hands the caller a job_id, but the
                    // family's progress panel has always keyed on reply_id, and a caller reading
                    // either finds it.
                    putExtra(SkAutomationProvider.KEY_JOB_ID, jobId)
                    putExtra(EXTRA_PROGRESS_REPLY_ID, jobId)
                    putExtra(EXTRA_PROGRESS_APP, appLabel())
                    putExtra(EXTRA_PROGRESS_TEXT, "区分 $done/$total — $catLabel")
                    putExtra(EXTRA_PROGRESS_CURRENT, done.toLong())
                    putExtra(EXTRA_PROGRESS_TOTAL, total.toLong())
                    putExtra(EXTRA_PROGRESS_UNIT, PROGRESS_UNIT)
                }
            )
        }

        executorService.execute {
            try {
                fd.use {
                    if (importing) {
                        runImport(it, ::reply)
                    } else {
                        runExport(jobId, it, items, ::sendProgress, ::reply)
                    }
                }
            } catch (e: Throwable) {
                reply("ERROR:${e.message ?: e.javaClass.simpleName}")
            } finally {
                stopForegroundCompat(ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun runExport(
        jobId: String,
        fd: ParcelFileDescriptor,
        items: String?,
        onProgress: (Int, Int, String) -> Unit,
        reply: (String) -> Unit
    ) {
        val cats = resolve(items)
        if (cats == null) {
            reply("ERROR:unknown category in items: $items")
            return
        }
        var written = 0L
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out ->
                // Counted as it goes rather than stat'ed afterwards: the caller owns the file and
                // we may not be able to see it at all — it can be an anonymous pipe or a descriptor
                // into a directory this app cannot list.
                val counting = object : OutputStream() {
                    override fun write(b: Int) {
                        out.write(b)
                        ++written
                    }

                    override fun write(b: ByteArray, off: Int, len: Int) {
                        out.write(b, off, len)
                        written += len
                    }
                }
                SkEximport.export(
                    cats, counting, onProgress, { SkAutomationJobs.isCancelled(jobId) }
                )
            }
        } catch (e: SkEximport.CancelledException) {
            // Nothing to delete: the partial bytes are in the caller's file, and disposing of it is
            // the caller's business — it opened it and it knows whether it committed.
            reply("ERROR:cancelled")
            return
        }
        reply("OK:$written|${SkStateExportReceiver.humanSize(written)}|${cats.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [SkEximport.import] wants the entries, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would otherwise import half an archive, and a
     * half-restored app is worse than one that refused.
     */
    private fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        // Held in memory rather than spooled to disk, which is safe *for this app specifically*:
        // its archive is per-category prefs JSON plus whatever font files 白い熊 imported by hand,
        // so it is measured in kilobytes to a few megabytes and never in the tens. An app whose
        // export carries a media corpus must spool instead.
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        if (bytes.isEmpty()) {
            reply("ERROR:empty archive")
            return
        }
        val files = SkEximport.readZip(bytes)
        // Every category the archive actually carries, not every category we know about: asking for
        // one the archive lacks is how a restore ends up reporting success over nothing.
        val present = SkEximport.categoriesIn(files)
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        SkEximport.import(this, files, present)
        // The caller force-stops us straight after this reply, with Process.killProcess — which is
        // a SIGKILL, not an orderly shutdown, so SharedPreferences.apply()'s asynchronous disk
        // write is not guaranteed to have landed. Force every store we touched out to disk BEFORE
        // answering, or a restore can be silently lost between "OK" and the kill.
        SkEximport.flushToDisk()
        reply("OK:${present.size} restored")
    }

    /** Absent/empty `items` means this app's default set, which here is every category. */
    private fun resolve(items: String?): Set<SkEximport.Cat>? {
        if (items.isNullOrBlank()) {
            return SkEximport.Cat.entries.toSet()
        }
        val wanted = items.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val found = wanted.mapNotNull { SkEximport.Cat.byId(it) }
        return if (found.size == wanted.size) found.toSet() else null
    }

    private fun notification(importing: Boolean): Notification =
        skAutomationDataNotificationTemplate.createBuilder(this)
            .setContentTitle(
                getString(
                    if (importing) {
                        R.string.sk_automation_data_notification_importing
                    } else {
                        R.string.sk_automation_data_notification_exporting
                    }
                )
            )
            .build()

    private fun appLabel(): String =
        runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(packageName)

    private fun stop(startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        executorService.shutdown()
    }

    companion object {
        private const val LOG_TAG = "SkAutomationData"

        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        // Contract progress extras — the same bare names §3 defines for the broadcast half, so a
        // caller parses one shape whichever door it came through.
        private const val EXTRA_PROGRESS_REPLY_ID = "reply_id"
        private const val EXTRA_PROGRESS_APP = "app"
        private const val EXTRA_PROGRESS_TEXT = "text"
        private const val EXTRA_PROGRESS_CURRENT = "current"
        private const val EXTRA_PROGRESS_TOTAL = "total"
        private const val EXTRA_PROGRESS_UNIT = "unit"

        private const val PROGRESS_INTERVAL_MILLIS = 500L
        private const val PROGRESS_UNIT = "区分"

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A `ParcelFileDescriptor` in an Intent extra is duplicated by the system on delivery and
         * the copy's lifetime stops being ours to reason about. Handing it through a map keyed by
         * the job id keeps exactly one open descriptor with exactly one owner — the service, which
         * closes it in a `finally`.
         */
        private val handover = ConcurrentHashMap<String, ParcelFileDescriptor>()

        fun start(
            context: Context,
            jobId: String,
            fd: ParcelFileDescriptor,
            importing: Boolean,
            extras: Bundle?
        ) {
            handover[jobId] = fd
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SkAutomationDataService::class.java).apply {
                        putExtra(EXTRA_JOB, jobId)
                        putExtra(EXTRA_IMPORTING, importing)
                        putExtra(
                            SkAutomationProvider.KEY_ITEMS,
                            extras?.getString(SkAutomationProvider.KEY_ITEMS)
                        )
                        putExtra(
                            SkAutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(SkAutomationProvider.KEY_PROGRESS_ACTION)
                        )
                        putExtra(
                            SkAutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(SkAutomationProvider.KEY_REPLY_ACTION)
                        )
                        putExtra(
                            SkAutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(SkAutomationProvider.KEY_REPLY_PACKAGE)
                        )
                    }
                )
            } catch (e: Exception) {
                // The service never started, so nothing will ever collect this descriptor — take it
                // back out rather than pin the caller's file open until the process dies.
                handover.remove(jobId)
                throw e
            }
        }
    }
}
