package com.noteVE.reminder

import android.content.Context
import com.noteVE.data.NoteDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A small write-ahead journal; Room remains at v5. Only the service actor writes this file. */
internal data class Occurrence(
    val noteId: Long,
    val expectedAt: Long,
    val title: String,
    val rule: String?,
    val nextAt: Long?,
    val notificationId: Int,
    val phase: String = CLAIMED,
    val resumeAt: Long = 0L
) {
    val key: String get() = "$noteId:$expectedAt"
    companion object {
        const val CLAIMED = "claimed"
        const val RINGING = "ringing"
        const val MUTED = "muted"
        const val SNOOZED = "snoozed"
    }
}

internal class OccurrenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_v3_occurrences", Context.MODE_PRIVATE)
    private val db = NoteDatabase.get(context)

    fun load(): MutableList<Occurrence> {
        // Do not silently discard corrupt state and risk consuming a future occurrence.
        val array = JSONArray(prefs.getString("active", "[]"))
        return (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            Occurrence(o.getLong("note"), o.getLong("at"), o.getString("title"),
                if (o.isNull("rule")) null else o.getString("rule"),
                if (o.isNull("next")) null else o.getLong("next"),
                o.getInt("notification"), o.getString("phase"), o.optLong("resume"))
        }.toMutableList()
    }

    fun save(rows: Collection<Occurrence>) {
        val array = JSONArray()
        rows.forEach { r ->
            array.put(JSONObject().put("note", r.noteId).put("at", r.expectedAt)
                .put("title", r.title).put("rule", r.rule ?: JSONObject.NULL)
                .put("next", r.nextAt ?: JSONObject.NULL).put("notification", r.notificationId)
                .put("phase", r.phase).put("resume", r.resumeAt))
        }
        check(prefs.edit().putString("active", array.toString()).commit()) { "Occurrence journal write failed" }
    }

    fun consumed(noteId: Long, at: Long): Boolean =
        prefs.getBoolean("done_${noteId}_$at", false) ||
            at <= prefs.getLong("consumed_$noteId", Long.MIN_VALUE) // migration of the early v3 journal

    /** Exact token tombstones: dismissing a newer repeat must not consume an older snooze. */
    fun markConsumed(row: Occurrence) {
        check(prefs.edit().putBoolean("done_${row.noteId}_${row.expectedAt}", true).commit())
    }

    fun allocateId(): Int {
        val id = prefs.getInt("notification_sequence", 30_000)
        check(id < Int.MAX_VALUE - 1) { "Notification IDs exhausted" }
        check(prefs.edit().putInt("notification_sequence", id + 1).commit())
        return id
    }

    suspend fun note(id: Long) = db.noteDao().getById(id)

    /** Field-only CAS avoids overwriting concurrent text edits; no DAO/schema changes needed. */
    suspend fun advance(row: Occurrence): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val note = db.noteDao().getById(row.noteId) ?: return@withTransaction false
            if (note.reminderAt != row.expectedAt) {
                // Recovery of the journal after the SQL committed but before phase=ringing.
                return@withTransaction note.reminderAt == row.nextAt &&
                    note.repeatRule == (if (row.nextAt == null) null else row.rule)
            }
            if (note.repeatRule != row.rule) return@withTransaction false
            val statement = db.openHelper.writableDatabase.compileStatement(
                "UPDATE notes SET reminderAt = ?, repeatRule = ? WHERE id = ? AND reminderAt = ?"
            )
            try {
                if (row.nextAt == null) statement.bindNull(1) else statement.bindLong(1, row.nextAt)
                if (row.nextAt == null || row.rule == null) statement.bindNull(2) else statement.bindString(2, row.rule)
                statement.bindLong(3, row.noteId)
                statement.bindLong(4, row.expectedAt)
                statement.executeUpdateDelete() == 1
            } finally { statement.close() }
        }
    }
}
