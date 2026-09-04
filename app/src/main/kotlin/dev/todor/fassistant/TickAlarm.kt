package dev.todor.fassistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The alarm is what brings the service back after the process is killed: the in-process timer dies
 * with it, this does not. Re-armed on every fire, so a single missed alarm cannot end the chain.
 */
class TickAlarm : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WatchdogService.start(context, "alarm")
    }

    companion object {

        fun schedule(ctx: Context, delayMs: Long) {
            val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val at = System.currentTimeMillis() + delayMs
            // Needs no permission at this targetSdk, and beats Doze once the app is whitelisted.
            runCatching { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(ctx)) }
        }

        fun cancel(ctx: Context) {
            val alarms = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            alarms.cancel(pendingIntent(ctx))
        }

        /**
         * Whether the alarm still exists. A force-stop clears a package's alarms, so this
         * disappearing is evidence of what happened rather than of a crash.
         */
        fun isScheduled(ctx: Context): Boolean = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, TickAlarm::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

        private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, TickAlarm::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
