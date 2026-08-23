package dev.todor.fassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.todor.fassistant.ui.MainActivity
import dev.todor.fassistant.ui.UpdateActivity

object Notifications {

    const val STATUS_ID = 1

    private const val UPDATE_ID = 2
    private const val CHANNEL_STATUS = "status"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_STATUS,
            ctx.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    fun status(ctx: Context, watchedCount: Int, detail: String): Notification {
        val title = if (watchedCount == 0) {
            ctx.getString(R.string.notif_title_none)
        } else {
            ctx.getString(R.string.notif_title, watchedCount)
        }
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return builder(ctx)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(ctx.getString(R.string.notif_text, BuildConfig.VERSION_NAME, detail))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Fallback for a launch the platform dropped. An activity started from a notification the user
     * taps is one of the documented exemptions from the background-start restriction, so this works
     * even when the silent relaunch does not.
     */
    fun postBlocked(ctx: Context, pkg: String, label: CharSequence) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(
            ctx,
            idFor(pkg),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder(ctx)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(ctx.getString(R.string.blocked_title, label))
            .setContentText(ctx.getString(R.string.blocked_text))
            .setStyle(Notification.BigTextStyle().bigText(ctx.getString(R.string.blocked_text)))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager(ctx)?.notify(idFor(pkg), notification)
    }

    fun clearBlocked(ctx: Context, pkg: String) = manager(ctx)?.cancel(idFor(pkg))

    /**
     * Opens the update screen rather than installing directly. The confirmation dialog is an
     * activity, so it has to be started by something the user touched.
     */
    fun postUpdateAvailable(ctx: Context, versionName: String) {
        val open = PendingIntent.getActivity(
            ctx,
            UPDATE_ID,
            Intent(ctx, UpdateActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder(ctx)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(ctx.getString(R.string.update_notification_title, versionName))
            .setContentText(ctx.getString(R.string.update_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager(ctx)?.notify(UPDATE_ID, notification)
    }

    private fun idFor(pkg: String) = 2000 + (pkg.hashCode() and 0x7fff)

    private fun manager(ctx: Context) =
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    @Suppress("DEPRECATION")
    private fun builder(ctx: Context): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, CHANNEL_STATUS)
        } else {
            Notification.Builder(ctx).setPriority(Notification.PRIORITY_LOW)
        }
}
