package dev.todor.fassistant

import android.content.Context
import android.content.Intent

class Relauncher(
    private val ctx: Context,
    private val watchlist: Watchlist,
    private val log: DeathLog,
) {

    private val recentLaunches = ArrayDeque<Long>()

    fun relaunch(app: WatchedApp, now: Long, reason: String): Boolean {
        val observation = watchlist.observation(app.pkg)
        val wait = backoffMs(observation.relaunchCount)
        if (observation.lastRelaunchAt > 0 && now - observation.lastRelaunchAt < wait) return false

        if (!withinRateCap(now)) {
            log.line("skipped ${app.pkg}: rate cap reached")
            return false
        }

        val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
        if (intent == null) {
            log.line("skipped ${app.pkg}: no launchable screen")
            return false
        }
        // No CLEAR_TASK on purpose — if the app is somehow alive, resume it rather than restart it.
        // No animation, because the app is usually sent straight back again and the transition
        // would be the most visible part of an operation meant to go unnoticed.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        return try {
            ctx.startActivity(intent)
            recentLaunches.addLast(now)
            watchlist.markRelaunch(app.pkg, now)
            log.line("reopened ${app.pkg} ($reason)")
            true
        } catch (e: Exception) {
            log.line("failed to reopen ${app.pkg}: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun withinRateCap(now: Long): Boolean {
        while (recentLaunches.isNotEmpty() && now - recentLaunches.first() > RATE_WINDOW_MS) {
            recentLaunches.removeFirst()
        }
        return recentLaunches.size < MAX_PER_WINDOW
    }

    private fun backoffMs(relaunchCount: Int): Long =
        BACKOFF_MS[relaunchCount.coerceIn(0, BACKOFF_MS.size - 1)]

    private companion object {
        val BACKOFF_MS = longArrayOf(0, 10_000, 30_000, 120_000, 300_000, 900_000)
        const val RATE_WINDOW_MS = 600_000L
        const val MAX_PER_WINDOW = 12
    }
}
