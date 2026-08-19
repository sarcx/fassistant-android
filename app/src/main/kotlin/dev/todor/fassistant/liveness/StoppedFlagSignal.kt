package dev.todor.fassistant.liveness

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.todor.fassistant.WatchedApp

/**
 * Separates a user force-stop from a memory kill. The platform sets a "stopped" bit on a package
 * the user force-stops and clears it when the app next launches.
 *
 * [ApplicationInfo.flags] is public but the bit itself is hidden, so the position is hardcoded.
 * Rather than trust that blindly, [refresh] sanity-checks it against this app's own flags — we are
 * demonstrably running, so if our own bit reads as set then it does not mean what we think.
 */
class StoppedFlagSignal(private val ctx: Context) : LivenessSignal {

    override val id = "stopped-flag"

    private var trusted = false

    override fun available() = trusted

    override fun refresh(now: Long) {
        val ownFlags = flagsOf(ctx.packageName) ?: return
        trusted = ownFlags and FLAG_STOPPED == 0
    }

    override fun check(app: WatchedApp, now: Long): Liveness {
        if (!trusted) return Liveness.UNKNOWN
        val flags = flagsOf(app.pkg) ?: return Liveness.UNKNOWN
        return if (flags and FLAG_STOPPED != 0) Liveness.DEAD else Liveness.UNKNOWN
    }

    private fun flagsOf(pkg: String): Int? = try {
        ctx.packageManager.getApplicationInfo(pkg, 0).flags
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val FLAG_STOPPED = 1 shl 21
    }
}
