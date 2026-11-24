package dev.callai.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.callai.Notify
import dev.callai.audio.Bridge
import dev.callai.data.Settings

/** Quick-settings tile: one tap to start or stop Drive. */
class DriveTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val on = System.currentTimeMillis() < Settings.now.config.driveUntil
        Settings.config { it.copy(driveUntil = if (on) 0 else FOREVER) }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = System.currentTimeMillis() < Settings.now.config.driveUntil
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (on) "Kivi answers" else "Off"
        tile.updateTile()
    }
}

/** After a restart Shizuku is usually stopped, so Kivi can't answer until it's started again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || Bridge.running()) return
        val c = Settings.now.config
        val anyMode = c.strangers || c.sleepSchedule || c.busyCalendar || c.carDevice.isNotBlank() ||
            listOf(c.sleepUntil, c.busyUntil, c.driveUntil, c.awayUntil).any { it > System.currentTimeMillis() }
        if (anyMode) Notify.problem(ctx, "Start Shizuku so Kivi can answer", "Your phone restarted. Until Shizuku is running, every call rings normally.")
    }
}
