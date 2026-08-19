package dev.todor.fassistant.liveness

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import dev.todor.fassistant.WatchedApp
import dev.todor.fassistant.Watchlist

/**
 * A media session disappearing is a death signal for anything that plays audio. Rides on the
 * notification-access grant, so it costs nothing beyond what [NotificationWatcher] already needs.
 */
class MediaSignal(
    private val ctx: Context,
    private val watchlist: Watchlist,
) : LivenessSignal {

    override val id = "media"

    private val lastPresent = HashMap<String, Long>()
    private var permitted = false
    private var permittedSince = 0L
    private var active: Set<String> = emptySet()

    override fun available() = permitted

    override fun refresh(now: Long) {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val component = ComponentName(ctx, NotificationWatcher::class.java)
        active = try {
            msm.getActiveSessions(component).mapNotNull { it.packageName }.toSet()
        } catch (e: SecurityException) {
            permitted = false
            return
        }
        if (!permitted) {
            permitted = true
            permittedSince = now
        }
        active.forEach { lastPresent[it] = now }
    }

    override fun check(app: WatchedApp, now: Long): Liveness {
        if (active.contains(app.pkg)) {
            watchlist.markMediaSeen(app.pkg)
            return Liveness.ALIVE
        }
        if (!watchlist.observation(app.pkg).everMediaSession) return Liveness.UNKNOWN

        val absentSince = maxOf(lastPresent[app.pkg] ?: 0L, permittedSince)
        return if (now - absentSince > app.graceMs) Liveness.DEAD else Liveness.UNKNOWN
    }
}
