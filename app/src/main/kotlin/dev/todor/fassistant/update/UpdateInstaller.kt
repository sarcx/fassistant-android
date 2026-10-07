package dev.todor.fassistant.update

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import dev.todor.fassistant.DeathLog
import dev.todor.fassistant.R

/**
 * Opens Android's own install screen on a verified update, which asks the user and does the install.
 *
 * Not a PackageInstaller session, although that is the newer route. Xiaomi's Android, with its
 * default "MIUI optimization" on, refuses sessions from ordinary apps with
 * "INSTALL_FAILED_INTERNAL_ERROR: Permission Denied", and the only cure on the phone is a developer
 * setting. The install screen is allowed there, and works the same everywhere else. It reads the
 * APK through [UpdateProvider].
 *
 * Must be started from something the user just touched. The install screen is an activity, and
 * starting one from the background is exactly what Android blocks; a service that tried this would
 * silently do nothing.
 */
object UpdateInstaller {

    fun install(activity: Activity, update: ReadyUpdate) {
        val log = DeathLog(activity)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(UpdateProvider.uriFor(activity, update.apk), UpdateProvider.MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            activity.startActivity(intent)
            log.line("install screen opened for ${update.manifest.versionName}")
        } catch (e: Exception) {
            log.line("install screen failed: ${e.javaClass.simpleName} ${e.message}")
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
        }
    }
}
