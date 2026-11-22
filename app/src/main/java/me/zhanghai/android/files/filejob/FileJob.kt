/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set

    // 白い熊 fork: what a tap on this job's notification opens (the extraction progress dialog).
    internal var skContentIntent: android.app.PendingIntent? = null

    // 白い熊 fork: set by SkExtractProgress.prepare() for a job that shows its progress in a
    // dialog — an extraction, a copy, a move, the creation of an archive.
    internal var skProgress: SkExtractProgress.Session? = null

    fun runOn(service: FileJobService) {
        this.service = service
        try {
            run()
            // TODO: Toast
        } catch (e: InterruptedIOException) {
            // TODO
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            service.showToast(e.toString())
        } finally {
            service.notificationManager.cancel(id)
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()
}
