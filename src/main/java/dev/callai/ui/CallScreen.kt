package dev.callai.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(id: Long, back: () -> Unit) {
    val ctx = context()
    val calls by Store.calls.collectAsState()
    val note = calls.find { it.id == id }
    var confirm by remember { mutableStateOf(false) }
    if (note == null) { LaunchedEffect(Unit) { back() }; return }

    Scaffold(topBar = {
        TopAppBar(title = {}, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        }, actions = {
            IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete note") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(note.name, style = MaterialTheme.typography.headlineMedium)
            Text(listOfNotNull(note.number.ifBlank { null },
                DateUtils.formatDateTime(ctx, note.time, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE),
                duration(note.durationMs)).joinToString(" · "),
                style = Tabular.merge(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            CategoryTag(note.category)
            Text(note.reason, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)

            if (note.number.isNotBlank()) Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${note.number}"))) }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Call, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Call back")
                }
                if (note.reply.isNotBlank()) FilledTonalButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${note.number}")).putExtra("sms_body", note.reply))
                }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Sms, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Send reply")
                }
            }
            if (note.reply.isNotBlank()) Surface(Modifier.padding(top = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (note.sentAt > 0) "Texted to them at ${clock(ctx, note.sentAt)}" else "Suggested reply",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(note.reply, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Text("Conversation", Modifier.padding(top = 28.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lines = parseTranscript(note.transcript)
            if (lines.isEmpty()) Text("Nothing was said.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Transcript(lines)
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete this note?") },
        text = { Text("The summary and the conversation are removed from this phone.") },
        confirmButton = { TextButton(onClick = { Store.delete(note.id); back() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
    )
}
