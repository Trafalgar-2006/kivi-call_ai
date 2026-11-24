package dev.callai.calls

import android.content.ContentUris
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract.PhoneLookup
import java.util.Calendar

// What the phone knows at the moment a call rings. Each lookup fails closed: no permission = no match.

/** The saved contact's name, or null for a stranger (or no contacts permission). */
fun contactName(ctx: Context, number: String): String? {
    if (number.isBlank()) return null
    return runCatching {
        ctx.contentResolver.query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}

/** A busy, non-all-day calendar event is happening right now. */
fun inMeeting(ctx: Context, now: Long): Boolean = runCatching {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 1) }.build()
    ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.ALL_DAY), null, null, null)?.use { c ->
        generateSequence { if (c.moveToNext()) c.getInt(0) to c.getInt(1) else null }
            .any { (availability, allDay) -> availability == CalendarContract.Events.AVAILABILITY_BUSY && allDay == 0 }
    } ?: false
}.getOrDefault(false)

private val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)

/** Names of Bluetooth audio devices connected right now (a car's head unit shows up here). */
fun bluetoothAudio(ctx: Context): List<String> =
    ctx.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in BLUETOOTH }.map { it.productName.toString() }.filter { it.isNotBlank() }.distinct()

fun minuteOfDay(now: Long): Int = Calendar.getInstance().apply { timeInMillis = now }
    .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

fun situation(ctx: Context, config: Config, now: Long = System.currentTimeMillis()) = Situation(
    now = now,
    minuteOfDay = minuteOfDay(now),
    inMeeting = config.busyCalendar && inMeeting(ctx, now),
    carConnected = config.carDevice.isNotBlank() && config.carDevice in bluetoothAudio(ctx),
)

/** For logs that may be shared: never the whole number. */
fun mask(number: String) = if (number.isBlank()) "hidden number" else "•••" + number.filter(Char::isDigit).takeLast(4)
