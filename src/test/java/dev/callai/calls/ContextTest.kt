package dev.callai.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTest {
    private val m = 60_000L

    @Test fun `not in a meeting means no busy-until`() = assertNull(freeAt(listOf(100 * m to 160 * m), 50 * m))
    @Test fun `free when the meeting ends`() = assertEquals(160 * m, freeAt(listOf(100 * m to 160 * m), 120 * m))
    @Test fun `back-to-back meetings count as one`() =
        assertEquals(240 * m, freeAt(listOf(100 * m to 160 * m, 165 * m to 200 * m, 200 * m to 240 * m, 300 * m to 330 * m), 120 * m))

    @Test fun `codes and OTPs are never kept`() {
        Messages.add("Bank", "Your OTP is 482913", 1000)
        Messages.add("Ravi", "the gate code is 1234", 1000)
        assertTrue(Messages.from("Bank", 2000).isEmpty())
        assertTrue(Messages.from("Ravi", 2000).isEmpty())
    }

    @Test fun `a contact's last three messages, for six hours`() {
        val t = 10_000_000L
        listOf("hi", "are you free", "call me about tickets", "urgent pls").forEachIndexed { i, s -> Messages.add("Satvik", s, t + i) }
        assertEquals(listOf("are you free", "call me about tickets", "urgent pls"), Messages.from("satvik", t + 10).map { it.second })
        assertTrue(Messages.from("Satvik", t + 7 * 3_600_000L).isEmpty())
    }
}
