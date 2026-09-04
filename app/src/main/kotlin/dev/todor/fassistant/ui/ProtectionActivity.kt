package dev.todor.fassistant.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import dev.todor.fassistant.Badge
import dev.todor.fassistant.Protection
import dev.todor.fassistant.R
import dev.todor.fassistant.Watchlist

/**
 * The same OS-level settings, applied across every watched app, because doing it one app at a time
 * from each detail screen is how one gets forgotten.
 *
 * Battery status is read live per app. The manufacturer screens are global lists with no API, so
 * those are a checklist you tick yourself.
 */
class ProtectionActivity : Activity() {

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
        addView(body(getString(R.string.protect_intro)))

        val watched = watchlist.all()
        if (watched.isEmpty()) {
            addView(spacer(12))
            addView(caption(getString(R.string.main_watched_empty)))
            return@apply
        }

        val restricted = watched.count { !Protection.batteryUnrestricted(this@ProtectionActivity, it.pkg) }
        addView(spacer(10))
        addView(
            if (restricted == 0) {
                caption(getString(R.string.protect_all_clear))
            } else {
                body(getString(R.string.protect_restricted_count, restricted, watched.size))
            }
        )

        addView(heading(getString(R.string.protect_battery_heading)))
        addView(caption(getString(R.string.protect_battery_why)))
        watched.forEach { addView(batteryRow(it.pkg)) }

        Protection.batteryListIntent(this@ProtectionActivity)?.let { intent ->
            addView(spacer(8))
            addView(button(getString(R.string.protect_open_battery_list)) { open(intent) })
        }

        addView(heading(getString(R.string.protect_manufacturer_heading)))
        addView(caption(getString(R.string.protect_manufacturer_why)))
        OemScreens.available(this@ProtectionActivity)
            .filter { it.id != "app-details" }
            .forEach { screen ->
                addView(spacer(8))
                addView(button(getString(screen.labelRes)) { open(screen.intent) })
            }
        addView(spacer(10))
        watched.forEach { addView(confirmRow(it.pkg)) }

        addView(heading(getString(R.string.protect_not_needed_heading)))
        addView(caption(getString(R.string.protect_not_needed_why)))
    }

    private fun batteryRow(pkg: String): View = horizontalLayout().apply {
        val unrestricted = Protection.batteryUnrestricted(this@ProtectionActivity, pkg)
        addView(
            verticalLayout(padding = 0).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(body(labelOf(pkg)))
                addView(caption(pkg))
            }
        )
        addView(
            badge(
                getString(if (unrestricted) R.string.protect_unrestricted else R.string.protect_restricted),
                badgeColor(if (unrestricted) Badge.STRONG else Badge.WEAK),
            )
        )
        if (!unrestricted) {
            Protection.batteryFixIntents(this@ProtectionActivity, pkg).firstOrNull()?.let { intent ->
                addView(
                    button(getString(R.string.grant_fix)) { open(intent) }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    }
                )
            }
        }
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun confirmRow(pkg: String): View = CheckBox(this).apply {
        text = getString(R.string.protect_confirmed_for, labelOf(pkg))
        isChecked = watchlist.protectionConfirmed(pkg)
        setOnCheckedChangeListener { _, checked -> watchlist.setProtectionConfirmed(pkg, checked) }
    }

    private fun labelOf(pkg: String): CharSequence = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
    }.getOrDefault(pkg)

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
    }
}
