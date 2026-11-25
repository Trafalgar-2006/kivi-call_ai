package dev.callai.ui

import android.content.Intent
import android.media.MediaPlayer
import android.provider.CalendarContract
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.ai.waNumber
import dev.callai.data.Store
import java.io.File

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

            // Wraps instead of squeezing "Send reply" at large font sizes.
            if (note.number.isNotBlank()) FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${note.number}"))) }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Call, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Call back")
                }
                if (note.reply.isNotBlank()) FilledTonalButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${note.number}")).putExtra("sms_body", note.reply))
                }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Sms, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Send reply")
                }
                // WhatsApp's own click-to-chat link: opens the chat with the reply typed in; you press send.
                val wa = waNumber(note.number)
                if (note.reply.isNotBlank() && wa != null) FilledTonalButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=${Uri.encode(note.reply)}"))) }
                }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("WhatsApp")
                }
                // They asked for a call back at a time: one tap puts it in your calendar.
                if (note.callbackAt > System.currentTimeMillis()) OutlinedButton(onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, note.callbackAt)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, note.callbackAt + 15 * 60_000L)
                            .putExtra(CalendarContract.Events.TITLE, "Call back ${note.name}")
                            .putExtra(CalendarContract.Events.DESCRIPTION, note.reason))
                    }
                }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Alarm, contentDescription = null); Spacer(Modifier.width(8.dp))
                    Text("Remind me at ${DateUtils.formatDateTime(ctx, note.callbackAt, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)}")
                }
            }
            if (note.audio.isNotBlank() && File(note.audio).exists()) Recording(note.audio)
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
    if (confirm) AlertDialog0(confirm = { Store.delete(note.id); back() }, dismiss = { confirm = false })
}

/** The caller's side of the call, as recorded (Settings > Record calls). */
@Composable
private fun Recording(path: String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release() } }
    OutlinedButton(onClick = {
        player?.let { it.release(); player = null } ?: run {
            player = runCatching { MediaPlayer().apply { setDataSource(path); prepare(); setOnCompletionListener { it.release(); player = null }; start() } }.getOrNull()
        }
    }, Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
        Icon(if (player == null) Icons.Rounded.PlayArrow else Icons.Rounded.Stop, contentDescription = null)
        Spacer(Modifier.width(8.dp)); Text(if (player == null) "Play the caller's audio" else "Stop")
    }
}

@Composable
private fun AlertDialog0(confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Delete this note?") },
        text = { Text("The summary, the conversation and any recording are removed from this phone.") },
        confirmButton = { TextButton(onClick = confirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Keep") } },
    )
}
