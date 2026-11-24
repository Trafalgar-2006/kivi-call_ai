package dev.callai.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.callai.Health
import dev.callai.ai.LiveSession
import dev.callai.calls.Mode
import dev.callai.calls.activeModes
import dev.callai.calls.situation
import dev.callai.data.CATEGORY_LABELS
import dev.callai.data.CallNote
import dev.callai.data.Settings
import dev.callai.data.Store
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = context()
    val prefs by Settings.state.collectAsState()
    val calls by Store.calls.collectAsState()
    val live by LiveSession.current.collectAsState()
    val tick = ticker(5_000) // timers expire, cars connect, Shizuku stops: re-read the world
    val now = remember(prefs, tick) { situation(ctx, prefs.config) }
    val active = remember(prefs, tick) { activeModes(prefs.config, now) }
    val blocker = remember(prefs, tick) { Health.blocker(ctx) }
    // Glanceable truth: when Kivi can't answer (e.g. Shizuku stopped after a reboot), the tailgate says so.
    val lead = (active.firstOrNull() ?: Mode.STRANGERS.takeIf { prefs.config.strangers })?.takeIf { blocker == null }
    val wanted = blocker != null && (active.isNotEmpty() || prefs.config.strangers)
    var sheet by remember { mutableStateOf<Mode?>(null) }
    var filter by rememberSaveable { mutableStateOf("needs_you") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kivi") }, actions = {
                IconButton(onClick = { go(Screen.Settings) }) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                val caller = live?.caller
                Tailgate(
                    onClick = if (live != null) ({ go(Screen.Live) }) else null, // during a call: tap to watch and step in
                    paint = modeColor(lead),
                    mood = when {
                        live != null -> Mood.TALKING
                        lead == Mode.SLEEP -> Mood.SLEEP
                        lead == Mode.DRIVE -> Mood.DRIVE
                        lead == Mode.BUSY -> Mood.BUSY
                        lead == null -> Mood.OFF
                        else -> Mood.DUTY
                    },
                    word = when {
                        live != null -> "On a call."
                        else -> when (lead) {
                            Mode.DRIVE -> "Driving."; Mode.SLEEP -> "Sleeping."; Mode.BUSY -> "Busy."
                            Mode.AWAY -> "Away."; Mode.STRANGERS -> "On duty."; null -> "Resting."
                        }
                    },
                    subtitle = when {
                        caller != null -> "Talking to ${caller.name ?: caller.number.ifBlank { "a hidden number" }}"
                        wanted -> "Kivi can't answer right now."
                        lead == null -> "Every call rings. Pick a mode to start."
                        lead == Mode.STRANGERS -> "Answering unknown numbers"
                        else -> "Answering everyone ${explain(ctx, lead, prefs.config, now)}"
                    },
                    rules = when (lead) {
                        null -> listOf("ALL CALLS RING", if (wanted) "TAP FIX BELOW" else "PICK A MODE BELOW")
                        Mode.STRANGERS -> listOf("CONTACTS RING THROUGH", "CALL 3 TIMES IF URGENT")
                        else -> listOf("KIVI ANSWERS EVERYONE", "CALL 3 TIMES IF URGENT")
                    },
                )
            }
            if (wanted) item { Problem(blocker!!) { go(Screen.Diagnostics) } }
            item { SectionTitle("Modes") }
            item {
                ModesGrid(prefs.config, active, now, onTap = { mode ->
                    if (mode == Mode.STRANGERS) {
                        val on = !prefs.config.strangers
                        Settings.config { it.copy(strangers = on) }
                        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(if (on) "Strangers on: unknown numbers go to Kivi" else "Strangers off") }
                    } else sheet = mode
                })
            }
            item { SectionTitle("Calls") }
            item { Filters(calls, filter) { filter = it } }
            val shown = calls.filter { filter == "all" || it.category == filter }
            if (calls.isEmpty()) item { EmptyCalls { go(Screen.Test) } }
            else if (shown.isEmpty()) item {
                Text("Nothing here.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.id }) { CallRow(it) { go(Screen.Call(it.id)) } }
        }
    }
    sheet?.let { ModeSheet(it) { sheet = null } }
}

/** The signature: a painted tailgate. A mode change repaints it with a wipe in the new mode's colour. */
@Composable
private fun Tailgate(onClick: (() -> Unit)?, paint: Color, mood: Mood, word: String, subtitle: String, rules: List<String>) {
    var shown by remember { mutableStateOf(paint) }
    var under by remember { mutableStateOf(paint) }
    val wipe = remember { Animatable(1f) }
    LaunchedEffect(paint) {
        if (paint != shown) { under = shown; shown = paint; wipe.snapTo(0f); wipe.animateTo(1f, kiviMotion()) }
    }
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                drawRect(under)
                drawRect(shown, size = Size(size.width * wipe.value, size.height))
                val band = 5.dp.toPx()
                drawRoundRect(Paint.Marigold, cornerRadius = CornerRadius(26.dp.toPx()), style = Stroke(band * 2))
                val inset = band + 4.dp.toPx()
                drawRoundRect(Paint.Vermilion, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
                    CornerRadius(19.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(mood, animationSpec = tween(MOTION_MS), label = "mood") { Kivi(it, Modifier.size(112.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                AnimatedContent(word, transitionSpec = {
                    (slideInVertically(kiviMotion()) { it / 3 } + fadeIn(tween(MOTION_MS))) togetherWith fadeOut(tween(MOTION_MS))
                }, label = "word") {
                    // One line at any font size: the sign shrinks rather than breaking "Sleeping." mid-word.
                    BasicText(it, style = signLarge(), maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 42.sp))
                }
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White) // AA on every mode paint
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(3.dp)) {
            drawLine(Paint.Marigold, Offset(0f, 0f), Offset(size.width, 0f), size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())))
        }
        // The rules sit on a night-enamel signboard so marigold lettering holds AA contrast on every mode's paint.
        Column(Modifier.fillMaxWidth().background(Paint.Night, RoundedCornerShape(12.dp)).padding(vertical = 8.dp)) {
            rules.forEach { Text(it, Modifier.fillMaxWidth(), style = SignSmall, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun Problem(text: String, onFix: () -> Unit) {
    Surface(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null)
            Text("Calls will ring normally: ${text}.", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

@Composable
private fun ModesGrid(config: dev.callai.calls.Config, active: List<Mode>, now: dev.callai.calls.Situation, onTap: (Mode) -> Unit) {
    val ctx = context()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Mode.SLEEP, Mode.BUSY, Mode.DRIVE, Mode.AWAY, Mode.STRANGERS).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    val on = if (mode == Mode.STRANGERS) config.strangers else mode in active
                    val caption = when {
                        mode == Mode.STRANGERS -> if (on) "Always on" else "Off"
                        on -> explain(ctx, mode, config, now).removePrefix("while you're ")
                        mode == Mode.SLEEP && config.sleepSchedule -> "Nightly ${clockOfDay(ctx, config.bedtime)}"
                        mode == Mode.BUSY && config.busyCalendar -> "With calendar"
                        mode == Mode.DRIVE && config.carDevice.isNotBlank() -> "With car"
                        else -> "Off"
                    }
                    ModeTile(mode, on, caption, Modifier.weight(1f)) { onTap(mode) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ModeTile(mode: Mode, on: Boolean, caption: String, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) modeColor(mode) else MaterialTheme.colorScheme.surfaceContainerHigh, kiviMotion(), label = "tile")
    val fg by animateColorAsState(if (on) Color.White else MaterialTheme.colorScheme.onSurface, kiviMotion(), label = "tileText")
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 92.dp), shape = RoundedCornerShape(20.dp), color = bg, contentColor = fg) {
        Column(Modifier.padding(14.dp)) {
            Icon(mode.icon, contentDescription = null)
            Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
            Text(mode.title, style = MaterialTheme.typography.titleSmall)
            Text(caption, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filters(calls: List<CallNote>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (CATEGORY_LABELS.keys + "all").forEach { key ->
            val count = if (key == "all") calls.size else calls.count { it.category == key }
            FilterChip(selected = selected == key, onClick = { onSelect(key) },
                label = { Text(if (key == "all") "All $count" else "${CATEGORY_LABELS[key]} $count", style = Tabular.merge(MaterialTheme.typography.labelLarge)) })
        }
    }
}

@Composable
private fun CallRow(note: CallNote, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(duration(note.durationMs), style = Tabular.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(note.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryTag(note.category)
                Text(DateUtils.getRelativeTimeSpanString(note.time).toString(), Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyCalls(onTest: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KiviBadge(Mood.DUTY, 112.dp)
        Text("No calls yet.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        Text("When Kivi takes a call, who it was and why lands here.", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onTest, Modifier.padding(top = 16.dp)) { Text("Hear what callers hear") }
    }
}
