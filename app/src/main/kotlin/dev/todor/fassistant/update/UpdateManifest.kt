package dev.todor.fassistant.update

import org.json.JSONObject
import java.io.File

class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
) {
    companion object {
        fun parse(text: String): UpdateManifest {
            val json = JSONObject(text)
            return UpdateManifest(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", json.getInt("versionCode").toString()),
                apkUrl = json.getString("apkUrl"),
                sha256 = json.optString("sha256").lowercase(),
                notes = json.optString("notes"),
            )
        }
    }
}

/** A newer build that has been downloaded and had its signature checked. Ready to install. */
class ReadyUpdate(val manifest: UpdateManifest, val apk: File)

sealed class CheckResult {
    class Ready(val update: ReadyUpdate) : CheckResult()
    object UpToDate : CheckResult()
    object NotConfigured : CheckResult()
    class Failed(val reason: String) : CheckResult()
}
