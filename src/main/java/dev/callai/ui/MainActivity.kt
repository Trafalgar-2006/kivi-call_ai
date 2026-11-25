package dev.callai.ui

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import dev.callai.data.Settings

sealed interface Screen {
    data object Home : Screen
    data class Call(val id: Long) : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
    data object Test : Screen
    data object Live : Screen
}

class MainActivity : ComponentActivity() {
    // Settings > Lock Kivi: notes are private conversations, so the app asks for your fingerprint or screen lock
    // when opened, and again after a minute away.
    private val unlocked = mutableStateOf(false)
    private var leftAt = 0L
    private var asking = false // a prompt is up: don't stack another; cancelling it leaves the Unlock button

    override fun onResume() {
        super.onResume()
        if (!Settings.now.lock) unlocked.value = true
        else if (!asking && (!unlocked.value || System.currentTimeMillis() - leftAt > 60_000)) { unlocked.value = false; unlock() }
    }

    override fun onPause() { super.onPause(); if (!asking) leftAt = System.currentTimeMillis() }

    private fun unlock() {
        val ways = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // No fingerprint and no screen lock set up: there is nothing to lock with, so don't lock you out.
        if (getSystemService(BiometricManager::class.java).canAuthenticate(ways) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked.value = true; return
        }
        asking = true
        BiometricPrompt.Builder(this).setTitle("Unlock Kivi").setAllowedAuthenticators(ways).build()
            .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { asking = false; unlocked.value = true }
                override fun onAuthenticationError(code: Int, message: CharSequence) { asking = false }
            })
    }

    override fun onCreate(state: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(state)
        // Notification taps open the call note or the diagnostics screen directly.
        val start = buildList {
            add(Screen.Home)
            intent.getLongExtra("call", 0).takeIf { it > 0 }?.let { add(Screen.Call(it)) }
            when (intent.getStringExtra("screen")) {
                "diagnostics" -> add(Screen.Diagnostics)
                "live" -> add(Screen.Live)
            }
        }
        setContent {
            KiviTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (unlocked.value) App(start) else Locked(::unlock)
                }
            }
        }
    }
}

@Composable
private fun Locked(unlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        KiviBadge(Mood.OFF, 140.dp)
        Text("Kivi is locked", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall)
        Text("Your call notes are private. Calls are still answered while it's locked.", Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = unlock, Modifier.padding(top = 20.dp).heightIn(min = 48.dp)) { Text("Unlock") }
    }
}

@Composable
private fun App(start: List<Screen>) {
    val prefs by Settings.state.collectAsState()
    var stack by remember { mutableStateOf(start) }
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { back() }

    val screen = stack.last()
    // Setup runs until finished; its test call is the one screen allowed on top of it.
    val showSetup = !prefs.onboarded && screen != Screen.Test
    AnimatedContent(if (showSetup) null else screen, transitionSpec = { fadeIn(tween(MOTION_MS)) togetherWith fadeOut(tween(MOTION_MS)) }, label = "nav") { s ->
        when (s) {
            null -> SetupScreen(::go)
            Screen.Home -> HomeScreen(::go)
            is Screen.Call -> CallScreen(s.id, ::back)
            Screen.Settings -> SettingsScreen(::go, ::back)
            Screen.Diagnostics -> DiagnosticsScreen(::go, ::back)
            Screen.Test -> TestCallScreen(::back)
            Screen.Live -> LiveCallScreen(::back)
        }
    }
}
