package dev.callai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import dev.callai.ai.LiveSession
import dev.callai.calls.CallWatcher
import dev.callai.data.Settings
import dev.callai.data.Store

/** What you can tell Kivi mid-call, from the live notification or the live screen. */
enum class LiveAction(val label: String) {
    TAKE("Take it"), TEXT("Text me instead"), CALL_BACK("Call back in 10 min"), BLOCK("Block as spam");

    fun perform() {
        val s = LiveSession.current.value ?: return
        val owner = Settings.now.owner.ifBlank { "The person they called" }
        Store.log("You chose \"$label\" during the call")
        when (this) {
            TAKE -> CallWatcher.active?.takeOver()
            TEXT -> s.direct("($owner asks the caller to send a text message instead. Tell them politely, note it, say goodbye and hang up.)")
            CALL_BACK -> s.direct("($owner will call back in about 10 minutes. Tell the caller, take any message, then say goodbye and hang up.)")
            BLOCK -> s.block("($owner says this caller is spam. Say a brief goodbye, note it as blocked, and hang up now.)")
        }
    }
}

/** Receives the live notification's buttons and the emergency alarm's Stop. Not exported: only our own notifications reach it. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == STOP_ALARM) return Emergency.quiet(ctx)
        runCatching { LiveAction.valueOf(intent.action.orEmpty()) }.getOrNull()?.perform()
    }
    companion object { const val STOP_ALARM = "STOP_ALARM" }
}

/**
 * The loud part of an emergency. Android muffles alarms during a call, so the alarm keeps looping after the call ends,
 * where it plays at full volume even on silent. It stops by itself after two minutes.
 */
object Emergency {
    private var player: MediaPlayer? = null
    private var restoreVolume = -1
    private val main = Handler(Looper.getMainLooper())

    fun sound(ctx: Context) { main.post { start(ctx) } }

    private fun start(ctx: Context) {
        if (player != null) return
        val am = ctx.getSystemService(AudioManager::class.java)
        restoreVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
        val tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(ctx, tone); isLooping = true; prepare(); start()
            }
        }.onFailure { Store.log("Couldn't sound the alarm: ${it.message}") }.getOrNull()
        main.postDelayed({ quiet(ctx) }, 2 * 60_000L)
    }

    fun quiet(ctx: Context) { main.post { stop(ctx) } }

    private fun stop(ctx: Context) {
        main.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        if (restoreVolume >= 0) runCatching {
            ctx.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, restoreVolume, 0)
        }
        restoreVolume = -1
    }
}
