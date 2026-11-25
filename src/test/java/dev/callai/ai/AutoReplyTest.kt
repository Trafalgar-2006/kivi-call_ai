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
    // A caller's words shape the reply, and it goes out from your own number: it must not carry their link, number or payment handle.
    @Test fun `never texts a reply with a link`() {
        assertFalse(text(reply = "Please pay at bit.ly/eb-pay-now today."))
        assertFalse(text(reply = "See https://example.com"))
        assertFalse(text(reply = "Visit www.quickcash.in"))
    }
    @Test fun `never texts a reply with a phone number or payment handle`() {
        assertFalse(text(reply = "I owe Ravi money, call 9876543210."))
        assertFalse(text(reply = "Call 98765 43210."))
        assertFalse(text(reply = "Send it to help4mom@ybl."))
    }
    @Test fun `ordinary replies with times and short numbers still go out`() {
        assertTrue(text(reply = "Hi Suresh, got your message about the 6 pm match. Will get back to you."))
        assertTrue(text(reply = "Sare andi, message andindi. Tarvata call chesta."))
    }
    @Test fun `at most one text per person every 12 hours`() {
        assertFalse(text(lastSent = now - 2 * hour))
        assertTrue(text(lastSent = now - 13 * hour))
    }
}
