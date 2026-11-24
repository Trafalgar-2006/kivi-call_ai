package dev.callai.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.callai.Fix
import dev.callai.Health
import dev.callai.ai.LiveSession
import dev.callai.audio.Bridge
import dev.callai.data.Store
import rikka.shizuku.Shizuku

/** Everything Kivi needs to answer safely, each with its fix, plus the recent activity log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val ctx = context()
    val tick = ticker(2_000)
    val checks = remember(tick) { Health.checks(ctx) }
    val events = remember(tick) { Store.events() }
    val ask = rememberPermissionAsker()
    var gemini by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Diagnostics") }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        }, actions = {
            IconButton(onClick = {
                val text = events.joinToString("\n") { (t, e) -> "${DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)}  $e" }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share Kivi log"))
            }) { Icon(Icons.Rounded.Share, contentDescription = "Share log") }
        })
    }) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                val blocker = checks.firstOrNull { it.critical && !it.ok }
                Text(if (blocker == null) "Kivi is ready to answer calls." else "Calls ring normally until this is fixed: ${blocker.problem}.",
                    Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
            }
            items(checks) { c ->
                ListItem(
                    leadingContent = {
                        Icon(when { c.ok -> Icons.Rounded.CheckCircle; c.critical -> Icons.Rounded.Error; else -> Icons.Rounded.Warning },
                            contentDescription = if (c.ok) "OK" else "Problem",
                            tint = when { c.ok -> Paint.Leaf; c.critical -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary })
                    },
                    headlineContent = { Text(c.label) },
                    supportingContent = if (c.ok) null else ({ Text(c.problem.replaceFirstChar { it.uppercase() }) }),
                    trailingContent = if (c.ok || c.fix == Fix.NONE) null else ({
                        TextButton(onClick = {
                            when (c.fix) {
                                Fix.OPEN_SHIZUKU -> ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let(ctx::startActivity)
                                Fix.ALLOW_SHIZUKU -> if (Bridge.running()) Shizuku.requestPermission(1)
                                Fix.PERMISSIONS -> ask()
                                Fix.ADD_KEY -> go(Screen.Settings)
                                Fix.NONE -> Unit
                            }
                        }) { Text("Fix") }
                    }),
                )
            }
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { gemini = "Checking…"; LiveSession.check(ctx) { gemini = it ?: "Gemini answered. Voice is working." } }) {
                        Text("Test Gemini connection")
                    }
                    gemini?.let { Text(it, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item { SectionTitle("Recent activity") }
            if (events.isEmpty()) item { Text("Nothing yet.", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(events) { (t, e) ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_TIME), Modifier.width(76.dp),
                        style = Tabular.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(e, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}
