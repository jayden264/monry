package com.monry.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val UPDATE_REPO = "jayden264/monry"

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** "v1.2.10" > "1.2.9": compares dotted numeric parts, ignoring a leading v and any suffix. */
fun isNewerVersion(remote: String, local: String): Boolean {
    fun parts(s: String) = s.trim().removePrefix("v").substringBefore('-')
        .split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

object Updater {
    /** Latest release newer than the installed version, or null. Network errors surface as exceptions. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode == 404) return@withContext null // no release published yet
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            if (!isNewerVersion(tag, installed)) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return@withContext UpdateInfo(tag.removePrefix("v"), json.optString("body"), a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "Monry-${info.version}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection // follows GitHub's CDN redirect
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    /** Returns false when the user must first allow installs from this app (settings page is opened). */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
