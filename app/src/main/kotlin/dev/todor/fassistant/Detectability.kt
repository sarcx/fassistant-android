package dev.todor.fassistant

import android.content.Context

enum class Badge { EXACT, STRONG, WEAK, NO_LAUNCHER }

class Detectability(val badge: Badge, val whyRes: Int)

object Detectabilities {

    fun of(ctx: Context, pkg: String, watchlist: Watchlist, processChecksWork: Boolean): Detectability {
        // Having no icon is not the same as being unstartable: a plugin with an exported service
        // has no launcher activity but its process can still be started.
        if (Starter.resolve(ctx, pkg) is StartMethod.None) {
            return Detectability(Badge.NO_LAUNCHER, R.string.badge_no_launcher_why)
        }
        if (processChecksWork) return Detectability(Badge.EXACT, R.string.badge_exact_why)

        val observation = watchlist.observation(pkg)
        if (observation.everNotification) return Detectability(Badge.STRONG, R.string.badge_strong_notification)
        if (observation.everMediaSession) return Detectability(Badge.STRONG, R.string.badge_strong_media)
        return Detectability(Badge.WEAK, R.string.badge_weak_why)
    }

    fun labelRes(badge: Badge): Int = when (badge) {
        Badge.EXACT -> R.string.badge_exact
        Badge.STRONG -> R.string.badge_strong
        Badge.WEAK -> R.string.badge_weak
        Badge.NO_LAUNCHER -> R.string.badge_no_launcher
    }

    fun defaultMode(badge: Badge): Mode = if (badge == Badge.WEAK) Mode.SWEEP else Mode.ON_SIGNAL
}
