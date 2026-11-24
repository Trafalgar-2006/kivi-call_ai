package dev.callai.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReplyTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private fun text(category: String = "needs_you", number: String = "+91 98480 12345", reply: String = "Got it, will call you back.",
                     on: Boolean = true, lastSent: Long = 0) = shouldText(category, number, reply, on, lastSent, now)

    @Test fun `a real caller gets Kivi's drafted reply`() = assertTrue(text())
    @Test fun `never texts spam or scam callers`() = assertFalse(text(category = "blocked"))
    @Test fun `never texts hidden or short numbers`() { assertFalse(text(number = "")); assertFalse(text(number = "121")) }
    @Test fun `nothing to send without a drafted reply`() = assertFalse(text(reply = " "))
    @Test fun `off means off`() = assertFalse(text(on = false))
    @Test fun `at most one text per person every 12 hours`() {
        assertFalse(text(lastSent = now - 2 * hour))
        assertTrue(text(lastSent = now - 13 * hour))
    }
}
