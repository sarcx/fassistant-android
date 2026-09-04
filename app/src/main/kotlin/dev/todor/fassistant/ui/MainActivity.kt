package dev.todor.fassistant.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import dev.todor.fassistant.Badge
import dev.todor.fassistant.BuildConfig
import dev.todor.fassistant.Detectabilities
import dev.todor.fassistant.GrantState
import dev.todor.fassistant.Grants
import dev.todor.fassistant.Protection
import dev.todor.fassistant.R
import dev.todor.fassistant.WatchdogService
import dev.todor.fassistant.Watchlist

class MainActivity : Activity() {

    private lateinit var watchlist: Watchlist

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchlist = Watchlist.of(this)
        if (watchlist.enabled) WatchdogService.start(this, "app opened")
    }

    override fun onResume() {
        super.onResume()
        setContentView(scrolling(buildScreen()))
    }

    private fun buildScreen(): LinearLayout = verticalLayout().apply {
        addView(title(getString(R.string.app_name)))
        addView(caption(getString(R.string.main_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)))
        addView(spacer(12))

        val statusRes = if (WatchdogService.running) R.string.main_status_running else R.string.main_status_stopped
        addView(body(getString(statusRes)))
        addView(caption(getString(R.string.main_last_tick, formatAgo(WatchdogService.lastTickAt))))
        addView(spacer(4))
        val detectionRes = if (WatchdogService.processChecksWork) {
            R.string.main_enumeration_exact
        } else {
            R.string.main_enumeration_inferred
        }
        addView(caption(getString(detectionRes)))

        addView(spacer(12))
        val toggleRes = if (watchlist.enabled) R.string.main_stop else R.string.main_start
        addView(button(getString(toggleRes)) { toggleWatchdog() })
        addView(button(getString(R.string.main_check_now)) { WatchdogService.start(this@MainActivity, "manual"); recreate() })

        addView(heading(getString(R.string.main_permissions_heading)))
        Grants.all(this@MainActivity).forEach { addView(grantRow(it)) }

        addView(heading(getString(R.string.main_watched_heading)))
        val watched = watchlist.all()
        if (watched.isEmpty()) {
            addView(caption(getString(R.string.main_watched_empty)))
        } else {
            watched.forEach { addView(watchedRow(it.pkg)) }
        }
        if (watched.isNotEmpty()) {
            val restricted = watched.count { !Protection.batteryUnrestricted(this@MainActivity, it.pkg) }
            addView(spacer(6))
            addView(
                if (restricted == 0) {
                    caption(getString(R.string.protect_all_clear))
                } else {
                    body(getString(R.string.protect_restricted_count, restricted, watched.size))
                }
            )
            addView(button(getString(R.string.protect_title)) { open(ProtectionActivity::class.java) })
        }

        addView(spacer(8))
        addView(button(getString(R.string.main_add_apps)) { open(PickerActivity::class.java) })

        addView(heading(getString(R.string.main_oem_setup)))
        addView(button(getString(R.string.main_oem_setup)) { open(OemSetupActivity::class.java) })
        addView(button(getString(R.string.main_updates)) { open(UpdateActivity::class.java) })
        addView(button(getString(R.string.main_view_log)) { open(LogActivity::class.java) })
    }

    private fun grantRow(grant: GrantState): View = horizontalLayout().apply {
        val text = verticalLayout(padding = 0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(body(getString(grant.titleRes)))
            addView(caption(getString(grant.whyRes)))
        }
        addView(text)
        if (grant.granted) {
            addView(badge(getString(R.string.grant_granted), badgeColor(Badge.STRONG)))
        } else {
            addView(badge(getString(R.string.grant_missing), badgeColor(Badge.NO_LAUNCHER)))
            grant.intent?.let { intent ->
                addView(
                    button(getString(R.string.grant_fix)) { startSafely(intent) }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    }
                )
            }
        }
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun watchedRow(pkg: String): View = horizontalLayout().apply {
        val detectability = Detectabilities.of(this@MainActivity, pkg, watchlist, WatchdogService.processChecksWork)
        val observation = watchlist.observation(pkg)
        val text = verticalLayout(padding = 0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(body(labelOf(pkg)))
            addView(caption(getString(R.string.detail_last_alive, formatAgo(observation.lastAliveAt))))
        }
        addView(text)
        addView(
            badge(
                getString(Detectabilities.labelRes(detectability.badge)),
                badgeColor(detectability.badge),
            )
        )
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        setOnClickListener { openDetail(pkg) }
    }

    private fun toggleWatchdog() {
        watchlist.enabled = !watchlist.enabled
        if (watchlist.enabled) WatchdogService.start(this, "switched on") else WatchdogService.stop(this)
        recreate()
    }

    private fun labelOf(pkg: String): CharSequence = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
    }.getOrDefault(pkg)

    private fun open(target: Class<*>) = startActivity(Intent(this, target))

    private fun openDetail(pkg: String) =
        startActivity(Intent(this, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_PACKAGE, pkg))

    private fun startSafely(intent: Intent) {
        runCatching { startActivity(intent) }
    }
}
