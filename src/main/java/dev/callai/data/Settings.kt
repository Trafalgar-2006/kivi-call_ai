package dev.callai.data

import android.content.Context
import android.content.SharedPreferences
import dev.callai.calls.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 32-year-old customer-service voice from India: "clear, medium-pitch, and friendly". Verified on gemini-3.8-live-extended-thinking. */
const val DEFAULT_VOICE = "en-in-csagent-5"

data class Prefs(
    val config: Config = Config(),
    val owner: String = "",
    val status: String = "",       // what saved contacts may hear, e.g. "In a meeting till 5"
    val key: String = "",          // Gemini API key
    val voice: String = DEFAULT_VOICE, // an Indian English voice from Google's voice library
    val mainLanguage: String = "Telugu", // what most callers speak; Kivi greets with it and leans on it when unsure
    val answerAfterSec: Int = 5,
    val keepDays: Int = 90,        // 0 = keep forever
    val onboarded: Boolean = false,
    val autoReply: Boolean = true, // text Kivi's drafted reply to real callers after the call
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
    }

    @Synchronized fun update(change: (Prefs) -> Prefs) {
        val p = change(_state.value)
        write(p)
        _state.value = p
    }

    fun config(change: (Config) -> Config) = update { it.copy(config = change(it.config)) }

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
        key = sp.getString("key", "").orEmpty(),
        // Earlier builds used Gemini's generic voices; anything that isn't an Indian voice moves to the default.
        voice = sp.getString("voice", "").orEmpty().takeIf { it.startsWith("en-in-") } ?: DEFAULT_VOICE,
        mainLanguage = sp.getString("mainLanguage", "Telugu").orEmpty().ifBlank { "Telugu" },
        answerAfterSec = sp.getInt("answerAfterSec", 5),
        keepDays = sp.getInt("keepDays", 90),
        onboarded = sp.getBoolean("onboarded", false),
        autoReply = sp.getBoolean("autoReply", true),
    )

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
            .putString("owner", p.owner).putString("status", p.status)
            .putString("key", p.key).putString("voice", p.voice).putString("mainLanguage", p.mainLanguage)
            .putInt("answerAfterSec", p.answerAfterSec).putInt("keepDays", p.keepDays)
            .putBoolean("onboarded", p.onboarded).putBoolean("autoReply", p.autoReply)
            .apply()
    }
}
