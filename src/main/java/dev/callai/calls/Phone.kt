package dev.callai.calls

import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.PhoneLookup
import dev.callai.data.Store
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

/** Who a saved contact is to you, from your contacts: "labelled Family, Gym; nickname Chintu; starred as a favourite". */
fun contactInfo(ctx: Context, number: String): String? = runCatching {
    val r = ctx.contentResolver
    val (id, starred) = r.query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
        arrayOf(PhoneLookup._ID, PhoneLookup.STARRED), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) to (it.getInt(1) == 1) else null }
        ?: return null
    val groups = mutableListOf<Long>(); var nick: String? = null
    r.query(Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1), "${Data.CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)",
        arrayOf(id.toString(), GroupMembership.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE), null)?.use { c ->
        while (c.moveToNext()) if (c.getString(0) == Nickname.CONTENT_ITEM_TYPE) nick = c.getString(1) else c.getString(1)?.toLongOrNull()?.let(groups::add)
    }
    // Your own labels and Google's Friends/Family/Coworkers; not the automatic "My Contacts" or "Starred".
    val labels = if (groups.isEmpty()) emptyList() else r.query(Groups.CONTENT_URI, arrayOf(Groups.TITLE),
        "${Groups._ID} IN (${groups.joinToString(",")}) AND (${Groups.SYSTEM_ID} IS NULL OR ${Groups.SYSTEM_ID} IN ('Friends','Family','Coworkers')) AND ${Groups.TITLE} NOT IN ('My Contacts','Starred in Android')",
        null, null)?.use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.filterNotNull().distinct().toList() }.orEmpty()
    listOfNotNull(labels.takeIf { it.isNotEmpty() }?.joinToString(", ", "labelled "), nick?.takeIf { it.isNotBlank() }?.let { "nickname $it" },
        "starred as a favourite".takeIf { starred }).joinToString("; ").ifBlank { null }
}.getOrNull()

/** A busy, non-all-day calendar event is happening right now. */
fun inMeeting(ctx: Context, now: Long): Boolean = runCatching {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 1) }.build()
    ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.ALL_DAY), null, null, null)?.use { c ->
        generateSequence { if (c.moveToNext()) c.getInt(0) to c.getInt(1) else null }
            .any { (availability, allDay) -> availability == CalendarContract.Events.AVAILABILITY_BUSY && allDay == 0 }
    } ?: false
}.getOrDefault(false)

/**
 * When you're next free, if a busy calendar event is on right now: the end of the run of busy events (gaps under
 * 10 minutes don't count). Null when you're not in one, or Kivi can't read the calendar.
 */
fun busyUntil(ctx: Context, now: Long): Long? = runCatching {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 12 * 3_600_000L) }.build()
    val busy = ctx.contentResolver.query(uri, arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
        CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.ALL_DAY), null, null, CalendarContract.Instances.BEGIN)?.use { c ->
        buildList { while (c.moveToNext()) if (c.getInt(2) == CalendarContract.Events.AVAILABILITY_BUSY && c.getInt(3) == 0) add(c.getLong(0) to c.getLong(1)) }
    }.orEmpty()
    freeAt(busy, now)
}.getOrNull()

/** The end of the busy run covering [now], from (begin, end) pairs; null if nothing covers it. */
fun freeAt(events: List<Pair<Long, Long>>, now: Long): Long? {
    var end = events.filter { it.first <= now && it.second > now }.maxOfOrNull { it.second } ?: return null
    for ((begin, stop) in events.sortedBy { it.first }) if (begin <= end + 10 * 60_000L && stop > end) end = stop
    return end
}

private val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)

/** Names of Bluetooth audio devices connected right now (a car's head unit shows up here). */
fun bluetoothAudio(ctx: Context): List<String> =
    ctx.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in BLUETOOTH }.map { it.productName.toString() }.filter { it.isNotBlank() }.distinct()

fun minuteOfDay(now: Long): Int = Calendar.getInstance().apply { timeInMillis = now }
    .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

fun situation(ctx: Context, config: Config, now: Long = System.currentTimeMillis(), number: String = "") = Situation(
    now = now,
    minuteOfDay = minuteOfDay(now),
    inMeeting = config.busyCalendar && inMeeting(ctx, now),
    carConnected = config.carDevice.isNotBlank() && config.carDevice in bluetoothAudio(ctx),
    dnd = config.followDnd && ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        .let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN },
    // Kivi judged this number's last two calls (newest first) as spam.
    knownSpam = number.isNotBlank() && Store.calls.value.filter { sameNumber(it.number, number) }.take(2)
        .let { it.size == 2 && it.all { n -> n.category == "blocked" } },
)

/** For logs that may be shared: never the whole number. */
fun mask(number: String) = if (number.isBlank()) "hidden number" else "•••" + number.filter(Char::isDigit).takeLast(4)
