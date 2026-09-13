package dev.todor.fassistant

import android.content.Context

class Relauncher(
    private val ctx: Context,
    private val watchlist: Watchlist,
    private val log: DeathLog,
) {

    private val recentLaunches = ArrayDeque<Long>()

    /** Returns how the app was started, or null if it was not. */
    fun relaunch(app: WatchedApp, now: Long, reason: String): StartMethod? {
        val observation = watchlist.observation(app.pkg)
        val wait = backoffMs(observation.relaunchCount)
        if (observation.lastRelaunchAt > 0 && now - observation.lastRelaunchAt < wait) return null

        if (!withinRateCap(now)) {
            log.line("skipped ${app.pkg}: rate cap reached")
            return null
        }

        val method = Starter.resolve(ctx, app.pkg)
        if (method is StartMethod.None) {
            log.line("skipped ${app.pkg}: nothing in it can be started")
            return null
        }

        if (!Starter.start(ctx, method)) {
            log.line("failed to start ${app.pkg} (${method.javaClass.simpleName})")
            return null
        }

        recentLaunches.addLast(now)
        watchlist.markRelaunch(app.pkg, now)
        log.line("started ${app.pkg} via ${method.javaClass.simpleName} ($reason)")
        return method
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
