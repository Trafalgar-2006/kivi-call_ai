package dev.callai.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.callai.BuildConfig
import dev.callai.Updates
import dev.callai.calls.contactName
import dev.callai.data.CATEGORY_LABELS
import dev.callai.data.Settings
import dev.callai.data.Store
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private fun hint(text: String) = @Composable {
    Text(text, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Your own instruction per person: "Amma: say I'll call after 8". Kivi follows it for that number. */
@Composable fun PeopleRules() {
    val ctx = context()
    val prefs by Settings.state.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) } // the number whose instruction is being typed
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) editing = it.getString(0)
        }
    }
    Text("Instructions for particular people", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyLarge)
    hint("Kivi follows these when that person calls, e.g. \"Amma: tell her I'll call after 8\".")()
    prefs.people.forEach { (number, rule) ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(contactName(ctx, number) ?: number, style = MaterialTheme.typography.bodyLarge)
                Text(rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { Settings.update { it.copy(people = it.people - number) } }) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
        }
    }
    OutlinedButton(onClick = { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, Modifier.padding(top = 8.dp)) {
        Icon(Icons.Rounded.PersonAdd, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Add an instruction")
    }
    editing?.let { number ->
        var rule by remember(number) { mutableStateOf(prefs.people[number].orEmpty()) }
        AlertDialog(onDismissRequest = { editing = null },
            title = { Text("When ${contactName(ctx, number) ?: number} calls") },
            text = { OutlinedTextField(rule, { if (it.length <= 300) rule = it }, minLines = 2, placeholder = { Text("Tell them I'll call after 8") }) },
            confirmButton = { TextButton(onClick = { Settings.update { it.copy(people = it.people + (number to rule.trim())) }; editing = null }, enabled = rule.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
}

private val LANGUAGE_CODES = mapOf("Telugu" to "te-IN", "Hindi" to "hi-IN", "Kannada" to "kn-IN", "Tamil" to "ta-IN", "Malayalam" to "ml-IN")

/**
 * A native voice for callers who spoke a language last time. Voices come live from Google's voice library; if Gemini
 * refuses one during a call, Kivi switches back to your main voice (LiveSession.voice).
 */
@Composable fun LanguageVoices() {
    val prefs by Settings.state.collectAsState()
    var picking by remember { mutableStateOf<String?>(null) }
    Text("Voices for repeat callers", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodyLarge)
    hint("When someone spoke Telugu or Hindi last time, Kivi can answer their next call in a native voice.")()
    LANGUAGE_CODES.keys.forEach { language ->
        ListItem(headlineContent = { Text("$language callers") },
            supportingContent = { Text(prefs.voices[language] ?: "Your main voice") },
            trailingContent = { TextButton(onClick = { picking = language }) { Text("Change") } })
    }
    picking?.let { language -> VoicePicker(language) { picking = null } }
}

@Composable private fun VoicePicker(language: String, close: () -> Unit) {
    val prefs by Settings.state.collectAsState()
    var voices by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(language) {
        thread {
            runCatching {
                val url = "https://generativelanguage.googleapis.com/v1beta/voices?languageCode=${LANGUAGE_CODES.getValue(language)}&key=${Settings.now.key}"
                OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build().newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val list = JSONObject(r.body!!.string()).optJSONArray("voices")
                    voices = (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }
                        .map { it.optString("id") to "${it.optString("display_name", it.optString("id"))} · ${it.optString("gender")}" }
                        .filter { it.first.isNotBlank() }
                }
            }.onFailure { problem = "Couldn't load voices: check the internet and your Gemini key." }
        }
    }
    AlertDialog(onDismissRequest = close, title = { Text("Voice for $language callers") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                val choose = { id: String? -> Settings.update { it.copy(voices = if (id == null) it.voices - language else it.voices + (language to id)) }; close() }
                Row(Modifier.fillMaxWidth().selectable(prefs.voices[language] == null) { choose(null) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prefs.voices[language] == null, onClick = null); Text("Your main voice", Modifier.padding(start = 8.dp))
                }
                when {
                    problem != null -> Text(problem!!, color = MaterialTheme.colorScheme.error)
                    voices == null -> Text("Loading voices…")
                    voices!!.isEmpty() -> Text("Google has no $language voices yet.")
                    else -> voices!!.forEach { (id, label) ->
                        Row(Modifier.fillMaxWidth().selectable(prefs.voices[language] == id) { choose(id) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = prefs.voices[language] == id, onClick = null); Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

/** The link to FlyOS on your laptop (README, "FlyOS link"). Both fields set = on. */
@Composable fun FlyOSFields() {
    var url by remember { mutableStateOf(Settings.now.flyosUrl) }
    var token by remember { mutableStateOf(Settings.now.flyosToken) }
    OutlinedTextField(url, { url = it; Settings.update { p -> p.copy(flyosUrl = it.trim()) } }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text("FlyOS address") }, placeholder = { Text("https://laptop.tailnet.ts.net") })
    OutlinedTextField(token, { token = it; Settings.update { p -> p.copy(flyosToken = it.trim()) } }, Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true, label = { Text("FlyOS token") })
    hint("Kivi sends each call note to FlyOS and asks it about a caller while the phone rings. Leave empty to keep everything on this phone.")()
}

/** Looks for a newer release on GitHub and opens its page. */
/** Checks GitHub for a newer release; with an APK attached, one tap installs it (Android asks you to confirm). */
@Composable fun UpdateCheck() {
    val ctx = context()
    var status by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Updates.Release?>(null) }
    ListItem(headlineContent = { Text("Check for updates") },
        supportingContent = { Text(status ?: "You have version ${BuildConfig.VERSION_NAME}") },
        modifier = Modifier.selectable(false) {
            status = "Checking…"
            thread {
                status = runCatching { Updates.newer(BuildConfig.VERSION_NAME) }.fold(
                    { r -> release = r; if (r == null) "You have the latest version" else "Version ${r.tag.removePrefix("v")} is out" },
                    { "Couldn't reach GitHub" })
            }
        })
    release?.let { r ->
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r.apk != null) Button(onClick = {
                status = "Downloading…"
                thread { status = runCatching { Updates.install(ctx, r.apk); "Confirm the update when Android asks" }.getOrElse { "Download failed: ${it.message}" } }
            }) { Text("Install ${r.tag.removePrefix("v")}") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("What's new") }
        }
    }
}

/** Your standing instructions for every call: how Kivi behaves, without a new build. */
@Composable fun ExtraInstructions() {
    var extra by remember { mutableStateOf(Settings.now.extra) }
    OutlinedTextField(extra, { if (it.length <= 600) { extra = it; Settings.update { p -> p.copy(extra = it.trim()) } } },
        Modifier.fillMaxWidth().padding(top = 20.dp), minLines = 2,
        label = { Text("Instructions for every call (optional)") },
        supportingText = { Text("e.g. Speak Telugu with my family. Be formal with Office contacts. Kivi follows these unless they break its privacy and safety rules.") })
}

/** Numbers you blocked: declined without ringing or Kivi. Kivi also declines a stranger it blocked as spam twice. */
@Composable fun BlockedNumbers() {
    val ctx = context()
    val prefs by Settings.state.collectAsState()
    if (prefs.config.blocked.isEmpty()) return
    Text("Blocked numbers", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodyLarge)
    hint("Declined without ringing. Block or unblock from a call's note.")()
    prefs.config.blocked.sorted().forEach { number ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(contactName(ctx, number) ?: number, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { Settings.config { c -> c.copy(blocked = c.blocked - number) } }) { Icon(Icons.Rounded.Close, contentDescription = "Unblock") }
        }
    }
}

/** All settings in one file, for a new phone or a reinstall. The key and FlyOS token stay locked to this phone. */
@Composable fun BackupSettings() {
    val ctx = context()
    var status by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        status = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Settings.backup().toString(2).toByteArray()) }; "Settings saved" }
            .getOrElse { "Couldn't save: ${it.message}" }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        status = runCatching { Settings.restore(JSONObject(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })); "Settings restored" }
            .getOrElse { "Couldn't restore: ${it.message}" }
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { save.launch("kivi-settings.json") }) { Text("Back up settings") }
        OutlinedButton(onClick = { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Restore") }
    }
    hint(status ?: "Everything except your Gemini key and FlyOS token, which stay locked to this phone.")()
}

/** Every note as plain text, to save or send anywhere (transcripts stay in the app). */
@Composable fun ExportNotes() {
    val ctx = context()
    OutlinedButton(onClick = {
        val text = Store.calls.value.joinToString("\n\n") { n ->
            listOf(DateUtils.formatDateTime(ctx, n.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR),
                "${n.name} (${n.number.ifBlank { "hidden" }}) · ${CATEGORY_LABELS[n.category]}", n.reason,
                n.reply.takeIf { it.isNotBlank() }?.let { "Reply: $it" }).filterNotNull().joinToString("\n")
        }.ifEmpty { "No call notes." }
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Export call notes"))
    }, Modifier.padding(top = 12.dp)) { Text("Export notes") }
}
