package dev.callai.ai

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class SummaryTest {
    private val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH)
    private val now = fmt.parse("2026-11-25T12:00")!!.time

    @Test fun `a callback later today is kept`() = assertEquals(fmt.parse("2026-11-25T18:00")!!.time, parseCallback("2026-11-25T18:00", now))
    @Test fun `no time, nothing to remind`() = assertEquals(0L, parseCallback("", now))
    @Test fun `a time already past is dropped`() = assertEquals(0L, parseCallback("2026-11-25T09:00", now))
    @Test fun `a misheard far-off date is dropped`() = assertEquals(0L, parseCallback("2027-03-01T10:00", now))
    @Test fun `garbage is dropped`() { assertEquals(0L, parseCallback("6 pm", now)); assertEquals(0L, parseCallback("2026-13-45T99:00", now)) }
}
