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
        Settings.onChange = { ModeWidget.refresh(this) }
    }
}
