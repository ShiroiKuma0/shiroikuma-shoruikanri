/*
 * 白い熊 fork: live progress of a file job — an extraction (a paste out of an archive), a copy,
 * a move, the creation of an archive — shared between the job that does the work and the
 * dialog that shows it. (It began with extraction, hence the name.)
 *
 * The job owns a Session and pushes numbers into it from its worker thread; the dialog
 * (SkExtractProgressDialogFragment) observes the session and writes one thing back — the name
 * 白い熊 typed for the extracted item, which the job applies as soon as that item is complete.
 * A session outlives its dialog, so hiding the dialog and bringing it back from the notification
 * picks the same numbers up again.
 */

package me.zhanghai.android.files.filejob

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.util.showToast
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SkExtractProgress {
    // A job this quick would only flash the dialog, so it gets none.
    private const val SHOW_DELAY_MILLIS = 400L

    // How often the dialog is fed, and the window the speed is averaged over.
    private const val POST_INTERVAL_MILLIS = 100L
    private const val SPEED_WINDOW_MILLIS = 4000L

    private val sessions = ConcurrentHashMap<Int, Session>()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** What the job does; it words the dialog. */
    enum class Kind(val titleRes: Int, val doneTitleRes: Int) {
        EXTRACT(R.string.sk_extract_progress_title, R.string.sk_extract_progress_title_done),
        COPY(R.string.sk_progress_title_copy, R.string.sk_progress_title_copy_done),
        MOVE(R.string.sk_progress_title_move, R.string.sk_progress_title_move_done),
        ARCHIVE(R.string.sk_progress_title_archive, R.string.sk_progress_title_archive_done)
    }

    enum class Phase {
        PREPARING,
        EXTRACTING,
        FINISHED,
        CANCELLED
    }

    class Snapshot(
        val phase: Phase,
        // The name the extracted item has (or will have) in the target directory.
        val name: String?,
        // The archive entry being written right now.
        val currentEntry: String?,
        val fileIndex: Int,
        val fileCount: Int,
        val transferredSize: Long,
        val size: Long,
        // Bytes per second over the last few seconds; 0 while unknown.
        val speed: Long,
        // The entry being written right now: how much of it is done, and its size.
        val entryTransferredSize: Long = 0,
        val entrySize: Long = 0
    ) {
        val entryFraction: Float
            get() = if (entrySize > 0) {
                (entryTransferredSize.toFloat() / entrySize).coerceIn(0f, 1f)
            } else {
                0f
            }

        val fraction: Float
            get() = if (size > 0) (transferredSize.toFloat() / size).coerceIn(0f, 1f) else 0f

        // Seconds left at the current speed; -1 while unknown.
        val etaSeconds: Long
            get() = if (speed > 0 && size > transferredSize) {
                (size - transferredSize + speed - 1) / speed
            } else {
                -1
            }
    }

    class Session internal constructor(
        val jobId: Int,
        val kind: Kind,
        // Where the items come from: the archive of an extraction, else their directory.
        val sourceName: String,
        val targetDirectory: Path,
        // Only a job that produces one item has one name to edit.
        val isRenamable: Boolean
    ) {
        private val _liveData = MutableLiveData(
            Snapshot(Phase.PREPARING, null, null, 0, 0, 0, 0, 0)
        )
        val liveData: LiveData<Snapshot>
            get() = _liveData

        private val startMillis = SystemClock.elapsedRealtime()

        @Volatile
        private var endMillis = 0L

        val elapsedMillis: Long
            get() = (if (endMillis != 0L) endMillis else SystemClock.elapsedRealtime()) -
                startMillis

        /** The name typed in the dialog, when it is valid and differs from the current one. */
        @Volatile
        var requestedName: String? = null

        /** Where the extracted item ended up — set once it is complete. */
        @Volatile
        var finalPath: Path? = null

        /** 白い熊 pressed Hide: completion is then reported with a toast instead. */
        @Volatile
        var isHiddenByUser = false

        @Volatile
        var isFinished = false
            private set

        @Volatile
        private var attachedDialogCount = 0

        // Written by the job thread only.
        @Volatile
        private var latest: Snapshot = _liveData.value!!
        private var lastPostMillis = 0L
        private val speedSamples = ArrayDeque<LongArray>()

        // The entry being written: its key, where the total stood when it began, its size.
        private var entryKey: String? = null
        private var entryStartSize = 0L
        private var entrySize = 0L

        /** A line for the notification: “12 MB/s • 1:23 left”. */
        @Volatile
        var notificationSubText: String? = null
            private set

        fun setName(name: String) {
            val snapshot = latest
            post(
                Snapshot(
                    snapshot.phase, name, snapshot.currentEntry, snapshot.fileIndex,
                    snapshot.fileCount, snapshot.transferredSize, snapshot.size, snapshot.speed
                ), true
            )
        }

        /**
         * @param entryKey what tells one entry from the next (its full path)
         * @param readEntrySize the size of that entry; asked only when the entry changes
         */
        fun update(
            entryKey: String,
            currentEntry: String,
            transferredFileCount: Int,
            fileCount: Int,
            transferredSize: Long,
            size: Long,
            readEntrySize: () -> Long
        ) {
            if (isFinished) {
                return
            }
            if (entryKey != this.entryKey) {
                this.entryKey = entryKey
                entryStartSize = transferredSize
                entrySize = try {
                    readEntrySize()
                } catch (e: Exception) {
                    0
                }
            }
            val now = SystemClock.elapsedRealtime()
            speedSamples.addLast(longArrayOf(now, transferredSize))
            while (speedSamples.size > 2 && now - speedSamples.first()[0] > SPEED_WINDOW_MILLIS) {
                speedSamples.removeFirst()
            }
            val oldest = speedSamples.first()
            val spanMillis = now - oldest[0]
            // Less than half a second of samples says nothing yet.
            val speed = if (spanMillis >= 500) {
                ((transferredSize - oldest[1]) * 1000 / spanMillis).coerceAtLeast(0)
            } else {
                latest.speed
            }
            val fileIndex = (transferredFileCount + 1).coerceAtMost(fileCount)
            val snapshot = Snapshot(
                Phase.EXTRACTING, latest.name, currentEntry, fileIndex, fileCount,
                transferredSize, size, speed,
                (transferredSize - entryStartSize).coerceIn(0, maxOf(entrySize, 0)), entrySize
            )
            notificationSubText = formatSpeedAndEta(snapshot, application)
            post(snapshot, fileIndex != latest.fileIndex)
        }

        fun finish(successful: Boolean) {
            if (isFinished) {
                return
            }
            isFinished = true
            endMillis = SystemClock.elapsedRealtime()
            val snapshot = latest
            // The average over the whole job, which is what a finished dialog shows.
            val averageSpeed = if (elapsedMillis > 0) {
                snapshot.size * 1000 / elapsedMillis
            } else {
                0
            }
            post(
                if (successful) {
                    Snapshot(
                        Phase.FINISHED, snapshot.name, null, snapshot.fileCount,
                        snapshot.fileCount, snapshot.size, snapshot.size, averageSpeed
                    )
                } else {
                    Snapshot(
                        Phase.CANCELLED, snapshot.name, null, snapshot.fileIndex,
                        snapshot.fileCount, snapshot.transferredSize, snapshot.size, 0
                    )
                }, true
            )
            val name = snapshot.name
            mainHandler.post {
                if (successful && isHiddenByUser && attachedDialogCount == 0) {
                    application.showToast(
                        application.getString(
                            R.string.sk_extract_progress_done_toast_format,
                            application.getString(kind.doneTitleRes), name ?: sourceName
                        )
                    )
                }
                removeIfUnused()
            }
        }

        private fun post(snapshot: Snapshot, force: Boolean) {
            latest = snapshot
            val now = SystemClock.elapsedRealtime()
            if (force || now - lastPostMillis >= POST_INTERVAL_MILLIS) {
                lastPostMillis = now
                _liveData.postValue(snapshot)
            }
        }

        fun onDialogAttached() {
            ++attachedDialogCount
            isHiddenByUser = false
        }

        fun onDialogDetached() {
            --attachedDialogCount
            removeIfUnused()
        }

        private fun removeIfUnused() {
            if (isFinished && attachedDialogCount <= 0) {
                sessions.remove(jobId)
            }
        }
    }

    fun get(jobId: Int): Session? = sessions[jobId]

    /**
     * Called on the main thread right before a job is queued: give it a session, make its
     * notification open the dialog, and show the dialog unless the job is over before it could
     * be read.
     */
    fun prepare(
        job: FileJob,
        kind: Kind,
        sources: List<Path>,
        targetDirectory: Path,
        context: Context
    ) {
        if (sources.isEmpty()) {
            return
        }
        val first = sources.first()
        val sourceName = if (kind == Kind.EXTRACT) {
            first.archiveFile.fileName?.toString()
        } else {
            first.parent?.toString()
        } ?: ""
        // One name to edit: the one item of a copy, a move or an extraction, or the archive
        // being made.
        val isRenamable = when (kind) {
            Kind.EXTRACT, Kind.COPY, Kind.MOVE -> sources.size == 1
            Kind.ARCHIVE -> true
        }
        val session = Session(job.id, kind, sourceName, targetDirectory, isRenamable)
        sessions[job.id] = session
        job.skProgress = session
        job.skContentIntent = PendingIntent.getActivity(
            application, job.id,
            SkExtractProgressActivity.createIntent(job.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val activityReference = WeakReference(context as? Activity)
        mainHandler.postDelayed({
            val activity = activityReference.get()
            if (!session.isFinished && activity != null && !activity.isFinishing
                && !activity.isDestroyed) {
                activity.startActivity(SkExtractProgressActivity.createIntent(job.id))
            }
        }, SHOW_DELAY_MILLIS)
    }

    fun formatSize(size: Long, context: Context): String =
        size.asFileSize().formatHumanReadable(context)

    fun formatSpeed(speed: Long, context: Context): String =
        context.getString(R.string.sk_extract_progress_speed_format, formatSize(speed, context))

    /** “1:05” below an hour, “1:02:05” from there on. */
    fun formatDuration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = safeSeconds % 3600 / 60
        val remainder = safeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, remainder)
        }
    }

    /** “12 MB/s • 1:23 left”, or whichever half of it is known; null when neither is. */
    fun formatSpeedAndEta(snapshot: Snapshot, context: Context): String? {
        val parts = mutableListOf<String>()
        if (snapshot.speed > 0) {
            parts += formatSpeed(snapshot.speed, context)
        }
        val etaSeconds = snapshot.etaSeconds
        if (etaSeconds >= 0) {
            parts += context.getString(
                R.string.sk_extract_progress_eta_format, formatDuration(etaSeconds)
            )
        }
        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }
}
