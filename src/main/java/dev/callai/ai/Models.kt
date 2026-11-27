package dev.callai.ai

import dev.callai.data.Settings
import dev.callai.data.Store
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google retires model names every few months. When Kivi's model is gone, Kivi asks Gemini which models exist and
 * moves to the newest one that does the same job, so a retirement doesn't need a new build.
 */
object Models {
    const val LIVE = "models/gemini-3.8-live-extended-thinking"
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    /** The voice model Kivi uses now: the built-in one, or the replacement it picked. */
    fun live() = Settings.now.liveModel.ifBlank { LIVE }

    /** Gemini says the model doesn't exist (or no longer takes this kind of request). */
    fun gone(reason: String) = Regex("(model|models/).*(not found|not supported|does not exist|is deprecated)|is not found", RegexOption.IGNORE_CASE)
        .containsMatchIn(reason)

    /** The newest model offering [method] ("bidiGenerateContent", "generateContent") that's most like [like]. Null offline. */
    fun pick(method: String, like: String): String? = runCatching {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000&key=${Settings.now.key}"
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            val models = JSONObject(r.body!!.string()).getJSONArray("models")
            (0 until models.length()).map(models::getJSONObject)
                .filter { m -> m.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.getString(it) == method } } == true }
                .map { it.getString("name") }
        }
    }.onFailure { Store.log("Couldn't list Gemini's models: ${it.message}") }.getOrNull()?.let { bestModel(it, like) }
}

/**
 * ponytail: a name heuristic. Same kind first ("live-extended-thinking" for "gemini-3.8-live-extended-thinking"), then
 * the same first word ("live"), then the highest version; previews and experiments only when nothing else fits.
 * If Google changes its naming entirely, the 8 am self-check reports the failure.
 */
fun bestModel(names: List<String>, like: String): String? {
    val kind = like.substringAfterLast('/').replace(Regex("^gemini-[\\d.]+-?"), "")
    fun version(n: String) = Regex("gemini-(\\d+)(?:\\.(\\d+))?").find(n)?.destructured?.let { (a, b) -> a.toInt() * 100 + (b.toIntOrNull() ?: 0) } ?: -1
    return names.filter { version(it) >= 0 }.maxWithOrNull(
        compareBy<String>({ kind.isNotEmpty() && it.endsWith(kind) }, { it.contains(kind.substringBefore('-')) },
            { !Regex("preview|exp", RegexOption.IGNORE_CASE).containsMatchIn(it) }, { version(it) }))
}
