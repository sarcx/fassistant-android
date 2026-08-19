package dev.todor.fassistant.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import dev.todor.fassistant.DeathLog
import dev.todor.fassistant.R

class LogActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        val contents = DeathLog(this).read().ifBlank { getString(R.string.log_empty) }
        setContentView(
            scrolling(
                verticalLayout().apply {
                    addView(button(getString(R.string.log_share)) { copy(contents) })
                    addView(spacer(8))
                    addView(
                        caption(contents).apply {
                            typeface = Typeface.MONOSPACE
                            setTextIsSelectable(true)
                        }
                    )
                }
            )
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun copy(contents: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.log_title), contents))
        Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show()
    }
}
