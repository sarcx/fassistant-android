package dev.todor.fassistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Mode { ON_SIGNAL, KEEP_IN_FRONT, SWEEP }

data class WatchedApp(
    val pkg: String,
    val mode: Mode = Mode.ON_SIGNAL,
    val graceMs: Long = DEFAULT_GRACE_MS,
    val sweepMs: Long = DEFAULT_SWEEP_MS,
) {
    companion object {
        const val DEFAULT_GRACE_MS = 30_000L
        const val DEFAULT_SWEEP_MS = 300_000L
    }
}

data class Observation(
    val everNotification: Boolean = false,
    val everMediaSession: Boolean = false,
    val lastAliveAt: Long = 0L,
    val lastRelaunchAt: Long = 0L,
    val relaunchCount: Int = 0,
)

class Watchlist private constructor(private val prefs: SharedPreferences) {

    private var apps: MutableMap<String, WatchedApp> = readApps()
    private var observations: MutableMap<String, Observation> = readObservations()

    fun all(): List<WatchedApp> = apps.values.sortedBy { it.pkg }

    fun size(): Int = apps.size

    fun get(pkg: String): WatchedApp? = apps[pkg]

    fun isWatched(pkg: String): Boolean = apps.containsKey(pkg)

    fun put(app: WatchedApp) {
        apps[app.pkg] = app
        writeApps()
    }

    fun remove(pkg: String) {
        apps.remove(pkg)
        writeApps()
    }

    fun observation(pkg: String): Observation = observations[pkg] ?: Observation()

    fun markAlive(pkg: String, now: Long) {
        val o = observation(pkg)
        val resetBackoff = now - o.lastRelaunchAt > BACKOFF_RESET_MS
        update(pkg, Observation(o.everNotification, o.everMediaSession, now, o.lastRelaunchAt, if (resetBackoff) 0 else o.relaunchCount))
    }

    fun markRelaunch(pkg: String, now: Long) {
        val o = observation(pkg)
        update(pkg, Observation(o.everNotification, o.everMediaSession, o.lastAliveAt, now, o.relaunchCount + 1))
    }

    fun markNotificationSeen(pkg: String) {
        val o = observation(pkg)
        if (o.everNotification) return
        update(pkg, Observation(true, o.everMediaSession, o.lastAliveAt, o.lastRelaunchAt, o.relaunchCount))
    }

    fun markMediaSeen(pkg: String) {
        val o = observation(pkg)
        if (o.everMediaSession) return
        update(pkg, Observation(o.everNotification, true, o.lastAliveAt, o.lastRelaunchAt, o.relaunchCount))
    }

    var tickMs: Long
        get() = prefs.getLong(KEY_TICK, DEFAULT_TICK_MS)
        set(value) = prefs.edit().putLong(KEY_TICK, value).apply()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Empty means fall back to the URL baked in at build time. */
    var updateUrl: String
        get() = prefs.getString(KEY_UPDATE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_UPDATE_URL, value.trim()).apply()

    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_UPDATE_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_CHECKED, value).apply()

    var updateChecksEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_ENABLED, value).apply()

    fun oemStepDone(id: String): Boolean = prefs.getBoolean("oem_$id", false)

    fun setOemStepDone(id: String, done: Boolean) = prefs.edit().putBoolean("oem_$id", done).apply()

    private fun update(pkg: String, o: Observation) {
        observations[pkg] = o
        writeObservations()
    }

    private fun readApps(): MutableMap<String, WatchedApp> {
        val out = LinkedHashMap<String, WatchedApp>()
        val array = JSONArray(prefs.getString(KEY_APPS, "[]") ?: "[]")
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val pkg = o.optString("pkg").takeIf { it.isNotEmpty() } ?: continue
            out[pkg] = WatchedApp(
                pkg = pkg,
                mode = runCatching { Mode.valueOf(o.optString("mode")) }.getOrDefault(Mode.ON_SIGNAL),
                graceMs = o.optLong("grace", WatchedApp.DEFAULT_GRACE_MS),
                sweepMs = o.optLong("sweep", WatchedApp.DEFAULT_SWEEP_MS),
            )
        }
        return out
    }

    private fun writeApps() {
        val array = JSONArray()
        apps.values.forEach {
            array.put(
                JSONObject()
                    .put("pkg", it.pkg)
                    .put("mode", it.mode.name)
                    .put("grace", it.graceMs)
                    .put("sweep", it.sweepMs)
            )
        }
        prefs.edit().putString(KEY_APPS, array.toString()).apply()
    }

    private fun readObservations(): MutableMap<String, Observation> {
        val out = HashMap<String, Observation>()
        val root = JSONObject(prefs.getString(KEY_OBSERVATIONS, "{}") ?: "{}")
        for (pkg in root.keys()) {
            val o = root.optJSONObject(pkg) ?: continue
            out[pkg] = Observation(
                everNotification = o.optBoolean("notif"),
                everMediaSession = o.optBoolean("media"),
                lastAliveAt = o.optLong("alive"),
                lastRelaunchAt = o.optLong("relaunch"),
                relaunchCount = o.optInt("count"),
            )
        }
        return out
    }

    private fun writeObservations() {
        val root = JSONObject()
        observations.forEach { (pkg, o) ->
            root.put(
                pkg,
                JSONObject()
                    .put("notif", o.everNotification)
                    .put("media", o.everMediaSession)
                    .put("alive", o.lastAliveAt)
                    .put("relaunch", o.lastRelaunchAt)
                    .put("count", o.relaunchCount)
            )
        }
        prefs.edit().putString(KEY_OBSERVATIONS, root.toString()).apply()
    }

    companion object {
        const val DEFAULT_TICK_MS = 20_000L
        private const val BACKOFF_RESET_MS = 300_000L
        private const val KEY_APPS = "apps"
        private const val KEY_OBSERVATIONS = "observations"
        private const val KEY_TICK = "tick_ms"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_UPDATE_URL = "update_url"
        private const val KEY_UPDATE_CHECKED = "update_checked_at"
        private const val KEY_UPDATE_ENABLED = "update_checks_enabled"

        @Volatile
        private var instance: Watchlist? = null

        fun of(ctx: Context): Watchlist = instance ?: synchronized(this) {
            instance ?: Watchlist(
                ctx.applicationContext.getSharedPreferences("watchlist", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
