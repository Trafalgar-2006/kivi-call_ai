package dev.callai

import dev.callai.data.CallNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTest {
    private val day = 1_800_000_000_000L
    private fun note(name: String, category: String, time: Long = day + 1000) =
        CallNote(time = time, number = "9848012345", name = name, reason = "about $name", category = category,
            reply = "", transcript = "", durationMs = 30_000, mode = "BUSY")

    @Test fun `no summary on a quiet day`() = assertNull(digest(listOf(note("Old", "needs_you", day - 1)), day))

    @Test fun `counts today's calls and names who needs you`() {
        val (title, text) = digest(listOf(note("Satvik", "needs_you"), note("Amma", "handled"), note("Spam", "blocked")), day)!!
        assertEquals("3 calls today, 1 needs you", title)
        assertEquals("Satvik: about Satvik", text)
    }

    @Test fun `says so when nothing needs you`() {
        val (title, text) = digest(listOf(note("Amma", "handled")), day)!!
        assertEquals("1 call today, nothing needs you", title)
        assertEquals("Kivi handled everything.", text)
    }

    @Test fun `long days stay short`() {
        val (title, text) = digest((1..7).map { note("P$it", "needs_you") }, day)!!
        assertEquals("7 calls today, 7 need you", title)
        assertTrue(text.endsWith("and 2 more"))
    }

    @Test fun `version numbers compare as numbers`() {
        assertTrue(isNewer("v0.10", "0.9"))
        assertTrue(isNewer("0.4", "0.3"))
        assertFalse(isNewer("v0.3", "0.3"))
        assertFalse(isNewer("0.2.9", "0.3"))
        assertTrue(isNewer("1.0", "0.99"))
    }
}
