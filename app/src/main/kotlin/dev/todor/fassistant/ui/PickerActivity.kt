package dev.todor.fassistant.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import dev.todor.fassistant.Badge
import dev.todor.fassistant.Detectabilities
import dev.todor.fassistant.Mode
import dev.todor.fassistant.R
import dev.todor.fassistant.WatchdogService
import dev.todor.fassistant.Watchlist
import java.util.concurrent.Executors

private class AppEntry(val pkg: String, val label: String)

class PickerActivity : Activity() {

    private lateinit var watchlist: Watchlist
    private lateinit var icons: IconCache
    private lateinit var listView: ListView
    private lateinit var status: TextView

    private var everything: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var query = ""
    private var showEverything = false

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            row(shown[position])
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        watchlist = Watchlist.of(this)
        icons = IconCache(this)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val search = EditText(this).apply {
            hint = getString(R.string.picker_search)
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim()?.lowercase().orEmpty()
                    applyFilter()
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            })
        }

        status = caption(getString(R.string.picker_loading))
        listView = ListView(this).apply {
            adapter = this@PickerActivity.adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            setOnItemClickListener { _, _, position, _ -> openDetail(shown[position].pkg) }
        }

        val includeHidden = CheckBox(this).apply {
            text = getString(R.string.picker_show_all)
            isChecked = showEverything
            setOnCheckedChangeListener { _, checked ->
                showEverything = checked
                loadApps()
            }
        }

        setContentView(
            verticalLayout().apply {
                addView(search)
                addView(includeHidden)
                addView(status)
                addView(listView)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadApps() {
        Executors.newSingleThreadExecutor().execute {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val withIcons = runCatching { packageManager.queryIntentActivities(launcherIntent, 0) }
                .getOrDefault(emptyList())
                .mapNotNull { it.activityInfo?.applicationInfo }

            // Plugins and helper apps often have no launcher activity, so they never appear in the
            // list above even though their process is exactly the kind worth watching.
            val rest = if (!showEverything) {
                emptyList()
            } else {
                runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
            }

            val entries = (withIcons + rest)
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { AppEntry(it.packageName, packageManager.getApplicationLabel(it).toString()) }
                .sortedWith(compareByDescending<AppEntry> { watchlist.isWatched(it.pkg) }.thenBy { it.label.lowercase() })

            Handler(Looper.getMainLooper()).post {
                everything = entries
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        shown = if (query.isEmpty()) {
            everything
        } else {
            everything.filter { it.label.lowercase().contains(query) || it.pkg.contains(query) }
        }
        status.text = getString(R.string.picker_watched_count, watchlist.size())
        adapter.notifyDataSetChanged()
    }

    private fun row(entry: AppEntry): View {
        val detectability = Detectabilities.of(this, entry.pkg, watchlist, WatchdogService.processChecksWork)
        val selectable = detectability.badge != Badge.NO_LAUNCHER

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) }
            alpha = if (selectable) 1f else 0.4f
        }
        icons.load(entry.pkg, icon)

        val names = verticalLayout(padding = 0).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(body(entry.label).apply { alpha = if (selectable) 1f else 0.4f })
            addView(caption(entry.pkg).apply { alpha = if (selectable) 1f else 0.4f })
        }

        val trailing = verticalLayout(padding = 0).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.END
            addView(
                badge(getString(Detectabilities.labelRes(detectability.badge)), badgeColor(detectability.badge))
                    .apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
            )
            addView(caption(trailingLabel(entry.pkg, selectable)).apply { gravity = Gravity.END })
        }

        return horizontalLayout().apply {
            setPadding(dp(4), dp(10), dp(4), dp(10))
            addView(icon)
            addView(names)
            addView(trailing)
        }
    }

    private fun trailingLabel(pkg: String, selectable: Boolean): String {
        if (!selectable) return getString(R.string.picker_not_selectable)
        val watched = watchlist.get(pkg) ?: return getString(R.string.picker_tap_to_watch)
        return getString(
            when (watched.mode) {
                Mode.ON_SIGNAL -> R.string.detail_mode_on_signal
                Mode.KEEP_IN_FRONT -> R.string.detail_mode_keep_in_front
                Mode.SWEEP -> R.string.detail_mode_sweep
            }
        )
    }

    private fun openDetail(pkg: String) =
        startActivity(Intent(this, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_PACKAGE, pkg))
}
