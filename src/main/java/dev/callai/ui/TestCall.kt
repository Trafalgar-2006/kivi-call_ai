package dev.callai.ui

import dev.callai.granted
import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.callai.ai.LiveSession
import dev.callai.audio.LocalAudio
import dev.callai.calls.Caller
import dev.callai.calls.Mode
import dev.callai.calls.activeModes
import dev.callai.calls.situation
import dev.callai.data.Settings

/** A practice call through the phone's mic and speaker: hear exactly what callers hear. Nothing is saved. */
@Composable
fun TestCallScreen(back: () -> Unit) {
    val ctx = context()
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val mic = granted(ctx, Manifest.permission.RECORD_AUDIO)
    val ask = rememberPermissionAsker()
    val session = remember {
        val c = Settings.now.config
        val mode = activeModes(c, situation(ctx, c)).firstOrNull() ?: Mode.STRANGERS
        LiveSession(ctx, Caller("", "a test caller"), mode, record = false) { _, e -> error = e; ended = true }
    }
    LaunchedEffect(mic) { if (mic) { session.connect(); session.begin(LocalAudio(ctx)) } }
    DisposableEffect(Unit) { onDispose { session.stop() } }
    val talking by session.talking.collectAsState()
    val lines by session.lines.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(0) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KiviBadge(if (ended) Mood.OFF else if (talking) Mood.TALKING else Mood.DUTY, 180.dp)
            Text(when {
                !mic -> "Kivi needs the microphone for a test call."
                error != null -> error!!
                ended -> "Call ended."
                !session.ready -> "Connecting…"
                talking -> "Kivi is talking"
                else -> "Listening… say hello in any language"
            }, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), state = list, reverseLayout = true) {
                item { Transcript(lines) }
            }
            if (!mic) Button(onClick = ask, Modifier.heightIn(min = 52.dp)) { Text("Allow microphone") }
            else if (ended) FilledTonalButton(onClick = back, Modifier.heightIn(min = 52.dp)) { Text("Done") }
            else Button(onClick = { session.stop() }, Modifier.heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Paint.Vermilion, contentColor = androidx.compose.ui.graphics.Color.White)) {
                Icon(Icons.Rounded.CallEnd, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("End test call")
            }
        }
    }
}
