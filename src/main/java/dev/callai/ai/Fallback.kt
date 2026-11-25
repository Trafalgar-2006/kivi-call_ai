package dev.callai.ai

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.callai.audio.Audio
import dev.callai.audio.OUT_RATE
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Short lines recorded once with the phone's own text-to-speech, for when Gemini can't speak. */
object Fallback {
    const val SORRY = "fallback" // Gemini is gone: the call ends
    const val HOLD = "hold"      // Gemini dropped mid-call and Kivi is reconnecting
    private val LINES = mapOf(
        SORRY to "Sorry, I'm having trouble on this line. Please call back a little later.",
        HOLD to "Sorry, the line is a bit unclear. One moment please.",
    )
    private fun file(ctx: Context, line: String) = File(ctx.filesDir, "$line.wav")

    fun prepare(ctx: Context) {
        val todo = LINES.filterKeys { !file(ctx, it).exists() }
        if (todo.isEmpty()) return
        val left = AtomicInteger(todo.size)
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech t.shutdown()
            t.language = Locale("en", "IN")
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { if (left.decrementAndGet() == 0) t.shutdown() }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = onDone(id)
            })
            todo.forEach { (line, text) -> t.synthesizeToFile(text, null, file(ctx, line), line) }
        }
    }

    /** Plays a line into the call; with wait, blocks until it has played (max ~8 s). */
    fun play(ctx: Context, audio: Audio, gen: Int, line: String = SORRY, wait: Boolean = true) {
        val (rate, pcm) = file(ctx, line).takeIf { it.exists() }?.readBytes()?.let(::readWav) ?: return
        val bytes = toBytes(resample(pcm, rate, OUT_RATE))
        audio.flush(gen)
        for (i in bytes.indices step OUT_RATE / 5 * 2) audio.play(gen, bytes.copyOfRange(i, minOf(i + OUT_RATE / 5 * 2, bytes.size)))
        val deadline = SystemClock.uptimeMillis() + 8000
        while (wait && audio.isPlaying() && SystemClock.uptimeMillis() < deadline) Thread.sleep(150)
    }
}

/** Writes 16-bit mono PCM as a WAV file as it arrives; close() fills in the header's sizes. Thread-safe. */
class WavWriter(val file: File, private val rate: Int) {
    private val out = RandomAccessFile(file.apply { parentFile?.mkdirs() }, "rw").apply { setLength(0); write(ByteArray(44)) }
    private var bytes = 0
    private var closed = false

    @Synchronized fun write(pcm: ByteArray) { if (!closed) { out.write(pcm); bytes += pcm.size } }

    @Synchronized fun close() {
        if (closed) return
        closed = true
        out.seek(0)
        out.write(wavHeader(bytes, rate))
        out.close()
    }
}

/** The 44-byte header of a 16-bit mono PCM WAV holding [dataBytes] bytes of samples. */
fun wavHeader(dataBytes: Int, rate: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
    put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
    put("data".toByteArray()); putInt(dataBytes)
}.array()

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
