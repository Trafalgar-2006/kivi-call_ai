package dev.callai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.LiveAction
import dev.callai.ai.LiveSession
import dev.callai.calls.CallWatcher

/** Watch Kivi's call as it happens, tell it what to say, listen in, or step in with one tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCallScreen(back: () -> Unit) {
    val session by LiveSession.current.collectAsState()
    val s = session
    if (s == null) { LaunchedEffect(Unit) { back() }; return } // the call ended
    val lines by s.lines.collectAsState()
    val talking by s.talking.collectAsState()
    val list = rememberLazyListState()
    var words by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(CallWatcher.active?.listening == true) }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(0) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("On a call with ${s.caller.name ?: s.caller.number.ifBlank { "a hidden number" }}") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        }, actions = {
            // Listen in: the caller on your speaker; your mic stays muted.
            IconToggleButton(checked = listening, onCheckedChange = { on -> CallWatcher.active?.listen(on); listening = on }) {
                Icon(Icons.Rounded.Hearing, contentDescription = if (listening) "Stop listening" else "Listen in")
            }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KiviBadge(if (talking) Mood.TALKING else Mood.DUTY, 120.dp)
            Text(if (talking) "Kivi is talking" else "Kivi is listening", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), state = list, reverseLayout = true) {
                item { Transcript(lines) }
            }
            // Type what Kivi should say; it says it in the caller's language.
            OutlinedTextField(words, { words = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true,
                placeholder = { Text("Tell Kivi what to say") },
                trailingIcon = {
                    IconButton(onClick = { s.say(words.trim()); words = "" }, enabled = words.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Say it")
                    }
                })
            Button(onClick = { LiveAction.TAKE.perform() }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Take the call") }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { LiveAction.TEXT.perform() }, Modifier.heightIn(min = 48.dp)) { Text("Text me instead") }
                FilledTonalButton(onClick = { LiveAction.CALL_BACK.perform() }, Modifier.heightIn(min = 48.dp)) { Text("Call back in 10 min") }
            }
            OutlinedButton(onClick = { LiveAction.BLOCK.perform() }, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Block as spam") }
        }
    }
}
