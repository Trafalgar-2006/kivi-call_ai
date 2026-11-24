package dev.callai.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Brings a quiet phone line up to a steady speaking level before Gemini hears it.
 * Rises slowly, drops at once on loud speech (no clipping a shout), and leaves line hiss alone.
 */
class Leveler(private val target: Double = 3000.0, private val maxGain: Double = 6.0) {
    private var gain = 1.0
    private var speechChunks = 0
    private var speechRms = 0.0

    fun apply(pcm: ByteArray): ByteArray {
        val n = pcm.size / 2
        if (n == 0) return pcm
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        for (i in 0 until n) { val s = input.getShort(i * 2).toDouble(); sum += s * s }
        val rms = sqrt(sum / n)
        if (rms > SPEECH_FLOOR) {
            speechChunks++; speechRms += rms
            val want = (target / rms).coerceIn(1.0, maxGain)
            gain = if (want < gain) want else gain + (want - gain) * 0.2
        }
        if (gain <= 1.01) return pcm
        val out = ByteBuffer.allocate(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) out.putShort((input.getShort(i * 2) * gain).roundToInt().coerceIn(-32768, 32767).toShort())
        return out.array()
    }

    /** For the diagnostics log: how loud the caller was and how much they were boosted. */
    fun summary(): String {
        if (speechChunks == 0) return "Caller audio: no speech heard on the line"
        val db = 20 * log10(speechRms / speechChunks / 32768)
        return "Caller audio: %.0f dBFS, boosted ×%.1f".format(db, gain)
    }

    private companion object { const val SPEECH_FLOOR = 300.0 } // ≈ -40 dBFS; quieter is line noise
}
