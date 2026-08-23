package dev.todor.fassistant

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

class GrantState(
    val titleRes: Int,
    val whyRes: Int,
    val granted: Boolean,
    val intent: Intent?,
)

object Grants {

    fun hasOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = try {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } catch (e: SecurityException) {
            return false
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        // MODE_DEFAULT defers to the permission, which only a privileged build would hold.
        return mode == AppOpsManager.MODE_DEFAULT &&
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationAccess(ctx: Context): Boolean {
        // Settings.Secure.ENABLED_NOTIFICATION_LISTENERS is hidden; the key name is stable.
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?: return false
        return enabled.split(':').any { it.startsWith("${ctx.packageName}/") }
    }

    fun ignoringBatteryOptimizations(ctx: Context): Boolean {
        val power = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Needed to install our own updates. Always true below Android 8, which had no per-app switch. */
    fun canInstallPackages(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return ctx.packageManager.canRequestPackageInstalls()
    }

    fun unknownSourcesIntent(ctx: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
    }

    fun all(ctx: Context): List<GrantState> = listOfNotNull(
        GrantState(
            R.string.grant_overlay,
            R.string.grant_overlay_why,
            hasOverlay(ctx),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
        ),
        GrantState(
            R.string.grant_notifications,
            R.string.grant_notifications_why,
            hasNotificationAccess(ctx),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        ),
        GrantState(
            R.string.grant_usage,
            R.string.grant_usage_why,
            hasUsageAccess(ctx),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        ),
        GrantState(
            R.string.grant_battery,
            R.string.grant_battery_why,
            ignoringBatteryOptimizations(ctx),
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        ),
        unknownSourcesIntent(ctx)?.let {
            GrantState(R.string.grant_install, R.string.grant_install_why, canInstallPackages(ctx), it)
        },
    )
}
