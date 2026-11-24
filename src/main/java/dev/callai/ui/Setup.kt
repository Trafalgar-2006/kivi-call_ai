package dev.callai.ui

import dev.callai.granted
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.callai.Health
import dev.callai.ai.LiveSession
import dev.callai.audio.Bridge
import dev.callai.data.Settings
import rikka.shizuku.Shizuku

private const val SHIZUKU = "moe.shizuku.privileged.api"
private val STEPS = listOf("Meet Kivi", "Shizuku", "Permissions", "Gemini key", "About you", "Try it")

/** First run: one step per screen, each with its own check, so nothing is left half set up. */
@Composable
fun SetupScreen(go: (Screen) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val tick = ticker(1_000)
    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(progress = { (step + 1f) / STEPS.size }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
            AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
                (slideInHorizontally(kiviMotion()) { it / 4 } + fadeIn(tween(MOTION_MS))) togetherWith fadeOut(tween(MOTION_MS))
            }, label = "step") { s ->
                BoxWithConstraints { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 24.dp),
                    verticalArrangement = if (s == 0) Arrangement.Center else Arrangement.Top) {
                    when (s) { // status steps re-read every second; steps with text fields must not
                        0 -> Welcome()
                        1 -> key(tick) { ShizukuStep() }
                        2 -> key(tick) { PermissionsStep() }
                        3 -> KeyStep()
                        4 -> AboutYou()
                        else -> key(tick) { TryIt { go(Screen.Test) } }
                    }
                } }
            }
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) TextButton(onClick = { step-- }, Modifier.heightIn(min = 48.dp)) { Text("Back") } else Spacer(Modifier)
                if (step < STEPS.lastIndex) Button(onClick = { step++ }, Modifier.heightIn(min = 48.dp)) { Text(if (step == 0) "Set up Kivi" else "Next") }
                else Button(onClick = { Settings.update { it.copy(onboarded = true) } }, Modifier.heightIn(min = 48.dp)) { Text("Finish") }
            }
        }
    }
}

@Composable private fun Heading(title: String, body: String) {
    Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall)
    Text(body, Modifier.padding(top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun Status(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = if (ok) "Done" else "Not yet",
            tint = if (ok) Paint.Leaf else MaterialTheme.colorScheme.outline)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun Welcome() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KiviBadge(Mood.DUTY, 200.dp)
        Text("Kivi answers the calls you can't.", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("It talks with the caller in their own language (English, Telugu, Hindi, Kannada, Tamil or Malayalam) and leaves you a note: who called, why, and whether it needs you.",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable private fun ShizukuStep() {
    val ctx = context()
    val installed = runCatching { ctx.packageManager.getPackageInfo(SHIZUKU, 0) }.isSuccess
    Heading("Connect Shizuku", "Shizuku lets Kivi hear the caller and speak into the call, with no root. Start it from the Shizuku app using Wireless debugging. You'll need to start it again after every restart.")
    Status("Shizuku is installed", installed)
    Status("Shizuku is running", Bridge.running())
    Status("Kivi is allowed in Shizuku", Bridge.allowed())
    Status("Call audio bridge connected", Bridge.connected())
    Status("Android lets Kivi answer calls", Bridge.companion)
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !installed -> Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }) { Text("Get Shizuku") }
            !Bridge.running() -> Button(onClick = { ctx.packageManager.getLaunchIntentForPackage(SHIZUKU)?.let(ctx::startActivity) }) { Text("Open Shizuku") }
            !Bridge.allowed() -> Button(onClick = { Shizuku.requestPermission(1) }) { Text("Allow Kivi") }
            !Bridge.connected() -> OutlinedButton(onClick = { Bridge.bind() }) { Text("Connect again") }
        }
    }
}

@Composable private fun PermissionsStep() {
    val ctx = context()
    val ask = rememberPermissionAsker()
    Heading("Permissions", "Kivi reads contacts to know who's a stranger, posts a note after each call, uses the microphone only for test calls, and reads your calendar only if Busy should follow it.")
    Status("Contacts", granted(ctx, android.Manifest.permission.READ_CONTACTS))
    Status("Notifications", granted(ctx, android.Manifest.permission.POST_NOTIFICATIONS))
    Status("Microphone (test calls)", granted(ctx, android.Manifest.permission.RECORD_AUDIO))
    Status("Calendar (optional)", granted(ctx, android.Manifest.permission.READ_CALENDAR))
    Button(onClick = ask, Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) { Text("Allow") }
}

@Composable fun KeyField() {
    val ctx = context()
    var key by remember { mutableStateOf(Settings.now.key) }
    var result by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    OutlinedTextField(key, { key = it.trim(); Settings.update { p -> p.copy(key = key) }; result = null },
        Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }) { Text("Get a free key") }
        Button(enabled = key.isNotBlank() && !checking, onClick = {
            checking = true; result = null
            LiveSession.check(ctx) { r -> checking = false; result = r ?: "ok" }
        }) { Text(if (checking) "Checking…" else "Check key") }
    }
    result?.let {
        Text(if (it == "ok") "Key works. Kivi can talk." else it, Modifier.padding(top = 8.dp),
            color = if (it == "ok") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun KeyStep() {
    Heading("Gemini key", "Kivi's voice is Google Gemini Live, free with your own key. On the free tier Google may use call audio to improve its products; your notes stay on this phone.")
    KeyField()
}

@Composable fun AboutYouFields() {
    var owner by remember { mutableStateOf(Settings.now.owner) }
    var status by remember { mutableStateOf(Settings.now.status) }
    OutlinedTextField(owner, { owner = it; Settings.update { p -> p.copy(owner = it.trim()) } }, Modifier.fillMaxWidth(),
        label = { Text("Your name") }, supportingText = { Text("Kivi says \"you've reached ${owner.ifBlank { "…" }}'s assistant\"") }, singleLine = true)
    OutlinedTextField(status, { status = it; Settings.update { p -> p.copy(status = it.trim()) } }, Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text("What saved contacts may hear (optional)") }, supportingText = { Text("e.g. In a meeting till 5. Strangers never hear this.") })
}

@Composable private fun AboutYou() {
    Heading("About you", "Callers hear your name. Only your saved contacts hear where you are.")
    AboutYouFields()
}

@Composable private fun TryIt(onTest: () -> Unit) {
    val ctx = context()
    val blocker = Health.blocker(ctx)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KiviBadge(Mood.TALKING, 160.dp, Modifier.padding(top = 16.dp))
        Text("Hear what callers hear", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall)
        Text("A test call uses your phone's mic and speaker. Try speaking Telugu or Hindi. Kivi switches with you.",
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onTest, Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) { Text("Start a test call") }
        if (blocker != null) Text("Before Kivi can answer real calls: $blocker. You can fix it later in Settings › Diagnostics.",
            Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
