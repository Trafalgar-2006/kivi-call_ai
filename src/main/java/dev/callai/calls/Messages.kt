package dev.callai.calls

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

private val CHAT_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging")
private const val KEEP_MS = 6 * 3_600_000L

/**
 * A saved contact's latest WhatsApp and SMS messages, read from notifications (Android's official way; nothing logs
 * into WhatsApp). Kept in memory only, for 6 hours, so Kivi can say "is this about the tickets?" when they call.
 */
class MessageListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in CHAT_APPS) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return // "3 new messages"
        val extras = n.extras
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)) return // group chats aren't the caller talking to you
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        Messages.add(sender, text, sbn.postTime)
    }

    companion object {
        fun allowed(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(ctx, MessageListener::class.java))
    }
}

object Messages {
    private val recent = HashMap<String, MutableList<Pair<Long, String>>>()

    /** Codes are never kept: an OTP must not reach a call, let alone a cloud model. */
    private val CODE = Regex("""(?i)\b(otp|code|pin|password|passcode)\b.*\b\d{4,8}\b|\b\d{4,8}\b.*\b(otp|code|pin)\b""")

    @Synchronized fun add(sender: String, text: String, time: Long) {
        if (text.isBlank() || CODE.containsMatchIn(text)) return
        val list = recent.getOrPut(sender.trim().lowercase()) { mutableListOf() }
        if (list.none { it.second == text }) list += time to text.take(200)
        list.removeAll { time - it.first > KEEP_MS }
        while (list.size > 5) list.removeAt(0)
    }

    /** Up to 3 messages this person sent in the last 6 hours, oldest first. */
    @Synchronized fun from(sender: String, now: Long): List<Pair<Long, String>> =
        recent[sender.trim().lowercase()].orEmpty().filter { now - it.first < KEEP_MS }.takeLast(3)
}
