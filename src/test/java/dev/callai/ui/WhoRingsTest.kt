package dev.callai.ui

import dev.callai.calls.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class WhoRingsTest {
    @Test fun `no mode, everything rings`() = assertEquals("Right now: every call rings for you. Kivi isn't answering anyone.", whoRings(null, false, 0, false))
    @Test fun `broken trumps everything`() = assertEquals("Right now: every call rings for you, because Kivi can't answer (see the problem above).", whoRings(Mode.BUSY, true, 2, true))
    @Test fun `strangers only`() = assertEquals("Right now: saved contacts ring for you; Kivi answers unknown numbers.", whoRings(Mode.STRANGERS, true, 0, false))
    @Test fun `busy says what happens after it ends`() {
        assertEquals("Right now: Kivi answers everyone; calling 3 times in a row rings through. When Busy ends, every call rings for you.", whoRings(Mode.BUSY, false, 0, false))
        assertEquals("Right now: Kivi answers everyone; calling 3 times in a row rings through. The 1 person you chose always rings through. When Sleep ends, Kivi keeps answering unknown numbers.",
            whoRings(Mode.SLEEP, true, 1, false))
    }
}
