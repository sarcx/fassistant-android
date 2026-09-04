package dev.todor.fassistant

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import dev.todor.fassistant.liveness.Ladder
import dev.todor.fassistant.liveness.Liveness
import dev.todor.fassistant.update.CheckResult
import dev.todor.fassistant.update.UpdateChecker
import java.util.concurrent.Executors

class WatchdogService : Service() {

    private lateinit var watchlist: Watchlist
    private lateinit var ladder: Ladder
    private lateinit var relauncher: Relauncher
    private lateinit var log: DeathLog
    private lateinit var updateChecker: UpdateChecker

    private val handler = Handler(Looper.getMainLooper())
    private val awaitingProof = HashMap<String, Long>()
    private val background = Executors.newSingleThreadExecutor()

    @Volatile
    private var updateCheckRunning = false

    private val tickRunnable = Runnable { tick("timer") }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = tick("screen")
    }

    override fun onCreate() {
        super.onCreate()
        watchlist = Watchlist.of(this)
        log = DeathLog(this)
        ladder = Ladder(this, watchlist)
        relauncher = Relauncher(this, watchlist, log)
        updateChecker = UpdateChecker(this, watchlist, log)

        Notifications.ensureChannel(this)
        runCatching {
            startForeground(
                Notifications.STATUS_ID,
                Notifications.status(this, watchlist.size(), getString(R.string.notif_text_all_alive)),
            )
        }.onFailure { log.line("could not go foreground: ${it.javaClass.simpleName}") }

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )

        running = true
        log.line("service started")
        log.logOwnExits(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!watchlist.enabled) {
            log.line("watchdog switched off, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        tick(intent?.getStringExtra(EXTRA_REASON) ?: "start")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        log.line("swiped out of recents, coming back")
        TickAlarm.schedule(this, RESTART_DELAY_MS)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tickRunnable)
        background.shutdownNow()
        runCatching { unregisterReceiver(screenReceiver) }
        log.line("service destroyed")
        if (watchlist.enabled) TickAlarm.schedule(this, RESTART_DELAY_MS)
        super.onDestroy()
    }

    private fun tick(reason: String) {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fassistant:tick")
        runCatching { wakeLock?.acquire(WAKE_LOCK_MS) }
        try {
            sweep(reason)
        } catch (e: Exception) {
            log.line("tick failed: ${e.javaClass.simpleName} ${e.message}")
        } finally {
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
            scheduleNext()
        }
    }

    private fun sweep(reason: String) {
        val now = System.currentTimeMillis()
        lastTickAt = now
        lastTickReason = reason

        ladder.refresh(now)
        processChecksWork = ladder.process.available()

        val wasInFront = ladder.foreground.topPackage()
        var reopened: String? = null
        var wantsToStayInFront = false

        for (app in watchlist.all()) {
            proveOrWarn(app, now)
            val verdict = ladder.verdict(app, now)
            val didReopen = when (verdict.liveness) {
                Liveness.ALIVE -> {
                    watchlist.markAlive(app.pkg, now)
                    Notifications.clearBlocked(this, app.pkg)
                    false
                }

                Liveness.DEAD -> relauncher.relaunch(app, now, verdict.source)
                Liveness.UNKNOWN -> applyPolicy(app, now)
            }
            if (didReopen) {
                reopened = app.pkg
                awaitingProof[app.pkg] = now
                if (app.mode == Mode.KEEP_IN_FRONT) wantsToStayInFront = true
            }
        }

        if (reopened != null && !wantsToStayInFront) restoreScreen(wasInFront, reopened)
        showStatus(reopened)
        maybeCheckForUpdate(now)
    }

    /**
     * Puts the screen back where it was. Another app's activity cannot be started without coming to
     * the front, so the only way not to be left staring at it is to bring back whatever was there
     * before — once per tick, however many apps were reopened.
     *
     * Skipped entirely for an app in "keep it in front" mode, since that mode wants the opposite.
     */
    private fun restoreScreen(wasInFront: String?, reopened: String) {
        if (!watchlist.returnToPreviousApp) return

        val back = wasInFront
            ?.takeIf { it != reopened }
            ?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        // Long enough for the reopened app to get through onCreate, short enough not to be a
        // visible detour. Its process stays alive once started, whether or not it is on screen.
        handler.postDelayed({
            if (!runCatching { startActivity(back) }.isSuccess) {
                log.line("could not return the screen to ${wasInFront ?: "home"}")
            }
        }, SETTLE_MS)
    }

    /**
     * Once a day, off the tick thread. Networking must not happen on the main thread, and a slow
     * server must never delay a relaunch.
     */
    private fun maybeCheckForUpdate(now: Long) {
        if (!watchlist.updateChecksEnabled || updateCheckRunning) return
        if (!updateChecker.dueForCheck(now)) return

        updateCheckRunning = true
        background.execute {
            val result = runCatching { updateChecker.check(System.currentTimeMillis()) }.getOrNull()
            updateCheckRunning = false
            if (result is CheckResult.Ready) {
                Notifications.postUpdateAvailable(this, result.update.manifest.versionName)
            }
        }
    }

    private fun applyPolicy(app: WatchedApp, now: Long): Boolean = when (app.mode) {
        Mode.ON_SIGNAL -> false

        Mode.KEEP_IN_FRONT -> {
            val isTop = ladder.foreground.topPackage() == app.pkg
            val awayFor = now - ladder.foreground.lastResumedAt(app.pkg)
            if (!isTop && awayFor > app.graceMs) relauncher.relaunch(app, now, "not in front") else false
        }

        Mode.SWEEP -> {
            val observation = watchlist.observation(app.pkg)
            val quietSince = maxOf(observation.lastAliveAt, observation.lastRelaunchAt)
            if (now - quietSince > app.sweepMs) relauncher.relaunch(app, now, "timer") else false
        }
    }

    /**
     * A blocked background launch throws nothing — it just does not happen. The only way to notice
     * is that the app never comes to the front afterwards.
     */
    private fun proveOrWarn(app: WatchedApp, now: Long) {
        val launchedAt = awaitingProof[app.pkg] ?: return
        if (now - launchedAt < PROOF_WINDOW_MS) return
        awaitingProof.remove(app.pkg)

        if (!ladder.foreground.available()) return
        if (ladder.foreground.lastResumedAt(app.pkg) >= launchedAt) return
        if (Grants.hasOverlay(this)) return

        log.line("reopen of ${app.pkg} was blocked by the system")
        Notifications.postBlocked(this, app.pkg, labelOf(app.pkg))
    }

    private fun showStatus(reopened: String?) {
        val detail = if (reopened == null) {
            getString(R.string.notif_text_all_alive)
        } else {
            getString(R.string.notif_text_relaunched, labelOf(reopened))
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.notify(Notifications.STATUS_ID, Notifications.status(this, watchlist.size(), detail))
        }
    }

    private fun labelOf(pkg: String): CharSequence = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
    }.getOrDefault(pkg)

    private fun scheduleNext() {
        val interval = watchlist.tickMs
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, interval)
        TickAlarm.schedule(this, interval)
        TickJob.ensure(this)
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val WAKE_LOCK_MS = 10_000L
        private const val PROOF_WINDOW_MS = 30_000L
        private const val RESTART_DELAY_MS = 1_000L
        private const val SETTLE_MS = 900L

        @Volatile
        var running = false
            private set

        @Volatile
        var lastTickAt = 0L
            private set

        @Volatile
        var lastTickReason = ""
            private set

        @Volatile
        var processChecksWork = false
            private set

        fun start(ctx: Context, reason: String) {
            val intent = Intent(ctx, WatchdogService::class.java).putExtra(EXTRA_REASON, reason)
            try {
                ctx.startService(intent)
            } catch (e: Exception) {
                // Only reachable if a future Android applies background-start limits to us anyway.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { ctx.startForegroundService(intent) }
                }
            }
        }

        fun stop(ctx: Context) {
            TickAlarm.cancel(ctx)
            TickJob.cancel(ctx)
            ctx.stopService(Intent(ctx, WatchdogService::class.java))
        }
    }
}
