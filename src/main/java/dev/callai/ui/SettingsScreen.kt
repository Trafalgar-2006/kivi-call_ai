package dev.callai.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.calls.contactName
import dev.callai.calls.sameNumber
import dev.callai.data.Settings
import dev.callai.data.Store

// Indian English voices from Google's voice library, each checked to work in live calls. Top row women, bottom row men.
private val VOICES = listOf("en-in-csagent-5" to "Friendly", "en-in-concierge-3" to "Polished", "en-in-podcaster-11" to "Bright",
    "en-in-csagent-1" to "Clear", "en-in-csagent-7" to "Relaxed", "en-in-concierge-8" to "Youthful")
private val LANGUAGES = listOf("Telugu", "Hindi", "Kannada", "Tamil", "Malayalam").map { it to it }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val ctx = context()
    val prefs by Settings.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) Settings.config { c -> c.copy(ringThrough = c.ringThrough + it.getString(0)) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionTitle("You")
            Column(Modifier.padding(horizontal = 20.dp)) { AboutYouFields() }

            SectionTitle("Answering")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Pick up after", style = MaterialTheme.typography.bodyLarge)
                Choice(listOf(3, 5, 8, 12).map { it to "$it s" }, prefs.answerAfterSec) { s -> Settings.update { it.copy(answerAfterSec = s) } }
                Text("Gives you a few rings to answer yourself first.", Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Always ring through", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyLarge)
                Text("Only the people you add here skip Kivi. Anyone else reaches you by calling 3 times in a row.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                prefs.config.ringThrough.sorted().forEach { number ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(contactName(ctx, number) ?: number, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { Settings.config { c -> c.copy(ringThrough = c.ringThrough.filterNot { sameNumber(it, number) }.toSet()) } }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove")
                        }
                    }
                }
                OutlinedButton(onClick = { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                    Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Add a person")
                }

                Toggle("Text callers back automatically", prefs.autoReply) { on -> Settings.update { it.copy(autoReply = on) } }
                Text("After a call, Kivi's drafted reply goes out by SMS from your number. Never to spam or hidden numbers, at most once every 12 hours per person.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Your car", Modifier.padding(top = 24.dp, bottom = 4.dp), style = MaterialTheme.typography.bodyLarge)
                CarPicker(prefs.config.carDevice)
            }

            SectionTitle("How Kivi talks")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Besides English, callers often speak", style = MaterialTheme.typography.bodyLarge)
                Choice(LANGUAGES.take(3), prefs.mainLanguage) { l -> Settings.update { it.copy(mainLanguage = l) } }
                Choice(LANGUAGES.drop(3), prefs.mainLanguage) { l -> Settings.update { it.copy(mainLanguage = l) } }
                Text("Kivi always starts in English, then switches to whatever language the caller actually speaks. This one is its first guess when a line is unclear.",
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Voice", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodyLarge)
                Choice(VOICES.take(3), prefs.voice) { v -> Settings.update { it.copy(voice = v) } }
                Choice(VOICES.drop(3), prefs.voice) { v -> Settings.update { it.copy(voice = v) } }
                Text("Real Indian English voices. Top row: women's voices, bottom row: men's. Try one with Settings › Test call.", Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("Gemini")
            Column(Modifier.padding(horizontal = 20.dp)) { KeyField() }

            SectionTitle("Notes")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Keep notes for", style = MaterialTheme.typography.bodyLarge)
                Choice(listOf(30 to "30 days", 90 to "90 days", 365 to "1 year", 0 to "Always"), prefs.keepDays) { d ->
                    Settings.update { it.copy(keepDays = d) }; Store.prune(d)
                }
                OutlinedButton(onClick = { confirmDelete = true }, Modifier.padding(top = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete all notes") }
            }

            SectionTitle("Help")
            Link("Test call", "Hear what callers hear, on the speaker") { go(Screen.Test) }
            Link("Diagnostics", "What's working, what isn't, and recent activity") { go(Screen.Diagnostics) }
            Link("Run setup again", "Shizuku, permissions, key and a test call") { Settings.update { it.copy(onboarded = false) } }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete every call note?") },
        text = { Text("Summaries and conversations are removed from this phone. This can't be undone.") },
        confirmButton = { TextButton(onClick = { Store.deleteAll(); confirmDelete = false }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(selected = value == selected, onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
        }
    }
}

@Composable
private fun Link(title: String, body: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(body) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick))
}
