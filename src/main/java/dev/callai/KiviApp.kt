package dev.callai

import android.app.Application
import dev.callai.ai.Fallback
import dev.callai.audio.Bridge
import dev.callai.calls.ModeWidget
import dev.callai.data.Settings
import dev.callai.data.Store

class KiviApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Store.init(this)
        Store.prune(Settings.now.keepDays)
        Bridge.init(this)
        Fallback.prepare(this)
        Daily.schedule(this)
        Reminders.scheduleAll(this) // alarms don't survive an update
        Settings.onChange = { ModeWidget.refresh(this) }
    }
}
