package dev.todor.fassistant.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import dev.todor.fassistant.BuildConfig
import dev.todor.fassistant.DeathLog
import dev.todor.fassistant.Grants
import dev.todor.fassistant.R
import dev.todor.fassistant.Watchlist
import dev.todor.fassistant.update.CheckResult
import dev.todor.fassistant.update.ReadyUpdate
import dev.todor.fassistant.update.UpdateChecker
import dev.todor.fassistant.update.UpdateInstaller
import java.util.concurrent.Executors

class UpdateActivity : Activity() {

    private lateinit var watchlist: Watchlist
    private lateinit var checker: UpdateChecker

    private var status: CharSequence = ""
    private var ready: ReadyUpdate? = null
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchlist = Watchlist.of(this)
        checker = UpdateChecker(this, watchlist, DeathLog(this))
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render() {
        setContentView(scrolling(buildScreen()))
    }

    private fun buildScreen(): LinearLayout = verticalLayout().apply {
        addView(title(getString(R.string.update_title)))
        addView(caption(getString(R.string.main_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)))

        val source = checker.manifestUrl()
        addView(spacer(14))
        if (source.isBlank()) {
            addView(body(getString(R.string.update_not_configured)))
        } else {
            addView(caption(getString(R.string.update_source, source)))
        }

        if (status.isNotEmpty()) {
            addView(spacer(12))
            addView(body(status))
        }

        val available = ready
        if (available != null) {
            addView(spacer(6))
            if (available.manifest.notes.isNotBlank()) {
                addView(
                    caption(available.manifest.notes).apply {
                        typeface = Typeface.MONOSPACE
                        textSize = 12f
                    }
                )
                addView(spacer(8))
            }
            addView(
                button(getString(R.string.update_install, available.manifest.versionName)) {
                    UpdateInstaller.install(this@UpdateActivity, available)
                }
            )
            if (!Grants.canInstallPackages(this@UpdateActivity)) {
                addView(caption(getString(R.string.update_needs_unknown_sources)))
                Grants.unknownSourcesIntent(this@UpdateActivity)?.let { intent ->
                    addView(button(getString(R.string.grant_fix)) { runCatching { startActivity(intent) } })
                }
            }
        }

        addView(spacer(14))
        addView(
            button(getString(if (checking) R.string.update_checking else R.string.update_check_now)) {
                if (!checking) checkNow()
            }
        )

        addView(heading(getString(R.string.update_source_heading)))
        val field = EditText(this@UpdateActivity).apply {
            setText(watchlist.updateUrl)
            hint = getString(R.string.update_url_hint)
            setSingleLine()
        }
        addView(field)
        addView(
            button(getString(R.string.update_save_url)) {
                watchlist.updateUrl = field.text.toString()
                ready = null
                status = getString(R.string.update_url_saved)
                render()
            }
        )
        addView(spacer(6))
        addView(caption(getString(R.string.update_url_why)))
    }

    private fun checkNow() {
        checking = true
        status = getString(R.string.update_checking)
        render()
        Executors.newSingleThreadExecutor().execute {
            val result = checker.check(System.currentTimeMillis())
            Handler(Looper.getMainLooper()).post {
                checking = false
                when (result) {
                    is CheckResult.Ready -> {
                        ready = result.update
                        status = getString(R.string.update_found, result.update.manifest.versionName)
                    }

                    CheckResult.UpToDate -> {
                        ready = null
                        status = getString(R.string.update_up_to_date)
                    }

                    CheckResult.NotConfigured -> status = getString(R.string.update_not_configured)
                    is CheckResult.Failed -> status = getString(R.string.update_failed, result.reason)
                }
                render()
            }
        }
    }
}
