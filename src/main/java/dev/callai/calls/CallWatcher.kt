package dev.callai.calls

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import dev.callai.Emergency
import dev.callai.Health
import dev.callai.Notify
import dev.callai.ai.LiveSession
import dev.callai.audio.Bridge
import dev.callai.audio.CallAudio
import dev.callai.data.Settings
import dev.callai.data.Store

private const val MUTE_MIC = true // keeps your room out of the call; set false if callers can't hear Kivi
private const val ANSWER_GRACE_MS = 15_000L // extra wait for Gemini or the bridge; carriers ring for about 30 s

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

    // An urgent or emergency caller should wake you even on silent: an alarm-volume tone while it rings.
    private fun ringLoud(call: Call) {
        Emergency.sound(this)
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state != Call.STATE_RINGING) { Emergency.quiet(this@CallWatcher); c.unregisterCallback(this) }
            }
        })
    }

    override fun onCallAdded(call: Call) {
        if (call.details.state != Call.STATE_RINGING || session != null) return
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val now = System.currentTimeMillis()
        val prefs = Settings.now
        val caller = Caller(number, contactName(this, number))
        val who = caller.name ?: mask(number)
        val last = Store.lastRing(number)
        val inARow = callsInARow(caller, now, last) // remembered so the 3rd call in a row can reach you
        val decision = decide(prefs.config, situation(this, prefs.config, now), caller, last)
        if (decision is Decision.Ring) {
            Store.rang(number, now, if (decision.urgent) Outcome.URGENT else Outcome.RANG, inARow)
            if (decision.urgent) ringLoud(call)
            return Store.log("$who rang through: ${decision.why}")
        }
        val mode = (decision as Decision.Answer).mode
        Store.rang(number, now, Outcome.KIVI, inARow)

        lateinit var s: LiveSession
        s = LiveSession(this, caller, mode, inARow = inARow) { byAssistant, _ ->
            main.post {
                if (byAssistant && call.details.state == Call.STATE_ACTIVE) call.disconnect()
                if (session === s) session = null
            }
        }
        session = s
        s.connect() // warm up while it rings, so the greeting is instant
        Bridge.bind() // no-op when connected; otherwise the ring gives the bridge time to start
        var answered = false

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_ACTIVE && answered) {
                    val bridge = Bridge.service ?: return s.stop(byAssistant = true)
                    sawMuted = false
                    if (MUTE_MIC) setMuted(true)
                    s.begin(CallAudio(bridge))
                } else if (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED) {
                    // you picked up yourself, or the caller hung up
                    if (!answered) Store.log(if (state == Call.STATE_ACTIVE) "You picked up $who"
                        else "$who hung up after %.0f s, before Kivi answered".format((System.currentTimeMillis() - now) / 1000.0))
                    s.stop()
                    c.unregisterCallback(this) // Kivi is out of this call now
                }
            }
        })

        // Checked at answer time, not on arrival: if this call just started Kivi, Shizuku and the bridge need a few
        // seconds to reconnect. The phone rings as usual meanwhile, so waiting a little longer for Gemini costs nothing.
        val giveUpAt = SystemClock.uptimeMillis() + prefs.answerAfterSec * 1000L + ANSWER_GRACE_MS
        main.postDelayed(object : Runnable {
            override fun run() {
                if (call.details.state != Call.STATE_RINGING) return
                val problem = s.error ?: Health.blocker(this@CallWatcher) ?: if (s.ready) null else "Gemini didn't connect in time"
                when {
                    problem == null -> {
                        answered = true
                        Store.log("Answering $who (${mode.name.lowercase()}, call $inARow in a row) after %.1f s".format((System.currentTimeMillis() - now) / 1000.0))
                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    }
                    s.error == null && SystemClock.uptimeMillis() < giveUpAt -> main.postDelayed(this, 500)
                    else -> { // When unsure, let it ring: Kivi never picks up into silence.
                        Store.rang(number, now, Outcome.RANG, inARow)
                        Store.log("$who rang through because $problem")
                        Notify.problem(this@CallWatcher, "Kivi couldn't answer $who", problem)
                        s.stop()
                    }
                }
            }
        }, prefs.answerAfterSec * 1000L)
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
        if (sawMuted && calls.any { it.details.state == Call.STATE_ACTIVE }) { sawMuted = false; Store.log("You took over the call"); s.stop() }
    }
}
