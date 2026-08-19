package dev.todor.fassistant.liveness

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.todor.fassistant.Grants
import dev.todor.fassistant.WatchedApp

class ForegroundSignal(private val ctx: Context) : LivenessSignal {

    override val id = "foreground"

    private val lastResumed = HashMap<String, Long>()
    private var topPackage: String? = null
    private var topSince = 0L

    override fun available() = Grants.hasUsageAccess(ctx)

    @Suppress("DEPRECATION")
    override fun refresh(now: Long) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val events = usm.queryEvents(now - LOOKBACK_MS, now + 1_000) ?: return
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            val pkg = event.packageName ?: continue
            lastResumed[pkg] = event.timeStamp
            if (pkg != topPackage) {
                topPackage = pkg
                topSince = event.timeStamp
            }
        }
    }

    /** The most recently resumed app, however long ago that was. */
    fun topPackage(): String? = topPackage

    fun topSince(): Long = topSince

    fun lastResumedAt(pkg: String): Long = lastResumed[pkg] ?: 0L

    /**
     * Only claims ALIVE while the resume is recent. An app resumed hours ago is probably
     * still resident but we cannot promise it, and a false ALIVE would stop us reopening it.
     */
    override fun check(app: WatchedApp, now: Long): Liveness {
        val resumedAt = lastResumed[app.pkg] ?: return Liveness.UNKNOWN
        return if (now - resumedAt < FRESH_MS) Liveness.ALIVE else Liveness.UNKNOWN
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
        const val FRESH_MS = 60_000L
    }
}
