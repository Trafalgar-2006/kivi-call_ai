package dev.callai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** New versions are published as GitHub releases of this repo, with the APK attached. */
object Updates {
    private const val LATEST = "https://api.github.com/repos/Trafalgar-2006/kivi-call_ai/releases/latest"
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    /** The newer release's page, or null when this is the latest (or nothing is released yet). Throws offline. */
    fun newer(current: String): String? =
        http.newCall(Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (r.code == 404) return null
            val release = JSONObject(r.body!!.string())
            if (isNewer(release.optString("tag_name"), current)) release.optString("html_url") else null
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
