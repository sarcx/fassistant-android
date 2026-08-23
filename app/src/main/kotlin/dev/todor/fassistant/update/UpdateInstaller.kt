package dev.todor.fassistant.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import dev.todor.fassistant.DeathLog
import dev.todor.fassistant.R
import java.io.File

/**
 * Installs an APK through [PackageInstaller], which takes a stream rather than a URI — so there is
 * no file:// exposure to worry about and no content provider to write.
 *
 * Must be started from something the user just touched. The system's confirmation screen is an
 * activity, and starting one from the background is exactly what Android blocks; a service that
 * tried this would silently do nothing.
 */
object UpdateInstaller {

    fun install(activity: Activity, update: ReadyUpdate) {
        val log = DeathLog(activity)
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(activity.packageName) }

        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("fassistant", 0, update.apk.length()).use { sink ->
                    update.apk.inputStream().use { it.copyTo(sink) }
                    session.fsync(sink)
                }
                session.commit(callback(activity, sessionId).intentSender)
            }
            log.line("install session committed for ${update.manifest.versionName}")
        } catch (e: Exception) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            log.line("install session failed: ${e.javaClass.simpleName} ${e.message}")
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun callback(ctx: Context, sessionId: Int): PendingIntent {
        val intent = Intent(ctx, InstallResultReceiver::class.java)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        // The system writes the outcome into this intent, so it cannot be immutable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
    }
}

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val log = DeathLog(context)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = confirmationIntent(intent) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!runCatching { context.startActivity(confirm) }.isSuccess) {
                log.line("could not show the install confirmation")
            }
            return
        }

        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            log.line("update installed")
        } else {
            log.line("update not installed: status $status ${message.orEmpty()}")
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
