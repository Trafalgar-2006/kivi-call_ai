package dev.callai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.LiveAction
import dev.callai.ai.LiveSession

/** Watch Kivi's call as it happens and step in with one tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCallScreen(back: () -> Unit) {
    val session by LiveSession.current.collectAsState()
    val s = session
    if (s == null) { LaunchedEffect(Unit) { back() }; return } // the call ended
    val lines by s.lines.collectAsState()
    val talking by s.talking.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(0) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("On a call with ${s.caller.name ?: s.caller.number.ifBlank { "a hidden number" }}") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KiviBadge(if (talking) Mood.TALKING else Mood.DUTY, 120.dp)
            Text(if (talking) "Kivi is talking" else "Kivi is listening", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), state = list, reverseLayout = true) {
                item { Transcript(lines) }
            }
            Button(onClick = { LiveAction.TAKE.perform() }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Take the call") }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { LiveAction.TEXT.perform() }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Text me instead") }
                FilledTonalButton(onClick = { LiveAction.CALL_BACK.perform() }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Call back in 10 min") }
            }
            OutlinedButton(onClick = { LiveAction.BLOCK.perform() }, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Block as spam") }
        }
    }
}
