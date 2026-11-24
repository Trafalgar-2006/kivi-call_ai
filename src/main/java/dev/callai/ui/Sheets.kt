package dev.callai.ui

import dev.callai.granted
import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.calls.Config
import dev.callai.calls.FOREVER
import dev.callai.calls.Mode
import dev.callai.calls.bluetoothAudio
import dev.callai.data.Settings

private const val HOUR = 3_600_000L

private fun Config.until(mode: Mode) = when (mode) {
    Mode.SLEEP -> sleepUntil; Mode.BUSY -> busyUntil; Mode.DRIVE -> driveUntil; Mode.AWAY -> awayUntil; Mode.STRANGERS -> 0
}
private fun setUntil(mode: Mode, t: Long) = Settings.config {
    when (mode) {
        Mode.SLEEP -> it.copy(sleepUntil = t); Mode.BUSY -> it.copy(busyUntil = t)
        Mode.DRIVE -> it.copy(driveUntil = t); Mode.AWAY -> it.copy(awayUntil = t); Mode.STRANGERS -> it
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(mode: Mode, onDismiss: () -> Unit) {
    val ctx = context()
    val c = Settings.state.collectAsState().value.config
    val now = System.currentTimeMillis()
    var picking by remember { mutableStateOf<String?>(null) } // "bed" | "wake"
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Settings.config { it.copy(busyCalendar = true) }
    }
    fun pick(t: Long) { setUntil(mode, t); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(modeColor(mode), RoundedCornerShape(4.dp))) // the mode's own paint
            Text(when (mode) { Mode.SLEEP -> "Sleeping"; Mode.BUSY -> "Busy"; Mode.DRIVE -> "Driving"; else -> "Away" },
                style = MaterialTheme.typography.headlineSmall)
            Text(when (mode) {
                Mode.SLEEP -> "Kivi answers everyone while you sleep. Someone who calls 3 times in a row still reaches you."
                Mode.BUSY -> "Kivi answers while you're busy. Saved contacts hear that you're in the middle of something."
                Mode.DRIVE -> "Kivi answers while you drive, so you never reach for the phone."
                else -> "Kivi answers everyone while you're away from the phone."
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val options = buildList {
                if (mode != Mode.DRIVE) { add("For 1 hour" to now + HOUR); add("For 3 hours" to now + 3 * HOUR) }
                if (mode == Mode.SLEEP) add("Until ${clockOfDay(ctx, c.wake)}" to nextClock(c.wake))
                if (mode == Mode.AWAY) add("Until ${clockOfDay(ctx, 9 * 60)} tomorrow" to nextClock(9 * 60, now + 12 * HOUR))
                add("Until I turn it off" to FOREVER)
                if (mode == Mode.DRIVE) add("For 1 hour" to now + HOUR)
            }
            options.forEach { (label, t) -> Option(label, mode) { pick(t) } }

            when (mode) {
                Mode.SLEEP -> {
                    Toggle("Every night, ${clockOfDay(ctx, c.bedtime)} – ${clockOfDay(ctx, c.wake)}", c.sleepSchedule) { on ->
                        Settings.config { it.copy(sleepSchedule = on) }
                    }
                    TextButton(onClick = { picking = "bed" }) { Text("Change bedtime and wake time") }
                }
                Mode.BUSY -> Toggle("Whenever my calendar says busy", c.busyCalendar) { on ->
                    if (on && !granted(ctx, Manifest.permission.READ_CALENDAR)) askCalendar.launch(Manifest.permission.READ_CALENDAR)
                    else Settings.config { it.copy(busyCalendar = on) }
                }
                Mode.DRIVE -> CarPicker(c.carDevice)
                else -> Unit
            }
            if (now < c.until(mode)) OutlinedButton(onClick = { pick(0) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Turn off now") }
        }
    }
    picking?.let { which ->
        ClockDialog(if (which == "bed") "Bedtime" else "Wake time", if (which == "bed") c.bedtime else c.wake,
            onPick = { m ->
                if (which == "bed") { Settings.config { it.copy(bedtime = m) }; picking = "wake" }
                else { Settings.config { it.copy(wake = m, sleepSchedule = true) }; picking = null }
            }, onDismiss = { picking = null })
    }
}

@Composable
private fun Option(label: String, mode: Mode, onClick: () -> Unit) {
    Surface(onClick = onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(modeColor(mode), CircleShape))
            Text(label, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
fun CarPicker(car: String) {
    val ctx = context()
    if (car.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Turns on by itself when you connect to $car", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { Settings.config { it.copy(carDevice = "") } }) { Text("Forget") }
        }
        return
    }
    val devices = remember { bluetoothAudio(ctx) }
    Text(if (devices.isEmpty()) "Connect to your car's Bluetooth, then come back here to have Drive turn on by itself."
        else "Is one of these your car? Drive will turn on whenever it connects.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    devices.forEach { d -> OutlinedButton(onClick = { Settings.config { it.copy(carDevice = d) } }, Modifier.fillMaxWidth()) { Text(d) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockDialog(title: String, minute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val ctx = context()
    val state = rememberTimePickerState(minute / 60, minute % 60, DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
