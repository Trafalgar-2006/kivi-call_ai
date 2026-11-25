package dev.callai.ai

import android.content.Context
import android.telephony.SmsManager
import dev.callai.data.Store

private const val TEXT_GAP_MS = 12 * 3_600_000L

// The caller's words shape the reply and it goes out from your own number (to a number that may be spoofed), so it
// never carries a link, a phone number or a payment handle, whatever the caller talked the note writer into.
private val UNSAFE = Regex("""(?i)https?://|www\.|\b[a-z0-9-]+\.(com|in|ly|net|org|co|me|io|link|xyz|info|app)\b|\d{5,}|@""")

/** Text Kivi's drafted reply only to real callers: never spam, never hidden numbers, never twice in 12 hours. */
fun shouldText(category: String, number: String, reply: String, enabled: Boolean, lastSent: Long, now: Long): Boolean =
    enabled && reply.isNotBlank() && !UNSAFE.containsMatchIn(reply) && category != "blocked" &&
        number.count(Char::isDigit) >= 10 && now - lastSent > TEXT_GAP_MS

/**
 * The number as WhatsApp's wa.me link wants it: country code and digits. A 10-digit number is Indian (+91); a short
 * code has no WhatsApp, so null.
 */
fun waNumber(number: String): String? {
    val d = number.filter(Char::isDigit)
    return when {
        d.length == 10 -> "91$d"
        d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
        d.length >= 11 -> d
        else -> null
    }
}

object AutoReply {
    /** Sends by SMS from the owner's own number. Returns whether it was handed to the phone's SMS service. */
    fun send(ctx: Context, number: String, text: String): Boolean = runCatching {
        val sms = ctx.getSystemService(SmsManager::class.java)
        sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
        true
    }.onFailure { Store.log("Couldn't text the reply: ${it.message}") }.getOrDefault(false)
}
