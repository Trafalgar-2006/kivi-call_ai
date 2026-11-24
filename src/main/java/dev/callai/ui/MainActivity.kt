package dev.callai.ui

import android.os.Bundle
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
        setContent { KiviTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(start) } } }
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
