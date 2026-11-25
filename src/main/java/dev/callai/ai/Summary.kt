package dev.callai.ai

import dev.callai.data.Settings
import dev.callai.data.Store
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

val CALLER_LANGUAGES = listOf("English", "Telugu", "Hindi", "Kannada", "Tamil", "Malayalam", "Other")

/**
 * The note writer's callback time ("2026-11-25T18:00") as a moment, or 0 when it's empty, unreadable, already past,
 * or more than 30 days away (a misheard date).
 */
fun parseCallback(text: String, now: Long): Long {
    val t = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).apply { isLenient = false }.parse(text.trim())?.time }
        .getOrNull() ?: return 0
    return if (t > now && t - now < 30 * 86_400_000L) t else 0
}

/**
 * Writes the call note after the call, from the whole transcript, with a fast free text model.
 * Replaces the live model's take_note tool, which the extended-thinking model can't call.
 */
object Summary {
    // The free tier often answers 503 "high demand" for a few seconds; try the next model, then go round again.
    private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-4-hydrogen")
    private const val GIVE_UP_MS = 60_000L
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    private fun s(description: String) = JSONObject().put("type", "STRING").put("description", description)

    /** Fields: caller_name, reason, category, reply, emergency, language, callback_at. Null if no model answered. */
    fun write(ownerName: String, contactName: String?, transcript: String): JSONObject? {
        if (transcript.isBlank()) return null
        val owner = ownerName.trim().substringBefore(' ').ifBlank { "the owner" }
        val prompt = """
            Write the call note for $owner from this phone call, which Kivi (an AI assistant) answered for $owner.
            The call ended on ${SimpleDateFormat("EEEE yyyy-MM-dd 'at' HH:mm", Locale.ENGLISH).format(Date())} (India time).
            ${contactName?.let { "The caller is saved in $owner's contacts as $it." } ?: "The caller is not in $owner's contacts."}
            Caller lines come from speech recognition over a phone line and may contain mistakes; Kivi's lines show what Kivi understood.

            Transcript:
            $transcript
        """.trimIndent()
        val schema = JSONObject().put("type", "OBJECT").put("properties", JSONObject()
            .put("caller_name", s("Who called, with their organisation if they gave one"))
            .put("reason", s("One short line in English: why they called and what they want, including any callback time they asked for"))
            .put("category", s("needs_you if $owner should act or call back, handled if nothing more is needed, blocked for spam, sales, scams or abuse")
                .put("enum", JSONArray(listOf("needs_you", "handled", "blocked"))))
            .put("reply", s("A short, polite text message from $owner to the caller, written in the same language and script as the caller's own lines in the transcript (if unsure, English). It says $owner got the message and will get back to them, calling $owner by name, never he, she, his or her. Promise no time and don't accept or decline anything for $owner. No links or numbers. Empty for blocked calls"))
            .put("emergency", s("One line if the caller reported a real emergency (an accident, someone hurt or in hospital, a death, a fire, danger), otherwise empty. Always empty for scams: an \"emergency\" that asks for money, codes or bank details is a scam"))
            .put("language", s("The language the caller mostly spoke").put("enum", JSONArray(CALLER_LANGUAGES)))
            .put("callback_at", s("If the caller asked $owner to call back at a particular time, that time as YYYY-MM-DDTHH:MM in India time, counting from when the call ended; otherwise empty")))
            .put("required", JSONArray(listOf("caller_name", "reason", "category", "reply", "emergency", "language", "callback_at")))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("responseSchema", schema))
            .toString().toRequestBody("application/json".toMediaType())
        val deadline = System.currentTimeMillis() + GIVE_UP_MS
        var round = 0
        while (System.currentTimeMillis() < deadline) {
            for (model in MODELS) {
                if (System.currentTimeMillis() >= deadline) break
                runCatching {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${Settings.now.key}"
                    http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                        val text = JSONObject(r.body!!.string()).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        return JSONObject(text)
                    }
                }
            }
            Thread.sleep(minOf(2_000L shl round++, 10_000L)) // 2 s, 4 s, 8 s, then every 10 s
        }
        Store.log("Note writer: every model was busy for a minute, used Kivi's own summary")
        return null
    }

    /** For Diagnostics: null when the note writer works, otherwise what went wrong. */
    fun check(): String? =
        if (write("Test", null, "Kivi: Hello! Who's calling?\nCaller: Ravi from Blue Dart, the parcel is at the gate.\nKivi: Got it, I'll tell them. Bye bye!") != null) null
        else "the note writer didn't answer (see recent activity)"
}
