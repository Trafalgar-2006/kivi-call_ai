package dev.callai.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FallbackTest {
    @Test fun `resampling 16k to 24k keeps the shape and grows by half`() {
        val out = resample(shortArrayOf(0, 100, 200, 300), 16000, 24000)
        assertArrayEquals(shortArrayOf(0, 66, 133, 200, 266, 300), out)
    }

    @Test fun `a recording reads back as written`() {
        val f = File.createTempFile("rec", ".wav")
        WavWriter(f, 16_000).apply { write(byteArrayOf(1, 0, 2, 0)); write(byteArrayOf(-1, -1)); close(); write(byteArrayOf(9, 9)) }
        val (rate, samples) = readWav(f.readBytes())!!
        assertEquals(16_000, rate)
        assertEquals(listOf<Short>(1, 2, -1), samples.toList()) // nothing lands after close()
        f.delete()
    }

    @Test fun `reads sample rate and samples from a minimal wav`() {
        val wav = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 40, 0, 0, 0,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
            'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), 16, 0, 0, 0,
            1, 0, 1, 0, 0x22, 0x56, 0, 0, 0x44, (0xAC).toByte(), 0, 0, 2, 0, 16, 0, // PCM, mono, 22050 Hz
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 4, 0, 0, 0,
            1, 0, (0xFF).toByte(), (0xFF).toByte(),
        )
        val (rate, pcm) = readWav(wav)!!
        assertEquals(22050, rate)
        assertArrayEquals(shortArrayOf(1, -1), pcm)
    }
}
