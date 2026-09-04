package dev.todor.fassistant.ui

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import dev.todor.fassistant.R
import dev.todor.fassistant.Watchlist

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

    private fun stepRow(step: OemScreen): View = verticalLayout(padding = 0).apply {
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

    private fun availableSteps(): List<OemScreen> = OemScreens.available(this)
}
