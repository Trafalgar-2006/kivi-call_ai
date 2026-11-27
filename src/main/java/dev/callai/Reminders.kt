package dev.callai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.callai.data.CallNote
import dev.callai.data.Store

/**
 * "Call me tomorrow at 11", or "after you're free": a notification at that time, with no tap needed beforehand.
 * The time lives in the note, so a reboot or an update just schedules them all again.
 */
object Reminders {
    fun schedule(ctx: Context, note: CallNote) {
        if (note.callbackAt <= System.currentTimeMillis() || note.id <= 0) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, note.id.toInt(), Intent(ctx, ReminderReceiver::class.java).putExtra("id", note.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.callbackAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.callbackAt, pi) // within a few minutes
    }

    fun scheduleAll(ctx: Context) = Store.calls.value.forEach { schedule(ctx, it) }
}

/** Not exported: only Kivi's own alarms reach it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val note = Store.calls.value.firstOrNull { it.id == intent.getLongExtra("id", -1) } ?: return // the note was deleted
        Notify.reminder(ctx, note)
    }
}
