package dev.callai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.callai.data.CATEGORY_LABELS
import dev.callai.data.CallNote
import dev.callai.ui.MainActivity

object Notify {
    private const val LIVE = 2
    private const val EMERGENCY = 3
    private const val RINGING = 6
    private const val WANTS = 8

    private fun post(ctx: Context, id: Int, channel: String, channelName: String, importance: Int, title: String, text: String,
                     extra: Intent.() -> Unit = {}, more: Notification.Builder.() -> Unit = {}) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel, channelName, importance))
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).apply(extra)
        val open = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(id, Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_kivi)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply(more)
            .build())
    }

    private fun action(ctx: Context, name: String, label: String): Notification.Action {
        val intent = Intent(ctx, ActionReceiver::class.java).setAction(name)
        val pi = PendingIntent.getBroadcast(ctx, name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, label, pi).build()
    }

    fun call(ctx: Context, note: CallNote) = post(ctx, note.time.toInt(), "calls", "Call notes", NotificationManager.IMPORTANCE_HIGH,
        "${CATEGORY_LABELS[note.category]}: ${note.name}", note.reason, { putExtra("call", note.id) })

    fun problem(ctx: Context, title: String, text: String) = post(ctx, 1, "health", "Kivi problems", NotificationManager.IMPORTANCE_DEFAULT,
        title, text.replaceFirstChar { it.uppercase() }, { putExtra("screen", "diagnostics") })

    /** While Kivi is on a call: what's being said, plus one-tap instructions. Updates quietly. */
    fun live(ctx: Context, who: String, latest: String) = post(ctx, LIVE, "live", "Calls Kivi is taking", NotificationManager.IMPORTANCE_HIGH,
        "Kivi is talking to $who", latest.ifBlank { "Listening…" }, { putExtra("screen", "live") }) {
        setOngoing(true); setOnlyAlertOnce(true); setAutoCancel(false); setCategory(Notification.CATEGORY_CALL)
        listOf(LiveAction.TAKE, LiveAction.TEXT, LiveAction.BLOCK).forEach { addAction(action(ctx, it.name, it.label)) }
    }

    fun endLive(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(LIVE)

    fun waiting(ctx: Context, who: String) = post(ctx, 4, "waiting", "Calls while Kivi is busy", NotificationManager.IMPORTANCE_HIGH,
        "$who is calling", "Kivi is on another call, so this one rings for you.")

    /** The 9 pm summary of the day's calls; opens the call list. */
    fun digest(ctx: Context, title: String, text: String) = post(ctx, 5, "digest", "Evening summary", NotificationManager.IMPORTANCE_DEFAULT,
        title, text)

    /** A call ringing for you: one tap hands it to Kivi instead (even a friend's, even with Kivi off). */
    fun ringing(ctx: Context, who: String) = post(ctx, RINGING, "ringing", "Calls ringing for you", NotificationManager.IMPORTANCE_HIGH,
        "$who is calling", "Can't talk? Kivi can take it.") {
        setCategory(Notification.CATEGORY_CALL); setTimeoutAfter(60_000)
        addAction(action(ctx, ActionReceiver.SEND_TO_KIVI, "Send to Kivi"))
    }

    fun endRinging(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(RINGING)

    /** The caller asked for you; the screen flashes too (Flash). */
    fun wantsYou(ctx: Context, who: String, urgent: Boolean) = post(ctx, WANTS, "wants", "Callers asking for you", NotificationManager.IMPORTANCE_HIGH,
        if (urgent) "Urgent: $who wants to talk to you" else "$who wants to talk to you", "Kivi is still on the call. Take it, or let Kivi take a message.",
        { putExtra("screen", "live") }) {
        setCategory(Notification.CATEGORY_CALL); setColor(if (urgent) 0xFFE53935.toInt() else 0xFF1E88E5.toInt()); setTimeoutAfter(3 * 60_000)
        addAction(action(ctx, LiveAction.TAKE.name, "Take the call"))
    }

    /** The callback time a caller asked for (or when you became free) has come. Opens the note, with Call back on it. */
    fun reminder(ctx: Context, note: CallNote) = post(ctx, 100_000 + note.id.toInt(), "reminders", "Callback reminders", NotificationManager.IMPORTANCE_HIGH,
        "Call back ${note.name}", note.reason, { putExtra("call", note.id) }) { setCategory(Notification.CATEGORY_REMINDER) }

    fun emergency(ctx: Context, who: String, reason: String, live: Boolean) = post(ctx, EMERGENCY, "emergency", "Emergencies",
        NotificationManager.IMPORTANCE_HIGH, "Emergency: $who", reason, { putExtra("screen", if (live) "live" else "home") }) {
        setCategory(Notification.CATEGORY_ALARM)
        if (live) addAction(action(ctx, LiveAction.TAKE.name, "Take the call"))
        addAction(action(ctx, ActionReceiver.STOP_ALARM, "Stop alarm"))
        setDeleteIntent(PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.STOP_ALARM), PendingIntent.FLAG_IMMUTABLE))
    }
}
