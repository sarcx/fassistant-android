package dev.todor.fassistant.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import dev.todor.fassistant.R
import dev.todor.fassistant.Watchlist

private class OemStep(val id: String, val labelRes: Int, val intent: Intent)

/**
 * Phone makers add their own app killers behind proprietary Settings screens with no ADB
 * equivalent. Skipping these is the most common reason a watchdog quietly stops working, so the
 * app deep-links to whichever ones this phone actually has and keeps a checklist.
 */
class OemSetupActivity : Activity() {

    private lateinit var watchlist: Watchlist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchlist = Watchlist.of(this)
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        setContentView(scrolling(buildScreen()))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildScreen(): LinearLayout = verticalLayout().apply {
        addView(body(getString(R.string.oem_intro)))
        val steps = availableSteps()
        if (steps.isEmpty()) {
            addView(spacer(12))
            addView(caption(getString(R.string.oem_none)))
        }
        steps.forEach { addView(stepRow(it)) }
    }

    private fun stepRow(step: OemStep): View = verticalLayout(padding = 0).apply {
        addView(divider())
        addView(body(getString(step.labelRes)))
        addView(caption(step.intent.component?.packageName ?: step.intent.action.orEmpty()))
        addView(button(getString(R.string.oem_open)) { runCatching { startActivity(step.intent) } })
        addView(
            CheckBox(this@OemSetupActivity).apply {
                text = getString(R.string.oem_mark_done)
                isChecked = watchlist.oemStepDone(step.id)
                setOnCheckedChangeListener { _, checked -> watchlist.setOemStepDone(step.id, checked) }
            }
        )
    }

    private fun availableSteps(): List<OemStep> {
        val candidates = VENDOR_SCREENS.map { (id, labelRes, component) ->
            OemStep(id, labelRes, Intent().setComponent(ComponentName.unflattenFromString(component)!!))
        } + OemStep(
            "app-details",
            R.string.oem_app_details,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        return candidates.filter { packageManager.resolveActivity(it.intent, 0) != null }
    }

    private companion object {
        val VENDOR_SCREENS = listOf(
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
}
