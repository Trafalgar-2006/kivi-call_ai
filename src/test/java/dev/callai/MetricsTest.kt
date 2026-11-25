package dev.callai

import dev.callai.data.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsTest {
    private fun m(kind: String, value: Double = 0.0) = Metric(0, kind, value)

    @Test fun `nothing yet, nothing to show`() = assertTrue(weekly(emptyList()).isEmpty())

    @Test fun `answer rate, greeting speed, drops and endings`() {
        val lines = weekly(listOf(m("answered", 5.0), m("answered", 5.1), m("answered", 9.0), m("rang_through"),
            m("greeting", 0.6), m("greeting", 0.8), m("greeting", 1.4), m("drop"), m("resumed", 2.9),
            m("ended:goodbye"), m("ended:goodbye"), m("ended:silence")))
        assertEquals("3 answered, 1 rang through because something wasn't ready (75% answered)", lines[0])
        assertEquals("Greeting starts 0.8 s after answering (median), 1.4 s at worst", lines[1])
        assertEquals("1 line drop, 1 recovered", lines[2])
        assertEquals("Ended: 2 goodbye, 1 silence", lines[3])
    }

    @Test fun `one alarm per caller every 30 minutes`() {
        assertTrue(alarmAllowed(null, 1_000_000))
        assertFalse(alarmAllowed(1_000_000, 1_000_000 + 29 * 60_000))
        assertTrue(alarmAllowed(1_000_000, 1_000_000 + 30 * 60_000))
    }
}
