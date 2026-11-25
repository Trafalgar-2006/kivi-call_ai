package dev.callai.ai

import dev.callai.data.CallNote
import dev.callai.data.Settings
import dev.callai.data.Store
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * The optional link to FlyOS on your laptop, reached over Tailscale (Settings > FlyOS). Kivi sends each call note in,
 * and while a call rings asks for a short brief on the caller. The contract FlyOS serves (README, "FlyOS link"):
 *   POST {url}/api/kivi/call            JSON note, header "Authorization: Bearer {token}" -> 2xx
 *   GET  {url}/api/kivi/brief?number=…  same header -> 200 with plain text (only what the caller may hear), or 204
 * Off until both the address and the token are set; any failure just means no brief.
 */
object FlyOS {
    private val http = OkHttpClient.Builder().callTimeout(1500, TimeUnit.MILLISECONDS).build()

    fun configured() = Settings.now.flyosUrl.isNotBlank() && Settings.now.flyosToken.isNotBlank()

    private fun base() = Settings.now.flyosUrl.trim().trimEnd('/')
    private fun Request.Builder.auth() = header("Authorization", "Bearer ${Settings.now.flyosToken}")

    fun brief(number: String): String? = if (!configured() || number.isBlank()) null else runCatching {
        val url = base() + "/api/kivi/brief?number=" + URLEncoder.encode(number, "UTF-8")
        http.newCall(Request.Builder().url(url).auth().build()).execute().use { r ->
            if (r.code == 200) r.body?.string()?.trim()?.take(1200)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()

    fun send(n: CallNote) {
        if (!configured()) return
        val json = JSONObject().put("time", n.time).put("number", n.number).put("name", n.name).put("reason", n.reason)
            .put("category", n.category).put("reply", n.reply).put("transcript", n.transcript).put("duration_ms", n.durationMs)
            .put("language", n.language).put("callback_at", n.callbackAt).put("trust", "tainted") // a caller's words: untrusted
        runCatching {
            http.newCall(Request.Builder().url(base() + "/api/kivi/call").auth()
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                if (!r.isSuccessful) Store.log("FlyOS didn't take the call note (HTTP ${r.code})")
            }
        }.onFailure { Store.log("FlyOS unreachable: ${it.javaClass.simpleName}") }
    }
}
