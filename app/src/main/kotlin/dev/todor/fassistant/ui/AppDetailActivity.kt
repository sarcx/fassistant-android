package dev.todor.fassistant.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import dev.todor.fassistant.Badge
import dev.todor.fassistant.Detectabilities
import dev.todor.fassistant.Mode
import dev.todor.fassistant.Protection
import dev.todor.fassistant.R
import dev.todor.fassistant.WatchdogService
import dev.todor.fassistant.WatchedApp
import dev.todor.fassistant.Watchlist

class AppDetailActivity : Activity() {

    private lateinit var watchlist: Watchlist
    private lateinit var pkg: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchlist = Watchlist.of(this)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        actionBar?.setDisplayHomeAsUpEnabled(true)
        title = labelOf(pkg)
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
        addView(caption(pkg))

        if (!isInstalled()) {
            addView(spacer(12))
            addView(body(getString(R.string.detail_uninstalled)))
            addView(spacer(12))
            addView(button(getString(R.string.detail_stop_watching)) { stopWatching() })
            return@apply
        }

        val detectability = Detectabilities.of(this@AppDetailActivity, pkg, watchlist, WatchdogService.processChecksWork)
        addView(spacer(10))
        addView(
            badge(getString(Detectabilities.labelRes(detectability.badge)), badgeColor(detectability.badge))
                .apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
        )
        addView(spacer(6))
        addView(caption(getString(detectability.whyRes)))

        if (detectability.badge == Badge.NO_LAUNCHER) return@apply

        val watched = watchlist.get(pkg)
        addView(spacer(14))
        if (watched == null) {
            addView(button(getString(R.string.detail_watch)) { startWatching(detectability.badge) })
            return@apply
        }
        addView(button(getString(R.string.detail_stop_watching)) { stopWatching() })

        addView(heading(getString(R.string.detail_mode_heading)))
        addView(modePicker(watched))

        addView(spacer(10))
        addView(caption(getString(R.string.detail_grace, formatDuration(watched.graceMs))))
        addView(durationSpinner(GRACE_CHOICES, watched.graceMs) { chosen ->
            save(watched.copy(graceMs = chosen))
        })

        if (watched.mode == Mode.SWEEP) {
            addView(spacer(10))
            addView(caption(getString(R.string.detail_sweep_every, formatDuration(watched.sweepMs))))
            addView(durationSpinner(SWEEP_CHOICES, watched.sweepMs) { chosen ->
                save(watched.copy(sweepMs = chosen))
            })
        }

        addView(heading(getString(R.string.protect_heading)))
        addView(protectionRows())

        val observation = watchlist.observation(pkg)
        addView(heading(getString(R.string.detail_history_heading)))
        addView(caption(getString(R.string.detail_last_alive, formatAgo(observation.lastAliveAt))))
        addView(caption(getString(R.string.detail_last_relaunch, formatAgo(observation.lastRelaunchAt))))
        addView(caption(getString(R.string.detail_relaunch_count, observation.relaunchCount)))
    }

    /**
     * The OS settings that keep *this* app alive, as opposed to the settings above, which decide
     * what Fassistant does about it once it dies.
     */
    private fun protectionRows(): View = verticalLayout(padding = 0).apply {
        val unrestricted = Protection.batteryUnrestricted(this@AppDetailActivity, pkg)
        addView(
            badge(
                getString(if (unrestricted) R.string.protect_unrestricted else R.string.protect_restricted),
                badgeColor(if (unrestricted) Badge.STRONG else Badge.WEAK),
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
        )
        addView(spacer(6))
        addView(caption(getString(R.string.protect_battery_why)))
        if (!unrestricted) {
            Protection.batteryFixIntents(this@AppDetailActivity, pkg).firstOrNull()?.let { intent ->
                addView(button(getString(R.string.protect_remove_limits)) { runCatching { startActivity(intent) } })
            }
        }
        addView(spacer(8))
        addView(
            button(getString(R.string.protect_open_app_settings)) {
                runCatching { startActivity(OemScreens.appInfo(this@AppDetailActivity, pkg).intent) }
            }
        )
        addView(spacer(8))
        addView(button(getString(R.string.protect_all_apps)) {
            startActivity(Intent(this@AppDetailActivity, ProtectionActivity::class.java))
        })
    }

    private fun modePicker(watched: WatchedApp): View = RadioGroup(this).apply {
        orientation = RadioGroup.VERTICAL
        Mode.entries.forEach { mode ->
            addView(
                RadioButton(this@AppDetailActivity).apply {
                    // Ids start at 1: RadioGroup treats 0 as ambiguous with "nothing checked".
                    id = mode.ordinal + 1
                    text = getString(modeLabel(mode))
                    isChecked = watched.mode == mode
                }
            )
            addView(caption(getString(modeWhy(mode))).apply { setPadding(dp(32), 0, 0, dp(8)) })
        }
        setOnCheckedChangeListener { _, checkedId ->
            val chosen = Mode.entries.getOrNull(checkedId - 1) ?: return@setOnCheckedChangeListener
            if (chosen != watched.mode) save(watched.copy(mode = chosen))
        }
    }

    private fun durationSpinner(choices: LongArray, current: Long, onPick: (Long) -> Unit): Spinner {
        val labels = choices.map { formatDuration(it) }
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@AppDetailActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            val index = choices.indexOfFirst { it == current }
            if (index >= 0) setSelection(index)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (choices[position] != current) onPick(choices[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun startWatching(badge: Badge) {
        val mode = Detectabilities.defaultMode(badge)
        watchlist.put(WatchedApp(pkg, mode))
        WatchdogService.start(this, "app added")
        recreate()
    }

    private fun stopWatching() {
        watchlist.remove(pkg)
        recreate()
    }

    private fun save(app: WatchedApp) {
        watchlist.put(app)
        WatchdogService.start(this, "settings changed")
        recreate()
    }

    private fun isInstalled() = runCatching { packageManager.getApplicationInfo(pkg, 0) }.isSuccess

    private fun labelOf(pkg: String): CharSequence = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
    }.getOrDefault(pkg)

    private fun modeLabel(mode: Mode) = when (mode) {
        Mode.ON_SIGNAL -> R.string.detail_mode_on_signal
        Mode.KEEP_IN_FRONT -> R.string.detail_mode_keep_in_front
        Mode.SWEEP -> R.string.detail_mode_sweep
    }

    private fun modeWhy(mode: Mode) = when (mode) {
        Mode.ON_SIGNAL -> R.string.detail_mode_on_signal_why
        Mode.KEEP_IN_FRONT -> R.string.detail_mode_keep_in_front_why
        Mode.SWEEP -> R.string.detail_mode_sweep_why
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        private val GRACE_CHOICES = longArrayOf(10_000, 30_000, 60_000, 120_000, 300_000)
        private val SWEEP_CHOICES = longArrayOf(60_000, 120_000, 300_000, 900_000, 1_800_000)
    }
}
