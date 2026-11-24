package dev.callai.calls

/** In priority order: when several are on, the first one is what Kivi tells contacts. */
enum class Mode { DRIVE, SLEEP, BUSY, AWAY, STRANGERS }

const val FOREVER = Long.MAX_VALUE // "until I turn it off"
const val URGENT_CALLS = 3                   // the 3rd call in a row rings through
private const val IN_A_ROW_MS = 10 * 60_000L // calls this close together count as "in a row"
private const val EMERGENCY_PASS_MS = 15 * 60_000L

/** Everything that decides who reaches the owner. Times are epoch ms; bedtime/wake are minutes of the day. */
data class Config(
    val strangers: Boolean = false,
    val sleepUntil: Long = 0,
    val sleepSchedule: Boolean = false,
    val bedtime: Int = 23 * 60,
    val wake: Int = 6 * 60 + 30,
    val busyUntil: Long = 0,
    val busyCalendar: Boolean = false,
    val driveUntil: Long = 0,
    val carDevice: String = "",
    val awayUntil: Long = 0,
    val ringThrough: Set<String> = emptySet(),
)

/** What's true at the moment the phone rings. */
data class Situation(
    val now: Long,
    val minuteOfDay: Int,
    val inMeeting: Boolean = false,
    val carConnected: Boolean = false,
)

data class Caller(val number: String, val name: String?)

sealed interface Decision {
    data class Ring(val why: String, val urgent: Boolean = false) : Decision
    data class Answer(val mode: Mode) : Decision
}

/** How the last call from a number went: it rang normally, rang as urgent, Kivi took it, or Kivi blocked it. */
enum class Outcome { RANG, URGENT, KIVI, BLOCKED, EMERGENCY }
/** inARow: how many calls this number had made in a row by then, that one included. */
data class LastRing(val time: Long, val outcome: Outcome, val inARow: Int = 1)

/** Same person whether written +91 98480 12345, 09848012345 or 9848012345. */
fun sameNumber(a: String, b: String): Boolean {
    val x = a.filter(Char::isDigit).takeLast(10)
    return x.isNotEmpty() && x == b.filter(Char::isDigit).takeLast(10)
}

fun inSleepWindow(minute: Int, bedtime: Int, wake: Int): Boolean =
    if (bedtime <= wake) minute in bedtime until wake else minute >= bedtime || minute < wake

/** The "answer everyone except your people" modes that are on right now, highest priority first. */
fun activeModes(c: Config, s: Situation): List<Mode> = buildList {
    if (s.now < c.driveUntil || (c.carDevice.isNotBlank() && s.carConnected)) add(Mode.DRIVE)
    if (s.now < c.sleepUntil || (c.sleepSchedule && inSleepWindow(s.minuteOfDay, c.bedtime, c.wake))) add(Mode.SLEEP)
    if (s.now < c.busyUntil || (c.busyCalendar && s.inMeeting)) add(Mode.BUSY)
    if (s.now < c.awayUntil) add(Mode.AWAY)
}

/**
 * Which call in a row this is (each within 10 minutes of the last). It restarts after a call was put
 * through as urgent, and hidden numbers can't be counted.
 */
fun callsInARow(caller: Caller, now: Long, last: LastRing?): Int =
    if (caller.number.isNotBlank() && last != null && now - last.time < IN_A_ROW_MS && last.outcome != Outcome.URGENT) last.inARow + 1 else 1

fun decide(config: Config, situation: Situation, caller: Caller, last: LastRing?): Decision {
    val everyone = activeModes(config, situation).firstOrNull()
    // Persistence is the only way past Kivi: the 3rd call in a row reaches you. Callers Kivi blocked never do.
    val urgent = callsInARow(caller, situation.now, last) >= URGENT_CALLS && last?.outcome != Outcome.BLOCKED
    // Kivi told an emergency caller that calling back would reach the owner; keep that promise for 15 minutes.
    val emergencyCallback = caller.number.isNotBlank() && last?.outcome == Outcome.EMERGENCY && situation.now - last.time < EMERGENCY_PASS_MS
    return when {
        emergencyCallback -> Decision.Ring("Emergency callback", urgent = true)
        urgent -> Decision.Ring("Called $URGENT_CALLS times in a row, so it may be urgent", urgent = true)
        everyone != null && config.ringThrough.any { sameNumber(it, caller.number) } -> Decision.Ring("${caller.name} always rings through")
        everyone != null -> Decision.Answer(everyone)
        !config.strangers -> Decision.Ring("Kivi is off")
        caller.name != null -> Decision.Ring("${caller.name} is a saved contact")
        else -> Decision.Answer(Mode.STRANGERS)
    }
}
