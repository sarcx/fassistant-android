package dev.todor.fassistant

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The only way to find out why a phone keeps losing the service. Kept on disk because the
 * interesting entries are written by a process that is about to be killed.
 */
class DeathLog(ctx: Context) {

    private val file = File(ctx.filesDir, "watchdog.log")
    private val previous = File(ctx.filesDir, "watchdog.log.1")
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun line(message: String) {
        synchronized(this) {
            if (file.length() > MAX_BYTES) {
                previous.delete()
                file.renameTo(previous)
            }
            runCatching { file.appendText("${stamp.format(Date())} $message\n") }
        }
    }

    fun read(): String = synchronized(this) {
        val head = runCatching { previous.readText() }.getOrDefault("")
        val tail = runCatching { file.readText() }.getOrDefault("")
        head + tail
    }

    fun clear() = synchronized(this) {
        file.delete()
        previous.delete()
    }

    fun logOwnExits(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val reasons = runCatching {
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 3)
        }.getOrNull() ?: return
        reasons.forEach { line("previous exit: reason=${it.reason} status=${it.status} ${it.description}") }
    }

    private companion object {
        const val MAX_BYTES = 128 * 1024L
    }
}
