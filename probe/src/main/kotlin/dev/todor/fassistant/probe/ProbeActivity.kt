package dev.todor.fassistant.probe

import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Throwaway. Answers the four questions the plan left marked unknown, on the phone rather than in
 * a document. Delete this module once the answers are written down.
 */
class ProbeActivity : Activity() {

    private lateinit var target: EditText
    private lateinit var output: TextView
    private var results = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        target = EditText(this).apply {
            hint = getString(R.string.probe_target_hint)
            setText("com.android.settings")
            setSingleLine()
        }
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@ProbeActivity).apply { text = getString(R.string.probe_intro) })
            addView(target)
            addView(Button(this@ProbeActivity).apply {
                text = getString(R.string.probe_run)
                setOnClickListener { run() }
            })
            addView(Button(this@ProbeActivity).apply {
                text = getString(R.string.probe_copy)
                setOnClickListener { copy() }
            })
            addView(TextView(this@ProbeActivity).apply { text = getString(R.string.probe_grants_heading) })
            addView(Button(this@ProbeActivity).apply {
                text = getString(R.string.probe_grant_usage)
                setOnClickListener { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            })
            addView(Button(this@ProbeActivity).apply {
                text = getString(R.string.probe_grant_overlay)
                setOnClickListener {
                    open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            })
            addView(Button(this@ProbeActivity).apply {
                text = getString(R.string.probe_open_target)
                setOnClickListener { openTargetSettings() }
            })
            addView(TextView(this@ProbeActivity).apply { text = getString(R.string.probe_force_stop_hint) })
            addView(output)
        }
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun run() {
        val pkg = target.text.toString().trim()
        val lines = ArrayList<String>()

        lines += "device: ${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "android: ${Build.VERSION.RELEASE} (api ${Build.VERSION.SDK_INT})"
        lines += "probe targetSdk: ${applicationInfo.targetSdkVersion}"
        lines += "target package: $pkg"
        lines += ""

        lines += processEnumeration(pkg)
        lines += serviceEnumeration(pkg)
        lines += procScan()
        lines += stoppedFlag(pkg)
        lines += errorState()
        lines += usageAccess(pkg)
        lines += overlay()

        results = lines.joinToString("\n")
        output.text = results
    }

    private fun processEnumeration(pkg: String): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = try {
            am.runningAppProcesses
        } catch (e: SecurityException) {
            return "getRunningAppProcesses: SecurityException"
        } ?: return "getRunningAppProcesses: null"

        val packages = processes.flatMap { it.pkgList?.toList() ?: emptyList() }.toSet()
        val others = packages.filter { it != packageName }
        return buildString {
            append("getRunningAppProcesses: ${processes.size} processes, ${packages.size} packages\n")
            append("  sees other apps: ${others.isNotEmpty()}\n")
            append("  target visible: ${packages.contains(pkg)}\n")
            append("  sample: ${others.take(5)}")
        }
    }

    @Suppress("DEPRECATION")
    private fun serviceEnumeration(pkg: String): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = try {
            am.getRunningServices(200)
        } catch (e: SecurityException) {
            return "getRunningServices: SecurityException"
        } ?: return "getRunningServices: null"

        val packages = services.mapNotNull { it.service?.packageName }.toSet()
        return buildString {
            append("getRunningServices: ${services.size} services, ${packages.size} packages\n")
            append("  sees other apps: ${packages.any { it != packageName }}\n")
            append("  target visible: ${packages.contains(pkg)}")
        }
    }

    private fun procScan(): String {
        val entries = File("/proc").listFiles()?.filter { it.name.toIntOrNull() != null } ?: emptyList()
        val readable = entries.count { File(it, "cmdline").canRead() }
        return "/proc scan: ${entries.size} pid entries, $readable readable"
    }

    private fun stoppedFlag(pkg: String): String {
        val bit = 1 shl 21
        val own = flagsOf(packageName)
        val other = flagsOf(pkg)
        return buildString {
            append("stopped bit (1 shl 21):\n")
            append("  own flags: ${own?.let { "0x%08x".format(it) }} bit set: ${own?.let { it and bit != 0 }}\n")
            append("  target flags: ${other?.let { "0x%08x".format(it) }} bit set: ${other?.let { it and bit != 0 }}\n")
            append("  trustworthy: ${own != null && own and bit == 0}")
        }
    }

    private fun flagsOf(pkg: String): Int? = try {
        packageManager.getApplicationInfo(pkg, 0).flags
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun errorState(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val states = try {
            am.processesInErrorState
        } catch (e: SecurityException) {
            return "getProcessesInErrorState: SecurityException"
        }
        return "getProcessesInErrorState: ${states?.size ?: 0} entries (null means none)"
    }

    @Suppress("DEPRECATION")
    private fun usageAccess(pkg: String): String {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return "usage access: not granted (mode $mode) — tap \"grant usage access\" above, then run again"
        }
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60 * 60 * 1000L, now + 1000)
        val event = UsageEvents.Event()
        var lastForeground: String? = null
        var targetSeenAt = 0L
        var count = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            count++
            lastForeground = event.packageName
            if (event.packageName == pkg) targetSeenAt = event.timeStamp
        }
        return buildString {
            append("usage access: granted, $count foreground events in the last hour\n")
            append("  last foreground app: $lastForeground\n")
            append("  target last foreground: ${if (targetSeenAt == 0L) "not in window" else "${(now - targetSeenAt) / 1000}s ago"}")
        }
    }

    private fun overlay(): String =
        "draw over other apps: ${Settings.canDrawOverlays(this)}"

    private fun open(intent: Intent) {
        if (!runCatching { startActivity(intent) }.isSuccess) {
            Toast.makeText(this, R.string.probe_no_screen, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTargetSettings() = open(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${target.text.toString().trim()}"),
        )
    )

    private fun copy() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("probe", results))
        Toast.makeText(this, R.string.probe_copied, Toast.LENGTH_SHORT).show()
    }
}
