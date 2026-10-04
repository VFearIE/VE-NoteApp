package com.noteVE.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Query("SELECT id FROM notes WHERE title = :title AND createdAt = :createdAt LIMIT 1")
    suspend fun findExisting(title: String, createdAt: Long): Long?

    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<Note>>

    /** 子串匹配（LIKE）检索标题与正文，多线程由 Room 查询执行器保证。 */
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :q || '%' OR plainText LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND reminderAt > :now")
    suspend fun getUpcomingReminders(now: Long): List<Note>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL")
    suspend fun getAllWithReminders(): List<Note>

    /** 最近的一条待办提醒（含刚过期的，用于补发）。 */
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL ORDER BY reminderAt ASC LIMIT 1")
    suspend fun nextDueReminder(): Note?
}
