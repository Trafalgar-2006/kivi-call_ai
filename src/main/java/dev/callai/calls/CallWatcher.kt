package dev.callai.calls

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.InCallService
import android.telecom.VideoProfile
import dev.callai.Emergency
import dev.callai.Health
import dev.callai.Notify
import dev.callai.ai.LiveSession
import dev.callai.ai.Net
import dev.callai.audio.Audio
import dev.callai.audio.Bridge
import dev.callai.audio.CallAudio
import dev.callai.data.Settings
import dev.callai.data.Store
import java.text.DateFormat
import java.util.Date

private const val MUTE_MIC = true // keeps your room out of the call; set false if callers can't hear Kivi
private const val ANSWER_GRACE_MS = 15_000L // extra wait for Gemini or the bridge; carriers ring for about 30 s

/** Set only by debug builds' fake phone line (src/debug), to test calls with a recorded caller. Always null in release. */
object TestHooks {
    const val FAKE_LINE = "dev.callai.debug.FakeCarrier"
    @Volatile var callerAudio: Audio? = null
}

/** Telecom binds this as a companion in-call service on every call (no need to be the default dialer). */
class CallWatcher : InCallService() {
    private val main = Handler(Looper.getMainLooper())
    private var session: LiveSession? = null
    private var sawMuted = false

    override fun onCreate() { super.onCreate(); active = this }
    override fun onDestroy() { if (active === this) active = null; super.onDestroy() }

    /** "Take it": Kivi tells the caller it's connecting you, then your mic opens and Kivi steps out. */
    fun takeOver() {
        val s = session ?: return
        val owner = Settings.now.owner.ifBlank { "The person they called" }
        s.direct("($owner is joining the call now. In one short sentence, in the caller's language, say you're connecting them.)")
        main.postDelayed({ setMuted(false) }, 2500) // unmuting ends Kivi's part (see onMuteStateChanged)
    }

    /** "Listen in": the caller on your speaker while Kivi keeps talking. Your mic stays muted, so they can't hear you. */
    fun listen(on: Boolean) = setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)

    val listening get() = callAudioState?.route == CallAudioState.ROUTE_SPEAKER

    // An urgent or emergency caller should wake you even on silent: an alarm-volume tone while it rings.
    private fun ringLoud(call: Call) {
        Emergency.sound(this)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state != Call.STATE_RINGING) { Emergency.quiet(this@CallWatcher); c.unregisterCallback(this) }
            }
        })
    }

    /** A second call while Kivi is on one rings for you as usual, and still counts toward 3 in a row. */
    private fun anotherCall(call: Call) {
        val now = System.currentTimeMillis()
        val caller = callerOf(call)
        val number = caller.number
        val who = caller.name ?: mask(number)
        val last = Store.lastRing(number)
        val decision = decide(Settings.now.config, situation(this, Settings.now.config, now, number), caller, last)
        if (decision is Decision.Decline) { Store.log("Declined $who: ${decision.why}"); return call.reject(false, null) }
        val urgent = decision is Decision.Ring && decision.urgent
        Store.rang(number, now, if (urgent) Outcome.URGENT else Outcome.RANG, callsInARow(caller, now, last))
        Store.log("$who called while Kivi was on another call; it rings for you")
        Notify.waiting(this, who)
        if (urgent) ringLoud(call)
    }

    // The network can mark a caller ID as forged. Then a saved name proves nothing: treat them as a stranger.
    private fun forged(call: Call) = call.details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
    private fun callerOf(call: Call): Caller {
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        return Caller(number, if (forged(call)) null else contactName(this, number))
    }

    override fun onCallAdded(call: Call) {
        if (call.details.state != Call.STATE_RINGING) return
        if (session != null) return anotherCall(call)
        val now = System.currentTimeMillis()
        val prefs = Settings.now
        val caller = callerOf(call)
        val number = caller.number
        val who = caller.name ?: mask(number)
        if (forged(call)) Store.log("$who's caller ID failed the network's check; treated as a stranger")
        val last = Store.lastRing(number)
        val inARow = callsInARow(caller, now, last) // remembered so the 3rd call in a row can reach you
        when (val decision = decide(prefs.config, situation(this, prefs.config, now, number), caller, last)) {
            is Decision.Decline -> { // a number you blocked, or known spam: it never rings and costs no Gemini call
                Store.rang(number, now, Outcome.BLOCKED, inARow)
                Store.log("Declined $who: ${decision.why}")
                call.reject(false, null)
            }
            is Decision.Ring -> {
                Store.rang(number, now, if (decision.urgent) Outcome.URGENT else Outcome.RANG, inARow)
                if (decision.urgent) ringLoud(call)
                Store.log("$who rang through: ${decision.why}")
                Notify.ringing(this, who) // "Send to Kivi", while it rings
                call.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(c: Call, state: Int) {
                        if (state != Call.STATE_RINGING) { Notify.endRinging(this@CallWatcher); c.unregisterCallback(this) }
                    }
                })
            }
            is Decision.Answer -> {
                Store.rang(number, now, Outcome.KIVI, inARow)
                // Saved contacts ring longer, so you can pick up a friend yourself.
                kivi(call, caller, who, decision.mode, inARow, now, if (caller.name != null) prefs.contactAnswerAfterSec else prefs.answerAfterSec)
            }
        }
    }

    /** "Send to Kivi" on a call that's ringing for you: Kivi answers it now, whoever it is and whatever the modes say. */
    fun sendToKivi() {
        if (session != null) return
        val call = calls.firstOrNull { it.details.state == Call.STATE_RINGING } ?: return
        val caller = callerOf(call)
        val who = caller.name ?: mask(caller.number)
        val now = System.currentTimeMillis()
        val inARow = Store.lastRing(caller.number)?.inARow ?: 1 // this ring was counted when it arrived
        Store.rang(caller.number, now, Outcome.KIVI, inARow)
        Store.log("You sent $who to Kivi")
        kivi(call, caller, who, Mode.BUSY, inARow, now, delaySec = 0)
    }

    /** Kivi takes the call: connects to Gemini while it rings, then answers after [delaySec] once everything is ready. */
    private fun kivi(call: Call, caller: Caller, who: String, mode: Mode, inARow: Int, now: Long, delaySec: Int) {
        val number = caller.number
        // Gemini's free daily limit is used up: don't make the caller wait 20 s for nothing.
        Settings.now.quotaUntil.takeIf { now < it }?.let { reset ->
            val why = "Gemini's free daily limit is used up until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(reset))}"
            Store.rang(number, now, Outcome.RANG, inARow)
            Store.log("$who rang through because $why")
            Store.metric("rang_through")
            return Notify.problem(this, "Kivi couldn't answer $who", why)
        }
        lateinit var s: LiveSession
        s = LiveSession(this, caller, mode, inARow = inARow) { byAssistant, _ ->
            main.post {
                if (byAssistant && call.details.state == Call.STATE_ACTIVE) call.disconnect()
                if (session === s) { session = null; Net.releaseCellular(this) }
            }
        }
        session = s
        Net.holdCellular(this) // mobile data stands by in case Wi-Fi fails mid-call
        s.connect() // warm up while it rings, so the greeting is instant
        Bridge.bind() // no-op when connected; otherwise the ring gives the bridge time to start
        var answered = false

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_ACTIVE && answered) {
                    // Debug builds' fake line can play a recorded caller instead of the phone's call audio.
                    val test = TestHooks.callerAudio?.takeIf { call.details.accountHandle?.componentName?.className == TestHooks.FAKE_LINE }
                    val audio = test ?: Bridge.service?.let(::CallAudio) ?: return s.stop(byAssistant = true)
                    sawMuted = false
                    if (MUTE_MIC) setMuted(true)
                    s.begin(audio)
                } else if (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED) {
                    // you picked up yourself (or a Bluetooth headset did), or the caller hung up
                    val after = "%.1f s".format((System.currentTimeMillis() - now) / 1000.0)
                    if (!answered && state == Call.STATE_ACTIVE && callAudioState?.route == CallAudioState.ROUTE_BLUETOOTH) {
                        // Some headsets answer every call by themselves a few seconds in, before Kivi can.
                        Store.log("$who was picked up on your Bluetooth headset after $after, before Kivi")
                        Notify.problem(this@CallWatcher, "Your Bluetooth headset answered $who",
                            "It picked up after $after, before Kivi. If it answers calls by itself, turn off its auto-answer, or set Settings › Pick up after to 3 s.")
                    } else if (!answered) Store.log(if (state == Call.STATE_ACTIVE) "You picked up $who"
                        else "$who hung up after $after, before Kivi answered")
                    s.stop()
                    c.unregisterCallback(this) // Kivi is out of this call now
                }
            }
        })

        // Checked at answer time, not on arrival: if this call just started Kivi, Shizuku and the bridge need a few
        // seconds to reconnect. The phone rings as usual meanwhile, so waiting a little longer for Gemini costs nothing.
        val giveUpAt = SystemClock.uptimeMillis() + delaySec * 1000L + ANSWER_GRACE_MS
        main.postDelayed(object : Runnable {
            override fun run() {
                if (call.details.state != Call.STATE_RINGING) return
                val problem = s.error ?: Health.blocker(this@CallWatcher) ?: if (s.ready) null else "Gemini didn't connect in time"
                when {
                    problem == null -> {
                        answered = true
                        val waited = (System.currentTimeMillis() - now) / 1000.0
                        Store.log("Answering $who (${mode.name.lowercase()}, call $inARow in a row) after %.1f s".format(waited))
                        Store.metric("answered", waited)
                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    }
                    s.error == null && SystemClock.uptimeMillis() < giveUpAt -> main.postDelayed(this, 500)
                    else -> { // When unsure, let it ring: Kivi never picks up into silence.
                        Store.rang(number, now, Outcome.RANG, inARow)
                        Store.log("$who rang through because $problem")
                        Store.metric("rang_through")
                        Notify.problem(this@CallWatcher, "Kivi couldn't answer $who", problem)
                        s.stop()
                    }
                }
            }
        }, delaySec * 1000L)
    }

    // Tapping unmute in the dialer = you're taking the call over; Kivi goes quiet.
    companion object {
        /** The bound service while a call is in progress, for the live screen's "Take it". */
        @Volatile var active: CallWatcher? = null; private set
    }

    // Telecom also unmutes when a call ends; that isn't you.
    override fun onMuteStateChanged(isMuted: Boolean) {
        if (isMuted) { sawMuted = true; return }
        val s = session ?: return
        if (sawMuted && calls.any { it.details.state == Call.STATE_ACTIVE }) { sawMuted = false; Store.log("You took over the call"); s.stop(why = "you took over") }
    }
}
