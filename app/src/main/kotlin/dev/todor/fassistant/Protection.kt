package dev.todor.fassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * The OS-level settings that make a *monitored* app less likely to be killed.
 *
 * Only one of the permissions this app holds for itself is relevant to another app's survival: the
 * battery-optimisation exemption. Overlay, usage access and notification access govern what
 * Fassistant may observe or start, not whether the target stays resident, so they are deliberately
 * absent here. The rest is manufacturer autostart screens, which have no API at all.
 */
object Protection {

    /** Readable for any package, unlike the request, which is why status can be shown per app. */
    fun batteryUnrestricted(ctx: Context, pkg: String): Boolean {
        val power = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(pkg) }.getOrDefault(false)
    }

    /**
     * Ordered by how directly each one lands on the setting. The first asks the system to allowlist
     * a named package; whether it accepts a package other than the caller's is not documented
     * either way, so nothing depends on it — the status above is read back afterwards, and the
     * app-details screen is always offered as the route that certainly works.
     */
    fun batteryFixIntents(ctx: Context, pkg: String): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    ).filter { ctx.packageManager.resolveActivity(it, 0) != null }

    fun batteryListIntent(ctx: Context): Intent? =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .takeIf { ctx.packageManager.resolveActivity(it, 0) != null }
}
