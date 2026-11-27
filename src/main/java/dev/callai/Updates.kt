package dev.callai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.callai.data.Store
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** New versions are published as GitHub releases of this repo, with the APK attached. */
object Updates {
    private const val LATEST = "https://api.github.com/repos/Trafalgar-2006/kivi-call_ai/releases/latest"
    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()

    data class Release(val tag: String, val page: String, val apk: String?)

    /** The newer release, or null when this is the latest (or nothing is released yet). Throws offline. */
    fun newer(current: String): Release? =
        http.newCall(Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (r.code == 404) return null
            val release = JSONObject(r.body!!.string())
            val tag = release.optString("tag_name")
            if (!isNewer(tag, current)) return null
            val assets = release.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
            Release(tag, release.optString("html_url"), apk)
        }

    /**
     * Downloads the release's APK into Android's installer. Android then asks you once to confirm the update
     * (it only accepts an APK signed with the same key, so nobody else's build can replace Kivi). Throws offline.
     */
    fun install(ctx: Context, apk: String) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(ctx.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            http.newCall(Request.Builder().url(apk).build()).execute().use { r ->
                check(r.isSuccessful) { "download failed (HTTP ${r.code})" }
                session.openWrite("kivi.apk", 0, r.body!!.contentLength()).use { out -> r.body!!.byteStream().copyTo(out); session.fsync(out) }
            }
            session.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender)
        }
    }
}

/** Android's installer reports back here: show its "Update Kivi?" confirmation, or log why it failed. Not exported. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Unit // Kivi restarts on the new version
            else -> Store.log("Update didn't install: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"}")
        }
    }
}

/** "v0.10" is newer than "0.9": versions compare part by part as numbers, and a leading "v" is ignored. */
fun isNewer(tag: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(tag); val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
