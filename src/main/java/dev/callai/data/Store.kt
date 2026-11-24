package dev.callai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import dev.callai.audio.TAG
import dev.callai.calls.LastRing
import dev.callai.calls.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CallNote(
    val id: Long = 0,
    val time: Long,
    val number: String,
    val name: String,
    val reason: String,
    val category: String, // needs_you | handled | blocked
    val reply: String,
    val transcript: String, // lines prefixed "Caller: " / "Kivi: "
    val durationMs: Long,
    val mode: String,
    val sentAt: Long = 0, // when the reply was texted automatically; 0 = not sent
)

val CATEGORY_LABELS = mapOf("needs_you" to "Needs you", "handled" to "Handled", "blocked" to "Blocked")

private const val RINGS = "CREATE TABLE rings(number TEXT PRIMARY KEY, time INTEGER, outcome TEXT, in_a_row INTEGER)"

/** Calls, recent rings (for the "3 calls in a row" rule) and a capped diagnostics log, in Android's built-in SQLite. */
object Store {
    private lateinit var db: SQLiteDatabase
    private val _calls = MutableStateFlow<List<CallNote>>(emptyList())
    val calls: StateFlow<List<CallNote>> = _calls

    fun init(ctx: Context) {
        db = object : SQLiteOpenHelper(ctx, "kivi.db", null, 4) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE calls(id INTEGER PRIMARY KEY, time INTEGER, number TEXT, name TEXT, reason TEXT, category TEXT, reply TEXT, transcript TEXT, duration INTEGER, mode TEXT, sent INTEGER DEFAULT 0)")
                db.execSQL("CREATE INDEX calls_time ON calls(time)")
                db.execSQL(RINGS)
                db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY, time INTEGER, text TEXT)")
            }
            override fun onUpgrade(db: SQLiteDatabase, from: Int, to: Int) {
                // v2/v3 reshaped rings. Only minutes-old data lives there, so starting it fresh loses nothing.
                if (from < 3) { db.execSQL("DROP TABLE IF EXISTS rings"); db.execSQL(RINGS) }
                // v4: when Kivi's reply was texted. Notes are kept.
                if (from < 4) db.execSQL("ALTER TABLE calls ADD COLUMN sent INTEGER DEFAULT 0")
            }
        }.writableDatabase
        refresh()
    }

    private fun refresh() {
        _calls.value = db.rawQuery("SELECT id,time,number,name,reason,category,reply,transcript,duration,mode,sent FROM calls ORDER BY time DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) add(CallNote(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4),
                    c.getString(5), c.getString(6), c.getString(7), c.getLong(8), c.getString(9), c.getLong(10)))
            }
        }
    }

    fun lastSent(number: String): Long =
        db.rawQuery("SELECT MAX(sent) FROM calls WHERE number=?", arrayOf(number)).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    fun markSent(id: Long, time: Long) { db.execSQL("UPDATE calls SET sent=? WHERE id=?", arrayOf(time, id)); refresh() }

    fun add(n: CallNote): Long {
        val id = db.insert("calls", null, ContentValues().apply {
            put("time", n.time); put("number", n.number); put("name", n.name); put("reason", n.reason)
            put("category", n.category); put("reply", n.reply); put("transcript", n.transcript)
            put("duration", n.durationMs); put("mode", n.mode)
        })
        refresh()
        return id
    }

    fun delete(id: Long) { db.delete("calls", "id=?", arrayOf(id.toString())); refresh() }
    fun deleteAll() { db.delete("calls", null, null); refresh() }

    fun prune(keepDays: Int) {
        if (keepDays > 0) db.delete("calls", "time<?", arrayOf((System.currentTimeMillis() - keepDays * 86_400_000L).toString()))
        db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 300)")
        refresh()
    }

    private fun ringKey(number: String) = number.filter(Char::isDigit).takeLast(10)

    fun lastRing(number: String): LastRing? =
        db.rawQuery("SELECT time,outcome,in_a_row FROM rings WHERE number=?", arrayOf(ringKey(number))).use {
            if (it.moveToFirst()) LastRing(it.getLong(0), runCatching { Outcome.valueOf(it.getString(1)) }.getOrDefault(Outcome.RANG), it.getInt(2)) else null
        }

    fun rang(number: String, time: Long, outcome: Outcome, inARow: Int) {
        if (ringKey(number).isEmpty()) return
        db.insertWithOnConflict("rings", null, ContentValues().apply {
            put("number", ringKey(number)); put("time", time); put("outcome", outcome.name); put("in_a_row", inARow)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Once Kivi has judged a call, remember a block so a quick redial doesn't count as urgent. */
    fun judged(number: String, category: String) {
        if (category == "blocked") db.execSQL("UPDATE rings SET outcome=? WHERE number=?", arrayOf(Outcome.BLOCKED.name, ringKey(number)))
    }

    /** Diagnostics trail. Never put full numbers or transcripts here; it can be shared. */
    fun log(text: String) {
        Log.i(TAG, text)
        db.insert("events", null, ContentValues().apply { put("time", System.currentTimeMillis()); put("text", text) })
    }

    fun events(): List<Pair<Long, String>> =
        db.rawQuery("SELECT time,text FROM events ORDER BY id DESC LIMIT 100", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }
}
