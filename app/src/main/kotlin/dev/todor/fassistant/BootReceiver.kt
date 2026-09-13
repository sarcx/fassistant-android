package dev.todor.fassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val watchlist = Watchlist.of(context)
        val reason = intent.action?.substringAfterLast('.') ?: "boot"

        // Recorded before anything can return early, so "did the broadcast arrive at all?" stays
        // answerable. A phone that withholds it looks identical to one where we ignored it.
        watchlist.bootSeenAt = System.currentTimeMillis()
        DeathLog(context).line("woken by $reason")

        if (!watchlist.enabled) {
            DeathLog(context).line("ignored $reason: the watchdog is switched off")
            return
        }
        WatchdogService.start(context, reason)
        TickJob.ensure(context)
    }
}
