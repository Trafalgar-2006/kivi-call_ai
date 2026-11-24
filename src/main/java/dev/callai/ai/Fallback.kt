package dev.callai.ai

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.callai.audio.Audio
import dev.callai.audio.OUT_RATE
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** A short apology recorded once with the phone's own text-to-speech, played if Gemini drops mid-call. */
object Fallback {
    private const val TEXT = "Sorry, I'm having trouble on this line. Please call back a little later."
    private fun file(ctx: Context) = File(ctx.filesDir, "fallback.wav")

    fun prepare(ctx: Context) {
        if (file(ctx).exists()) return
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech t.shutdown()
            t.language = Locale("en", "IN")
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = t.shutdown()
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = t.shutdown()
            })
            t.synthesizeToFile(TEXT, null, file(ctx), "fallback")
        }
    }

    /** Blocks until played (max ~8 s). */
    fun play(ctx: Context, audio: Audio, gen: Int) {
        val (rate, pcm) = file(ctx).takeIf { it.exists() }?.readBytes()?.let(::readWav) ?: return
        val bytes = toBytes(resample(pcm, rate, OUT_RATE))
        audio.flush(gen)
        for (i in bytes.indices step OUT_RATE / 5 * 2) audio.play(gen, bytes.copyOfRange(i, minOf(i + OUT_RATE / 5 * 2, bytes.size)))
        val deadline = SystemClock.uptimeMillis() + 8000
        while (audio.isPlaying() && SystemClock.uptimeMillis() < deadline) Thread.sleep(150)
    }
}

/** 16-bit mono WAV → (sample rate, samples). Null if it isn't one. */
fun readWav(bytes: ByteArray): Pair<Int, ShortArray>? {
    if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF") return null
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val rate = b.getInt(24)
    var i = 12
    while (i + 8 <= bytes.size) { // walk chunks to "data"
        val id = String(bytes, i, 4)
        val size = b.getInt(i + 4)
        if (id == "data") {
            val n = minOf(size, bytes.size - i - 8) / 2
            return rate to ShortArray(n) { b.getShort(i + 8 + it * 2) }
        }
        i += 8 + size + (size and 1)
    }
    return null
}

/** Linear resampling; good enough for a spoken sentence. */
fun resample(x: ShortArray, from: Int, to: Int): ShortArray {
    if (from == to || x.isEmpty()) return x
    val n = (x.size.toLong() * to / from).toInt()
    return ShortArray(n) { j ->
        val pos = j.toDouble() * from / to
        val k = pos.toInt().coerceAtMost(x.size - 1)
        val next = x[minOf(k + 1, x.size - 1)]
        (x[k] + (next - x[k]) * (pos - k)).toInt().toShort()
    }
}

private fun toBytes(s: ShortArray): ByteArray =
    ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> s.forEach { b.putShort(it) } }.array()
