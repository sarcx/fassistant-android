package dev.todor.fassistant.liveness

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.todor.fassistant.WatchedApp
import dev.todor.fassistant.Watchlist

/**
 * Tracks which packages currently hold an ongoing notification. An app running its own
 * foreground service has to show one, so the notification vanishing while the app is not on
 * screen is the strongest death signal available without root.
 */
object NotificationState {

    @Volatile
    var connectedAt = 0L
        private set

    @Volatile
    var ongoing: Set<String> = emptySet()
        private set

    private val lastPresent = HashMap<String, Long>()

    fun onConnected(now: Long) {
        connectedAt = now
    }

    fun onDisconnected() {
        connectedAt = 0L
        ongoing = emptySet()
    }

    fun update(packages: Set<String>, now: Long) {
        ongoing = packages
        synchronized(lastPresent) {
            packages.forEach { lastPresent[it] = now }
        }
    }

    fun lastPresentAt(pkg: String): Long = synchronized(lastPresent) { lastPresent[pkg] ?: 0L }
}

class NotificationWatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationState.onConnected(System.currentTimeMillis())
        sync()
    }

    override fun onListenerDisconnected() {
        NotificationState.onDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = sync()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = sync()

    private fun sync() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        } ?: return
        val ongoing = active.filter { it.isOngoing }.map { it.packageName }.toSet()
        NotificationState.update(ongoing, System.currentTimeMillis())
    }
}

class NotificationSignal(
    private val watchlist: Watchlist,
) : LivenessSignal {

    override val id = "notification"

    override fun available() = NotificationState.connectedAt > 0L

    override fun check(app: WatchedApp, now: Long): Liveness {
        if (NotificationState.ongoing.contains(app.pkg)) {
            watchlist.markNotificationSeen(app.pkg)
            return Liveness.ALIVE
        }
        if (!watchlist.observation(app.pkg).everNotification) return Liveness.UNKNOWN

        // Absence only counts from when we started listening, otherwise every process
        // restart would declare every app dead.
        val absentSince = maxOf(NotificationState.lastPresentAt(app.pkg), NotificationState.connectedAt)
        return if (now - absentSince > app.graceMs) Liveness.DEAD else Liveness.UNKNOWN
    }
}
