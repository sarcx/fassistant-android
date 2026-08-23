package dev.todor.fassistant.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.todor.fassistant.BuildConfig
import dev.todor.fassistant.DeathLog
import dev.todor.fassistant.Watchlist
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches the update manifest, and if it names a newer build, downloads and validates it.
 *
 * Two checks stand between a download and an install prompt: the SHA-256 from the manifest, which
 * catches corruption, and the signing certificate, which must match this app's own. The second is
 * the one that matters — Android would refuse a mismatched update anyway, so checking here only
 * means the user is never asked to approve something that could not work.
 */
class UpdateChecker(
    private val ctx: Context,
    private val watchlist: Watchlist,
    private val log: DeathLog,
) {

    fun manifestUrl(): String = watchlist.updateUrl.ifBlank { BuildConfig.UPDATE_MANIFEST_URL }

    fun dueForCheck(now: Long): Boolean = now - watchlist.lastUpdateCheckAt > CHECK_EVERY_MS

    fun check(now: Long): CheckResult {
        val source = manifestUrl()
        if (source.isBlank()) return CheckResult.NotConfigured

        watchlist.lastUpdateCheckAt = now

        val manifest = try {
            UpdateManifest.parse(fetchText(source))
        } catch (e: Exception) {
            log.line("update check failed: ${e.javaClass.simpleName} ${e.message}")
            return CheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }

        if (manifest.versionCode <= BuildConfig.VERSION_CODE) return CheckResult.UpToDate

        return try {
            CheckResult.Ready(download(manifest, source))
        } catch (e: Exception) {
            log.line("update download failed: ${e.javaClass.simpleName} ${e.message}")
            CheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun download(manifest: UpdateManifest, manifestSource: String): ReadyUpdate {
        val target = File(ctx.filesDir, "update-${manifest.versionCode}.apk")

        // Resolved against the manifest's own address, so moving hosts never means editing the
        // manifest. Deliberately the configured URL, not wherever a redirect landed.
        val apkUrl = URL(URL(manifestSource), manifest.apkUrl).toString()

        if (!target.isFile || (manifest.sha256.isNotEmpty() && sha256(target) != manifest.sha256)) {
            val partial = File(ctx.filesDir, "${target.name}.part")
            openStream(apkUrl).use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IllegalStateException("could not move the downloaded file into place")
            }
        }

        if (manifest.sha256.isNotEmpty()) {
            val actual = sha256(target)
            if (actual != manifest.sha256) {
                target.delete()
                throw IllegalStateException("checksum did not match")
            }
        }

        if (!signedLikeUs(target)) {
            target.delete()
            throw IllegalStateException("that APK is signed by someone else")
        }

        log.line("update ${manifest.versionName} (${manifest.versionCode}) downloaded and verified")
        return ReadyUpdate(manifest, target)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun signedLikeUs(apk: File): Boolean {
        val pm = ctx.packageManager
        val ours: List<ByteArray>
        val theirs: List<ByteArray>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flag = PackageManager.GET_SIGNING_CERTIFICATES
            ours = pm.getPackageInfo(ctx.packageName, flag).signingInfo
                ?.apkContentsSigners?.map { it.toByteArray() } ?: return false
            theirs = pm.getPackageArchiveInfo(apk.absolutePath, flag)?.signingInfo
                ?.apkContentsSigners?.map { it.toByteArray() } ?: return false
        } else {
            val flag = PackageManager.GET_SIGNATURES
            ours = pm.getPackageInfo(ctx.packageName, flag).signatures?.map { it.toByteArray() }
                ?: return false
            theirs = pm.getPackageArchiveInfo(apk.absolutePath, flag)?.signatures?.map { it.toByteArray() }
                ?: return false
        }
        if (ours.isEmpty() || ours.size != theirs.size) return false
        return ours.all { mine -> theirs.any { it.contentEquals(mine) } }
    }

    private fun fetchText(url: String): String = openStream(url).use { it.readBytes().decodeToString() }

    /**
     * Follows redirects by hand. A release URL that always points at the newest build is a chain of
     * them, and HttpURLConnection stops following as soon as the scheme changes.
     */
    private fun openStream(url: String): InputStream {
        var target = url
        repeat(MAX_REDIRECTS) {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "*/*")
            }
            when (val status = connection.responseCode) {
                in 200..299 -> return connection.inputStream
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    target = URL(URL(target), location ?: throw IllegalStateException("redirect with no target")).toString()
                }

                else -> {
                    connection.disconnect()
                    throw IllegalStateException("server said $status")
                }
            }
        }
        throw IllegalStateException("too many redirects")
    }

    private companion object {
        const val CHECK_EVERY_MS = 24 * 60 * 60 * 1000L
        const val TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5
    }
}
