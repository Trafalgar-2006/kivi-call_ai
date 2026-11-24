package dev.callai.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt

class LevelerTest {
    /** 100 ms of a 300 Hz tone at the given peak, as 16 kHz little-endian PCM. */
    private fun tone(peak: Double) = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN).also { b ->
        for (i in 0 until 1600) b.putShort((peak * sin(2 * Math.PI * 300 * i / 16000)).toInt().toShort())
    }.array()

    private fun rms(pcm: ByteArray): Double {
        val b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        return sqrt((0 until pcm.size / 2).sumOf { b.getShort(it * 2).toDouble().let { s -> s * s } } / (pcm.size / 2))
    }

    @Test fun `quiet speech is brought up towards a steady level`() {
        val leveler = Leveler()
        var out = tone(1000.0) // about -33 dBFS: a quiet phone line
        repeat(30) { out = leveler.apply(tone(1000.0)) }
        assertTrue("rms ${rms(out)}", rms(out) in 2500.0..3500.0)
    }

    @Test fun `loud speech is left alone`() {
        val leveler = Leveler()
        val loud = tone(8000.0)
        repeat(10) { leveler.apply(loud) }
        assertEquals(rms(loud), rms(leveler.apply(loud)), 1.0)
    }

    @Test fun `line hiss between words is never amplified`() {
        val leveler = Leveler()
        val hiss = tone(150.0)
        repeat(30) { leveler.apply(hiss) }
        assertEquals(rms(hiss), rms(leveler.apply(hiss)), 1.0)
    }

    @Test fun `boosting never wraps around past full scale`() {
        val leveler = Leveler()
        repeat(30) { leveler.apply(tone(1000.0)) }
        val spike = leveler.apply(tone(30000.0)) // a sudden shout after a quiet stretch
        val b = ByteBuffer.wrap(spike).order(ByteOrder.LITTLE_ENDIAN)
        val positivePeakSign = (0 until 1600).map { b.getShort(it * 2) }.maxOrNull()!!
        assertTrue(positivePeakSign > 20000) // clipped at the top, not flipped negative
    }
}
