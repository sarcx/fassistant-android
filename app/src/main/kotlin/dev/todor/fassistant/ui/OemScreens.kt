package dev.todor.fassistant.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.todor.fassistant.R

class OemScreen(val id: String, val labelRes: Int, val intent: Intent)

/**
 * Phone makers add their own app killers behind proprietary Settings screens with no API and no ADB
 * equivalent. Skipping them is the most common reason a watchdog quietly stops working.
 *
 * These screens are global lists rather than per-app, so nothing here can be read back or set — the
 * best the app can do is open the right screen and keep a checklist. Shared between the app's own
 * setup screen and the per-watched-app protection screen.
 */
object OemScreens {

    fun available(ctx: Context): List<OemScreen> =
        (vendorScreens() + appInfo(ctx, ctx.packageName, "app-details"))
            .filter { ctx.packageManager.resolveActivity(it.intent, 0) != null }

    /** The one per-app screen that exists everywhere. Battery limits are reachable from here. */
    fun appInfo(ctx: Context, pkg: String, id: String = "app-details-$pkg") = OemScreen(
        id,
        R.string.oem_app_details,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
    )

    private fun vendorScreens(): List<OemScreen> = VENDOR_COMPONENTS.mapNotNull { (id, labelRes, component) ->
        ComponentName.unflattenFromString(component)?.let {
            OemScreen(id, labelRes, Intent().setComponent(it))
        }
    }

    private val VENDOR_COMPONENTS = listOf(
        Triple("miui-autostart", R.string.oem_autostart, "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Triple("huawei-startup", R.string.oem_autostart, "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"),
        Triple("huawei-protect", R.string.oem_protected, "com.huawei.systemmanager/.optimize.process.ProtectActivity"),
        Triple("oppo-startup", R.string.oem_autostart, "com.coloros.safecenter/.startupapp.StartupAppListActivity"),
        Triple("oppo-startup-alt", R.string.oem_autostart, "com.coloros.safecenter/.permission.startup.StartupAppListActivity"),
        Triple("vivo-startup", R.string.oem_autostart, "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity"),
        Triple("vivo-whitelist", R.string.oem_battery, "com.iqoo.secure/.ui.phoneoptimize.AddWhiteListActivity"),
        Triple("oneplus-chain", R.string.oem_autostart, "com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity"),
        Triple("letv-autoboot", R.string.oem_autostart, "com.letv.android.letvsafe/.AutobootManageActivity"),
        Triple("asus-manager", R.string.oem_protected, "com.asus.mobilemanager/.MainActivity"),
        Triple("samsung-battery", R.string.oem_battery, "com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity"),
    )
}
