package dev.callai.data

import android.content.Context
import android.content.SharedPreferences
import dev.callai.calls.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** 32-year-old customer-service voice from India: "clear, medium-pitch, and friendly". Verified on gemini-3.8-live-extended-thinking. */
const val DEFAULT_VOICE = "en-in-csagent-5"

/** How hard Kivi thinks before each reply: quicker replies, or better understanding of unclear speech. */
val THINKING = listOf("low" to "Quick", "medium" to "Balanced", "high" to "Deep")

data class Prefs(
    val config: Config = Config(),
    val owner: String = "",
    val status: String = "",       // what saved contacts may hear, e.g. "In a meeting till 5"
    val about: String = "",        // what Kivi knows about you: family, work, routine; strangers hear none of it
    val people: Map<String, String> = emptyMap(), // number -> your instruction for that person ("say I'll call after 8")
    val key: String = "",          // Gemini API key (sealed in the hardware keystore on disk)
    val voice: String = DEFAULT_VOICE, // an Indian English voice from Google's voice library
    val voices: Map<String, String> = emptyMap(), // language -> voice for callers who spoke it last time (Telugu -> te-in-...)
    val thinking: String = "medium",
    val mainLanguage: String = "Telugu", // what most callers speak; Kivi greets with it and leans on it when unsure
    val answerAfterSec: Int = 5,
    val keepDays: Int = 90,        // 0 = keep forever
    val onboarded: Boolean = false,
    val autoReply: Boolean = true, // text Kivi's drafted reply to real callers after the call
    val useMessages: Boolean = true, // a saved contact's recent WhatsApp/SMS notifications give Kivi context
    val digest: Boolean = true,    // 9 pm summary of the day's calls
    val record: Boolean = false,   // keep the caller's audio, and tell callers the call is recorded
    val lock: Boolean = false,     // fingerprint or screen lock to open Kivi
    val flyosUrl: String = "",     // FlyOS on your laptop, over Tailscale; empty = not connected
    val flyosToken: String = "",   // sealed on disk, like the key
)

/** All settings as one observable value; the screens, the call watcher and the tile all read the same state. */
object Settings {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Prefs())
    val state: StateFlow<Prefs> = _state
    val now: Prefs get() = _state.value

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("kivi", Context.MODE_PRIVATE)
        _state.value = read()
        if (sp.contains("key")) write(now) // older builds kept the key in plain text: seal it and drop the plain copy
    }

    /** Told after every change (the home-screen widget redraws). */
    @Volatile var onChange: (() -> Unit)? = null

    fun update(change: (Prefs) -> Prefs) {
        synchronized(this) {
            val p = change(_state.value)
            write(p)
            _state.value = p
        }
        onChange?.let { runCatching(it) }
    }

    fun config(change: (Config) -> Config) = update { it.copy(config = change(it.config)) }

    private fun map(name: String): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(name, "{}").orEmpty())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun read() = Prefs(
        config = Config(
            strangers = sp.getBoolean("strangers", false),
            sleepUntil = sp.getLong("sleepUntil", 0),
            sleepSchedule = sp.getBoolean("sleepSchedule", false),
            bedtime = sp.getInt("bedtime", 23 * 60),
            wake = sp.getInt("wake", 6 * 60 + 30),
            busyUntil = sp.getLong("busyUntil", 0),
            busyCalendar = sp.getBoolean("busyCalendar", false),
            driveUntil = sp.getLong("driveUntil", 0),
            carDevice = sp.getString("carDevice", "").orEmpty(),
            awayUntil = sp.getLong("awayUntil", 0),
            ringThrough = sp.getStringSet("ringThrough", emptySet()).orEmpty().toSet(),
        ),
        owner = sp.getString("owner", "").orEmpty(),
        status = sp.getString("status", "").orEmpty(),
        about = sp.getString("about", "").orEmpty(),
        people = map("people"),
        key = Secret.open(sp.getString("keySealed", "").orEmpty()).ifEmpty { sp.getString("key", "").orEmpty() },
        // Earlier builds used Gemini's generic voices; anything that isn't an Indian voice moves to the default.
        voice = sp.getString("voice", "").orEmpty().takeIf { it.startsWith("en-in-") } ?: DEFAULT_VOICE,
        voices = map("voices"),
        thinking = sp.getString("thinking", "medium").orEmpty().takeIf { t -> THINKING.any { it.first == t } } ?: "medium",
        mainLanguage = sp.getString("mainLanguage", "Telugu").orEmpty().ifBlank { "Telugu" },
        answerAfterSec = sp.getInt("answerAfterSec", 5),
        keepDays = sp.getInt("keepDays", 90),
        onboarded = sp.getBoolean("onboarded", false),
        autoReply = sp.getBoolean("autoReply", true),
        useMessages = sp.getBoolean("useMessages", true),
        digest = sp.getBoolean("digest", true),
        record = sp.getBoolean("record", false),
        lock = sp.getBoolean("lock", false),
        flyosUrl = sp.getString("flyosUrl", "").orEmpty(),
        flyosToken = Secret.open(sp.getString("flyosTokenSealed", "").orEmpty()).ifEmpty { sp.getString("flyosToken", "").orEmpty() },
    )

    // If the keystore ever fails, keep the secret as before rather than lose it (or crash at startup).
    private fun SharedPreferences.Editor.secret(name: String, value: String): SharedPreferences.Editor =
        runCatching { Secret.seal(value) }.fold({ putString("${name}Sealed", it).remove(name) }, { putString(name, value) })

    private fun write(p: Prefs) {
        val c = p.config
        sp.edit()
            .putBoolean("strangers", c.strangers)
            .putLong("sleepUntil", c.sleepUntil).putBoolean("sleepSchedule", c.sleepSchedule)
            .putInt("bedtime", c.bedtime).putInt("wake", c.wake)
            .putLong("busyUntil", c.busyUntil).putBoolean("busyCalendar", c.busyCalendar)
            .putLong("driveUntil", c.driveUntil).putString("carDevice", c.carDevice)
            .putLong("awayUntil", c.awayUntil)
            .putStringSet("ringThrough", c.ringThrough)
            .putString("owner", p.owner).putString("status", p.status).putString("about", p.about)
            .putString("people", JSONObject(p.people).toString())
            .secret("key", p.key)
            .putString("voice", p.voice).putString("voices", JSONObject(p.voices).toString())
            .putString("thinking", p.thinking).putString("mainLanguage", p.mainLanguage)
            .putInt("answerAfterSec", p.answerAfterSec).putInt("keepDays", p.keepDays)
            .putBoolean("onboarded", p.onboarded).putBoolean("autoReply", p.autoReply)
            .putBoolean("useMessages", p.useMessages).putBoolean("digest", p.digest)
            .putBoolean("record", p.record).putBoolean("lock", p.lock)
            .putString("flyosUrl", p.flyosUrl).secret("flyosToken", p.flyosToken)
            .apply()
    }
}
