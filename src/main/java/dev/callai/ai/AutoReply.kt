package dev.callai.ai

import android.content.Context
import android.telephony.SmsManager
import dev.callai.data.Store

private const val TEXT_GAP_MS = 12 * 3_600_000L

/** Text Kivi's drafted reply only to real callers: never spam, never hidden numbers, never twice in 12 hours. */
fun shouldText(category: String, number: String, reply: String, enabled: Boolean, lastSent: Long, now: Long): Boolean =
    enabled && reply.isNotBlank() && category != "blocked" && number.count(Char::isDigit) >= 10 && now - lastSent > TEXT_GAP_MS

object AutoReply {
    /** Sends by SMS from the owner's own number. Returns whether it was handed to the phone's SMS service. */
    fun send(ctx: Context, number: String, text: String): Boolean = runCatching {
        val sms = ctx.getSystemService(SmsManager::class.java)
        sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
        true
    }.onFailure { Store.log("Couldn't text the reply: ${it.message}") }.getOrDefault(false)
}
