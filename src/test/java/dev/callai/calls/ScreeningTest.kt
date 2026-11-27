package dev.callai.calls

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningTest {
    private val now = 1_800_000_000_000L
    private val noon = Situation(now = now, minuteOfDay = 12 * 60)
    private val stranger = Caller(number = "+91 98480 22334", name = null)

    @Test fun `with every mode off, calls ring normally`() {
        assertEquals(Decision.Ring("Kivi is off"), decide(Config(), noon, stranger, last = null))
    }

    @Test fun `strangers mode answers unknown numbers`() {
        assertEquals(Decision.Answer(Mode.STRANGERS), decide(Config(strangers = true), noon, stranger, last = null))
    }

    private val friend = Caller(number = "9848012345", name = "Ravi")
    private val sleepingForAnHour = Config(sleepUntil = now + 3_600_000)

    @Test fun `strangers mode lets saved contacts ring`() {
        assertEquals(Decision.Ring("Ravi is a saved contact"), decide(Config(strangers = true), noon, friend, last = null))
    }

    @Test fun `a sleep timer answers saved contacts too`() {
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, friend, last = null))
    }

    @Test fun `chosen people ring through however their number is written`() {
        val config = sleepingForAnHour.copy(ringThrough = setOf("09848012345"))
        val ravi = friend.copy(number = "+91 98480 12345")
        assertEquals(Decision.Ring("Ravi always rings through"), decide(config, noon, ravi, last = null))
    }

    private val urgent = Decision.Ring("Called 3 times in a row, so it may be urgent", urgent = true)
    private val minute = 60_000L

    @Test fun `the third call in a row rings through`() {
        assertEquals(urgent, decide(sleepingForAnHour, noon, stranger, LastRing(now - 2 * minute, Outcome.KIVI, inARow = 2)))
    }

    @Test fun `the second call in a row still goes to Kivi`() {
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, stranger, LastRing(now - minute, Outcome.KIVI, inARow = 1)))
    }

    @Test fun `a gap of more than ten minutes starts the count again`() {
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, stranger, LastRing(now - 11 * minute, Outcome.KIVI, inARow = 2)))
    }

    @Test fun `counting a run of calls`() {
        assertEquals(1, callsInARow(stranger, now, null))
        assertEquals(3, callsInARow(stranger, now, LastRing(now - 5 * minute, Outcome.KIVI, inARow = 2)))
        assertEquals(1, callsInARow(stranger, now, LastRing(now - 30_000, Outcome.URGENT, inARow = 3))) // put through: start over
        assertEquals(1, callsInARow(stranger.copy(number = ""), now, LastRing(now - 30_000, Outcome.KIVI, inARow = 2))) // hidden: can't count
    }

    @Test fun `after the urgent ring-through, the next call goes back to Kivi`() {
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, stranger, LastRing(now - 30_000, Outcome.URGENT, inARow = 3)))
    }

    @Test fun `numbers Kivi blocked never ring through, however often they call`() {
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, stranger, LastRing(now - 30_000, Outcome.BLOCKED, inARow = 5)))
    }

    @Test fun `an emergency caller's callback rings straight through and loud`() {
        assertEquals(Decision.Ring("Emergency callback", urgent = true),
            decide(sleepingForAnHour, noon, stranger, LastRing(now - 5 * minute, Outcome.EMERGENCY)))
    }

    @Test fun `the emergency pass lasts 15 minutes`() {
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, stranger, LastRing(now - 16 * minute, Outcome.EMERGENCY)))
    }

    @Test fun `hidden numbers can't use the repeat rule`() {
        val hidden = stranger.copy(number = "")
        assertEquals(Decision.Answer(Mode.SLEEP), decide(sleepingForAnHour, noon, hidden, LastRing(now - minute, Outcome.KIVI, inARow = 2)))
    }

    private val nightly = Config(sleepSchedule = true, bedtime = 23 * 60, wake = 6 * 60 + 30)

    @Test fun `the sleep schedule covers the small hours across midnight`() {
        val twoAm = Situation(now = now, minuteOfDay = 2 * 60)
        assertEquals(Decision.Answer(Mode.SLEEP), decide(nightly, twoAm, friend, last = null))
    }

    @Test fun `the sleep schedule is off during the day`() {
        assertEquals(Decision.Ring("Kivi is off"), decide(nightly, noon, friend, last = null))
    }

    @Test fun `the sleep schedule ends exactly at wake time`() {
        val wakeUp = Situation(now = now, minuteOfDay = 6 * 60 + 30)
        assertEquals(Decision.Ring("Kivi is off"), decide(nightly, wakeUp, friend, last = null))
    }

    @Test fun `busy follows the calendar when allowed`() {
        val inMeeting = noon.copy(inMeeting = true)
        assertEquals(Decision.Answer(Mode.BUSY), decide(Config(busyCalendar = true), inMeeting, friend, last = null))
        assertEquals(Decision.Ring("Kivi is off"), decide(Config(busyCalendar = false), inMeeting, friend, last = null))
    }

    @Test fun `drive turns on when the car is connected`() {
        val inCar = noon.copy(carConnected = true)
        assertEquals(Decision.Answer(Mode.DRIVE), decide(Config(carDevice = "Swift"), inCar, friend, last = null))
    }

    @Test fun `away timer answers everyone`() {
        assertEquals(Decision.Answer(Mode.AWAY), decide(Config(awayUntil = now + 60_000), noon, friend, last = null))
    }

    @Test fun `driving outranks the other modes`() {
        val all = Config(sleepUntil = FOREVER, busyUntil = FOREVER, awayUntil = FOREVER, driveUntil = FOREVER)
        assertEquals(listOf(Mode.DRIVE, Mode.SLEEP, Mode.BUSY, Mode.AWAY), activeModes(all, noon))
    }

    @Test fun `an expired sleep timer does nothing`() {
        assertEquals(Decision.Ring("Kivi is off"), decide(Config(sleepUntil = now - 1), noon, friend, last = null))
    }

    @Test fun `a number you blocked is declined, even on its 3rd call in a row`() {
        val blocked = Config(strangers = true, blocked = setOf("98480 22334"))
        assertEquals(Decision.Decline("you blocked this number"), decide(blocked, noon, stranger, LastRing(now - 60_000, Outcome.RANG, 2)))
    }

    @Test fun `known spam is declined for strangers only`() {
        val spam = noon.copy(knownSpam = true)
        assertEquals(Decision.Decline("Kivi blocked its last two calls as spam"), decide(Config(strangers = true), spam, stranger, last = null))
        assertEquals(Decision.Ring("Ravi is a saved contact"), decide(Config(strangers = true), spam, friend, last = null))
    }

    @Test fun `do not disturb sends everyone to Kivi, busy by day and asleep at night`() {
        assertEquals(Decision.Answer(Mode.BUSY), decide(Config(), noon.copy(dnd = true), friend, last = null))
        assertEquals(Decision.Answer(Mode.SLEEP), decide(Config(), Situation(now, 2 * 60, dnd = true), friend, last = null))
        assertEquals(Decision.Ring("Kivi is off"), decide(Config(followDnd = false), noon.copy(dnd = true), friend, last = null))
    }

    @Test fun `Kivi knows when a timed mode ends, and when the sleep schedule wakes you`() {
        assertEquals(now + 3_600_000, modeEnds(Config(busyUntil = now + 3_600_000), Mode.BUSY, noon))
        assertEquals(null, modeEnds(Config(busyUntil = FOREVER), Mode.BUSY, noon))
        val at2am = Situation(now - now % 60_000L, 2 * 60)
        assertEquals(at2am.now + (4 * 60 + 30) * 60_000L, modeEnds(Config(sleepSchedule = true), Mode.SLEEP, at2am)) // wakes 6:30
    }
}
