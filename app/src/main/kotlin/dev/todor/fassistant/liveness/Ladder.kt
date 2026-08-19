package dev.todor.fassistant.liveness

import android.content.Context
import dev.todor.fassistant.WatchedApp
import dev.todor.fassistant.Watchlist

/**
 * Walks the signals in order of certainty and stops at the first one with an opinion. Signals that
 * are unavailable on this phone are skipped, so a missing grant degrades the ladder rather than
 * breaking it.
 */
class Ladder(ctx: Context, watchlist: Watchlist) {

    val foreground = ForegroundSignal(ctx)
    val process = ProcessSignal(ctx)

    private val signals: List<LivenessSignal> = listOf(
        foreground,
        process,
        NotificationSignal(watchlist),
        MediaSignal(ctx, watchlist),
        StoppedFlagSignal(ctx),
    )

    fun refresh(now: Long) = signals.forEach { runCatching { it.refresh(now) } }

    fun verdict(app: WatchedApp, now: Long): Verdict {
        for (signal in signals) {
            if (!signal.available()) continue
            val liveness = runCatching { signal.check(app, now) }.getOrDefault(Liveness.UNKNOWN)
            if (liveness != Liveness.UNKNOWN) return Verdict(liveness, signal.id)
        }
        return Verdict(Liveness.UNKNOWN, "none")
    }

    fun availableSignals(): List<String> = signals.filter { it.available() }.map { it.id }
}
