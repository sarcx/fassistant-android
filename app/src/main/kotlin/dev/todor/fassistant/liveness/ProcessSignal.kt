package dev.todor.fassistant.liveness

import android.app.ActivityManager
import android.content.Context
import dev.todor.fassistant.WatchedApp

/**
 * Exact liveness, where the platform still allows it. Android restricts
 * [ActivityManager.getRunningAppProcesses] to the caller's own process, but the restriction is
 * documented against the level an app *targets*, and this app targets 25 — so on some devices
 * the full list comes back. Whether it does is decided at runtime, never assumed.
 */
class ProcessSignal(private val ctx: Context) : LivenessSignal {

    override val id = "process"

    private var enumerationWorks = false
    private var running: Set<String> = emptySet()

    override fun available() = enumerationWorks

    override fun refresh(now: Long) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val processes = try {
            am.runningAppProcesses
        } catch (e: SecurityException) {
            null
        } ?: return

        val packages = HashSet<String>()
        processes.forEach { process -> process.pkgList?.forEach { packages.add(it) } }
        running = packages

        // Latched on: a moment when nothing else happens to be running must not be read
        // as the platform having closed the door.
        if (packages.any { it != ctx.packageName }) enumerationWorks = true
    }

    override fun check(app: WatchedApp, now: Long): Liveness {
        if (!enumerationWorks) return Liveness.UNKNOWN
        return if (running.contains(app.pkg)) Liveness.ALIVE else Liveness.DEAD
    }
}
