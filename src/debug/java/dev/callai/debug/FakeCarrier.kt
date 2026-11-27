package dev.callai.debug

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dev.callai.ai.LiveSession
import dev.callai.ai.readWav
import dev.callai.ai.resample
import dev.callai.audio.Audio
import dev.callai.Reminders
import dev.callai.calls.TestHooks
import dev.callai.calls.contactInfo
import dev.callai.data.CallNote
import dev.callai.data.Settings
import dev.callai.data.Store
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/*
 * Debug builds only. A fake phone line, so the whole call path (Telecom → CallWatcher → Gemini → hang-up → note)
 * can be tested from a PC without a second phone. The fake caller is silent: there's no real call audio.
 *
 *   C=dev.callai/dev.callai.debug.FakeCarrier
 *   adb shell telecom register-phone-account $C fake 0 Fake && adb shell telecom set-phone-account-enabled $C fake 0
 *   adb shell am broadcast -n dev.callai/.debug.FakeCall --es number 40001   # ring (no number: a hidden caller)
 *       add --es audio caller.wav [--ei delay 5]: a recorded caller (tools/make_caller.mjs) speaks after the greeting
 *   adb shell am broadcast -n dev.callai/.debug.FakeCall --es do hangup      # the newest caller hangs up (hangup-all: every one)
 *   adb shell am broadcast -n dev.callai/.debug.FakeCall --es do fixture     # worst-case notes for the screens (fixture-huge: 1,000)
 *   adb shell am broadcast -n dev.callai/.debug.FakeCall --es do clean --el since <ms>   # delete test notes (hidden ones since <ms>)
 *   adb shell telecom unregister-phone-account $C fake 0                     # always, or the dialer offers "Fake"
 *   adb shell am broadcast -n dev.callai/.debug.FakeCall --es do set-model --es model models/gone   # a retired model (none: built-in)
 *   adb shell am broadcast -n dev.callai/.debug.FakeCall --es do key         # the Gemini key as the result, for the tools/ model tests
 *       (it's sealed in the keystore; only adb holds DUMP, which this receiver requires, and release builds lack it)
 *
 * Only numbers starting 4000 ring: they're too short for Kivi to ever text, and clean() knows them.
 */
private const val NUMBER = "dev.callai.debug.NUMBER"
private const val TEST_PREFIX = "4000"
private val lines = mutableListOf<Line>() // main thread only

class FakeCarrier : ConnectionService() {
    override fun onCreateIncomingConnection(account: PhoneAccountHandle?, request: ConnectionRequest): Connection = Line().apply {
        val number = request.extras?.getString(NUMBER).orEmpty()
        if (number.isEmpty()) setAddress(null, TelecomManager.PRESENTATION_RESTRICTED)
        else setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED)
        setRinging()
        lines += this
    }
}

class Line : Connection() {
    override fun onAnswer() = setActive()
    override fun onReject() = end(DisconnectCause.REJECTED)
    override fun onDisconnect() = end(DisconnectCause.LOCAL)
    override fun onAbort() = end(DisconnectCause.CANCELED)
    fun end(cause: Int) { setDisconnected(DisconnectCause(cause)); destroy(); lines -= this }
}

private const val MIN = 60_000L
private const val DAY = 24 * 60 * MIN
private val LONG_CHAT = (1..15).joinToString("\n") { "Caller: Haan andi, registration documents gurinchi, sub-registrar office Saturday close avutundi, challan expire avutundi.\nKivi: Achha, so you want Arjun to call back before 6 pm about the flat registration, right?" }

/** Realistic worst cases, spread across rows as real data is: long, short, other scripts, emoji, missing bits.
 * [audio]: a WAV for the first note's recording player (tools/caller.wav, if calltest.sh pushed it). */
private fun worstCase(now: Long, audio: String) = listOf(
    CallNote(time = now - 2 * MIN, number = "${TEST_PREFIX}1", name = "Venkata Satya Sai Lakshmi Narasimha Prasad Garikapati",
        reason = "Wants Arjun to call back before 6 pm about the registration documents for the Gachibowli flat; the sub-registrar office closes on Saturday and the stamp duty challan expires",
        category = "needs_you", reply = "నమస్కారం వెంకట సత్య సాయి లక్ష్మీ నరసింహ ప్రసాద్ గారు, మీ మెసేజ్ అందింది. అర్జున్ మీకు త్వరలోనే తిరిగి సమాధానం ఇస్తారు.",
        transcript = LONG_CHAT, durationMs = 178_000, mode = "BUSY", language = "Telugu", callbackAt = now + 2 * MIN, audio = audio), // its reminder fires in 2 minutes
    CallNote(time = now - 60 * MIN, number = "${TEST_PREFIX}2", name = "Jo", reason = "OK", category = "handled", reply = "",
        transcript = "Caller: OK", durationMs = 4_000, mode = "DRIVE"),
    CallNote(time = now - DAY, number = "${TEST_PREFIX}3", name = "QuickCash Loans Pvt. Ltd. (Priya, Senior Relationship Executive, Bengaluru)",
        reason = "Pre-approved personal loan offer", category = "blocked", reply = "", transcript = "", durationMs = 21_000, mode = "STRANGERS"),
    CallNote(time = now - 3 * DAY, number = "${TEST_PREFIX}4", name = "Neighbour",
        reason = "Emergency: Arjun's father fell down the stairs and is being taken to Apollo Hospitals, Jubilee Hills", category = "needs_you",
        reply = "Got it, I will let Arjun know immediately.", transcript = "Caller: His father fell down the stairs!\nKivi: I'm alerting Arjun right now.", durationMs = 63_000, mode = "SLEEP"),
    CallNote(time = now - 89 * DAY, number = "${TEST_PREFIX}5", name = "శ్రీనివాస రావు గారు", reason = "రేపు ఉదయం పది గంటలకు బ్యాంకు పని గురించి మాట్లాడాలని అనుకుంటున్నారు, తిరిగి కాల్ చేయమన్నారు",
        category = "handled", reply = "", transcript = "", durationMs = 0, mode = "AWAY"),
    CallNote(time = now - 5 * MIN, number = "${TEST_PREFIX}6", name = "राजेश कुमार (Flipkart डिलीवरी)", reason = "पार्सल गेट पर छोड़ दिया है, OTP नहीं चाहिए",
        category = "handled", reply = "ठीक है राजेश जी, संदेश मिल गया।", transcript = "Caller: पार्सल गेट पर है", durationMs = 600_000, mode = "BUSY"),
    CallNote(time = now - 30 * MIN, number = "", name = "Hidden number", reason = "The caller hung up before saying why", category = "needs_you",
        reply = "", transcript = "", durationMs = 2_000, mode = "STRANGERS"),
    CallNote(time = now - 10 * MIN, number = "${TEST_PREFIX}8", name = "Amma ❤️🙏", reason = "Asked if you've eaten; nothing urgent 😊",
        category = "handled", reply = "Haan Amma, message andindi 😊", transcript = "Caller: Tinnava?", durationMs = 45_000, mode = "BUSY"),
)

/**
 * A recorded caller for fake calls: [delaySec] of silence (Kivi greets), then the WAV at real-time pace, then silence
 * until the call ends. Kivi's own audio is dropped; its words show in the transcript.
 */
class WavCaller(file: File, private val delaySec: Int) : Audio {
    private val pcm: ByteArray = readWav(file.readBytes())?.let { (rate, samples) ->
        val s = resample(samples, rate, 16_000)
        ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> s.forEach { b.putShort(it) } }.array()
    } ?: ByteArray(0).also { Store.log("Fake line: ${file.name} isn't a 16-bit mono WAV") }
    @Volatile private var running = true

    override fun startCapture(onPcm: (ByteArray) -> Unit) {
        thread(name = "wav-caller") {
            val chunk = 3200 // 100 ms
            val silence = ByteArray(chunk)
            repeat(delaySec * 10) { if (!running) return@thread; onPcm(silence); Thread.sleep(100) }
            var i = 0
            while (running && i < pcm.size) { onPcm(pcm.copyOfRange(i, minOf(i + chunk, pcm.size))); i += chunk; Thread.sleep(100) }
            while (running) { onPcm(silence); Thread.sleep(100) }
        }
    }
    override fun play(gen: Int, pcm: ByteArray) = Unit
    override fun flush(gen: Int) = Unit
    override fun isPlaying() = false
    override fun stop() { running = false }
}

class FakeCall : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getStringExtra("do")) {
            "key" -> resultData = Settings.now.key
            // Pretend Google retired Kivi's model: the next connect must find a replacement (Models). No --es model = built-in.
            "set-model" -> Settings.update { it.copy(liveModel = intent.getStringExtra("model").orEmpty()) }
            // Block a test number (--es number 4000x); with no number, unblock every test number.
            "block" -> intent.getStringExtra("number")?.takeIf { it.startsWith(TEST_PREFIX) }
                ?.let { n -> Settings.config { it.copy(blocked = it.blocked + n) } } ?: Settings.config { it.copy(blocked = it.blocked.filterNot { b -> b.startsWith(TEST_PREFIX) }.toSet()) }
            // The 8 am self-check's Gemini test, now: "ok" or what went wrong.
            "check" -> { val done = goAsync(); LiveSession.check(ctx) { r -> done.resultData = r ?: "ok"; done.finish() } }
            // Checks contactInfo() against your contacts without showing anyone: how many have labels etc., and which labels.
            "who-check" -> resultData = runCatching {
                val numbers = ctx.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.NUMBER), null, null, null)?.use { c ->
                    generateSequence { if (c.moveToNext()) c.getString(0) else null }.filterNotNull().take(500).toList() }.orEmpty()
                val infos = numbers.mapNotNull { contactInfo(ctx, it) }
                "${numbers.size} numbers, ${infos.size} with info; labels: " +
                    infos.flatMap { i -> i.split("; ").filter { it.startsWith("labelled ") }.flatMap { it.removePrefix("labelled ").split(", ") } }.distinct() +
                    "; nicknames: ${infos.count { "nickname " in it }}; starred: ${infos.count { "starred" in it }}"
            }.getOrElse { "failed: $it" }
            "hangup" -> lines.lastOrNull()?.end(DisconnectCause.REMOTE) // the newest caller hangs up
            "hangup-all" -> lines.toList().forEach { it.end(DisconnectCause.REMOTE) }
            // Hidden callers have no number to recognise, so those go by time: --el since <ms when testing began>.
            "clean" -> {
                val since = intent.getLongExtra("since", Long.MAX_VALUE)
                val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                Store.calls.value.filter { it.number.startsWith(TEST_PREFIX) || (it.number.isEmpty() && it.time >= since) }
                    .forEach { Store.delete(it.id); nm.cancel(100_000 + it.id.toInt()); nm.cancel(it.time.toInt()) } // its reminder and note notifications
                if (since != Long.MAX_VALUE) Store.forgetMetrics(since) // calltest.sh stops if a real call comes in
            }
            // Worst-case notes for checking the screens (break-ui); clean removes them.
            "fixture", "fixture-huge" -> {
                val done = goAsync()
                val rows = worstCase(System.currentTimeMillis(), File(ctx.filesDir, "caller.wav").path)
                thread {
                    if (intent.getStringExtra("do") == "fixture") rows.forEach { n -> Reminders.schedule(ctx, n.copy(id = Store.add(n))) }
                    else repeat(1000) { i -> Store.add(rows[i % rows.size].copy(time = rows[0].time - i * 3_600_000L, number = "$TEST_PREFIX$i")) }
                    done.finish()
                }
            }
            else -> {
                val number = intent.getStringExtra("number").orEmpty()
                if (number.isNotEmpty() && !number.startsWith(TEST_PREFIX)) return Store.log("Fake line: refused $number, test numbers start $TEST_PREFIX")
                // --es audio caller.wav: a recorded caller (a WAV in Kivi's files folder) speaks after Kivi's greeting.
                TestHooks.callerAudio = intent.getStringExtra("audio")?.let { WavCaller(File(ctx.filesDir, it), intent.getIntExtra("delay", 5)) }
                val handle = PhoneAccountHandle(ComponentName(ctx, FakeCarrier::class.java), "fake")
                ctx.getSystemService(TelecomManager::class.java).addNewIncomingCall(handle, Bundle().apply {
                    putString(NUMBER, number)
                    if (number.isNotEmpty()) putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("tel", number, null))
                })
            }
        }
    }
}
