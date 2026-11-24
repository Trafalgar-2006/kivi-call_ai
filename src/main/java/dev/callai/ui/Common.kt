package dev.callai.ui

import android.Manifest
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.callai.ai.Line
import dev.callai.data.CATEGORY_LABELS
import dev.callai.calls.Config
import dev.callai.calls.FOREVER
import dev.callai.calls.Mode
import dev.callai.calls.Situation
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

val Tabular = TextStyle(fontFeatureSettings = "tnum")

// --- time ---

fun clock(ctx: Context, millis: Long): String = DateFormat.getTimeFormat(ctx).format(Date(millis))
fun clockOfDay(ctx: Context, minute: Int): String =
    clock(ctx, Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60) }.timeInMillis)

/** The next time the clock shows this minute of the day. */
fun nextClock(minute: Int, now: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

fun until(ctx: Context, t: Long): String = when {
    t == FOREVER -> "until you turn it off"
    DateUtils.isToday(t) -> "until ${clock(ctx, t)}"
    else -> "until ${clock(ctx, t)} tomorrow"
}

fun duration(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

@Composable fun ticker(everyMs: Long): Int = produceState(0) { while (true) { delay(everyMs); value++ } }.value

// --- modes ---

val Mode.title get() = when (this) { Mode.SLEEP -> "Sleep"; Mode.BUSY -> "Busy"; Mode.DRIVE -> "Drive"; Mode.AWAY -> "Away"; Mode.STRANGERS -> "Strangers" }
val Mode.icon: ImageVector get() = when (this) {
    Mode.SLEEP -> Icons.Rounded.Bedtime; Mode.BUSY -> Icons.Rounded.Event; Mode.DRIVE -> Icons.Rounded.DirectionsCar
    Mode.AWAY -> Icons.Rounded.Luggage; Mode.STRANGERS -> Icons.Rounded.Shield
}

/** Why a mode is on right now, in words ("until 6:30 AM", "while your calendar says busy"). */
fun explain(ctx: Context, mode: Mode, c: Config, s: Situation): String = when (mode) {
    Mode.DRIVE -> if (s.now < c.driveUntil) until(ctx, c.driveUntil) else "while you're connected to ${c.carDevice}"
    Mode.SLEEP -> if (s.now < c.sleepUntil) until(ctx, c.sleepUntil) else "until ${clockOfDay(ctx, c.wake)}"
    Mode.BUSY -> if (s.now < c.busyUntil) until(ctx, c.busyUntil) else "while your calendar says busy"
    Mode.AWAY -> until(ctx, c.awayUntil)
    Mode.STRANGERS -> "always"
}

// --- call notes ---

@Composable fun CategoryTag(category: String) {
    val (bg, fg) = when (category) {
        "needs_you" -> Paint.Vermilion to Color.White
        "handled" -> Paint.Leaf to Color.White
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp)) {
        Text(CATEGORY_LABELS[category] ?: category, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

fun parseTranscript(t: String): List<Line> = t.lines().filter { it.isNotBlank() }.map {
    if (it.startsWith("Caller: ")) Line(true, it.removePrefix("Caller: ")) else Line(false, it.removePrefix("Kivi: "))
}

/** Mirrored columns: the caller on the left, Kivi on the right, time running down. */
@Composable fun Transcript(lines: List<Line>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEachIndexed { i, line ->
            val first = i == 0 || lines[i - 1].fromCaller != line.fromCaller
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (line.fromCaller) Alignment.Start else Alignment.End) {
                if (first) Text(if (line.fromCaller) "Caller" else "Kivi", Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    color = if (line.fromCaller) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (line.fromCaller) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = if (line.fromCaller) RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp) else RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    modifier = Modifier.widthIn(max = 300.dp),
                ) { Text(line.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

// --- permissions ---

val RUNTIME_PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS,
    Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CALENDAR, Manifest.permission.SEND_SMS)

/** Returns a function that asks for every runtime permission Kivi uses. */
@Composable fun rememberPermissionAsker(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    return { launcher.launch(RUNTIME_PERMISSIONS) }
}

@Composable fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable fun context(): Context = LocalContext.current
