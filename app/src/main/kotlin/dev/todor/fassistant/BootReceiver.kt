package dev.todor.fassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Watchlist.of(context).enabled) return
        val reason = intent.action?.substringAfterLast('.') ?: "boot"
        DeathLog(context).line("woken by $reason")
        WatchdogService.start(context, reason)
        TickJob.ensure(context)
    }
}
