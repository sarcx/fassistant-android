package dev.todor.fassistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * How a package can be brought back to life.
 *
 * Not everything worth watching has an icon. A plugin — the kind that lets a remote-control app
 * drive the touchscreen, say — often ships with no launcher activity at all, so the obvious
 * "open the app" route does not exist for it. Starting any exported component of a package starts
 * its process, which is what actually matters here.
 */
sealed class StartMethod {

    /** Comes to the front, so the screen may need putting back afterwards. */
    class Screen(val intent: Intent) : StartMethod()

    /** Starts the process without anything appearing. Better, where it is available. */
    class Background(val intent: Intent) : StartMethod()

    /** Touching a content provider also starts the process, and is the quietest way in. */
    class Probe(val uri: Uri) : StartMethod()

    object None : StartMethod()
}

object Starter {

    fun resolve(ctx: Context, pkg: String): StartMethod {
        val pm = ctx.packageManager

        launcherIntent(pm, pkg)?.let { return StartMethod.Screen(it) }
        exportedActivity(pm, pkg)?.let { return StartMethod.Screen(it) }
        exportedService(pm, pkg)?.let { return StartMethod.Background(it) }
        exportedProvider(pm, pkg)?.let { return StartMethod.Probe(it) }
        return StartMethod.None
    }

    fun start(ctx: Context, method: StartMethod): Boolean = when (method) {
        is StartMethod.Screen -> runCatching { ctx.startActivity(method.intent) }.isSuccess

        // Allowed from the background at this targetSdk, which is part of why it is kept low.
        is StartMethod.Background -> runCatching { ctx.startService(method.intent) != null }
            .getOrDefault(false)

        is StartMethod.Probe -> runCatching {
            ctx.contentResolver.query(method.uri, null, null, null, null)?.close()
            true
        }.getOrDefault(false)

        StartMethod.None -> false
    }

    fun descriptionRes(method: StartMethod): Int = when (method) {
        is StartMethod.Screen -> R.string.start_via_screen
        is StartMethod.Background -> R.string.start_via_service
        is StartMethod.Probe -> R.string.start_via_provider
        StartMethod.None -> R.string.start_via_nothing
    }

    private fun launcherIntent(pm: PackageManager, pkg: String): Intent? =
        (pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    private fun exportedActivity(pm: PackageManager, pkg: String): Intent? =
        runCatching { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES) }.getOrNull()
            ?.activities
            ?.firstOrNull { it.exported && it.enabled }
            ?.let {
                Intent()
                    .setComponent(ComponentName(pkg, it.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

    private fun exportedService(pm: PackageManager, pkg: String): Intent? =
        runCatching { pm.getPackageInfo(pkg, PackageManager.GET_SERVICES) }.getOrNull()
            ?.services
            ?.firstOrNull { it.exported && it.enabled }
            ?.let { Intent().setComponent(ComponentName(pkg, it.name)) }

    private fun exportedProvider(pm: PackageManager, pkg: String): Uri? =
        runCatching { pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS) }.getOrNull()
            ?.providers
            ?.firstOrNull { it.exported && it.enabled && !it.authority.isNullOrEmpty() }
            ?.let { Uri.parse("content://${it.authority.split(';').first()}") }
}
