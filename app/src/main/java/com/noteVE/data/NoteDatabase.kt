package com.noteVE.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun get(context: Context): NoteDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notes.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // v2.1.0：不再创建 FTS 虚拟表与触发器（详见 MIGRATION_4_5 说明）。
                    // 搜索统一走 title/plainText 的 LIKE 子串匹配。
                    .build()
                    .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN voiceRecords TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN lastOpenedAt INTEGER NOT NULL DEFAULT 0")
                // 旧 HTML 正文 + 附件列表 → 块 JSON
                val cursor = db.query("SELECT id, body, imagePaths, voiceRecords FROM notes")
                val updates = mutableListOf<Pair<Long, String>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val html = cursor.getString(1) ?: ""
                    val images = parseList(cursor.getString(2))
                    val audios = parseList(cursor.getString(3))
                    val blocks = mutableListOf<com.noteVE.domain.Block>()
                    if (html.isNotBlank()) blocks.add(com.noteVE.domain.Block.Text(html))
                    images.forEach { blocks.add(com.noteVE.domain.Block.Image(it)) }
                    audios.forEach { blocks.add(com.noteVE.domain.Block.Audio(it)) }
                    updates.add(id to com.noteVE.domain.BlockSerializer.toJson(blocks))
                }
                cursor.close()
                for ((id, json) in updates) {
                    db.execSQL("UPDATE notes SET body = ? WHERE id = ?", arrayOf(json, id))
                }
            }

            private fun parseList(json: String?): List<String> {
                if (json.isNullOrBlank()) return emptyList()
                return try {
                    val arr = org.json.JSONArray(json)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (e: Exception) { emptyList() }
            }
        }

        /** 3→4：新增 filePaths（任意文件块路径）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN filePaths TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * 4→5：**移除 FTS4 虚拟表与同步触发器**。
         *
         * 决策依据（方案 B）：项目实际搜索走 `title/plainText` 的 **LIKE 子串匹配**，
         * FTS 从未进入业务链路。而 FTS4 默认 tokenizer 按空白分词，
         * **中文整段会成为一个 token，子串搜索直接失效** —— 本项目以中文为主，
         * 保留 FTS 只会带来写入开销与结构复杂度，没有实际收益。
         * 故彻底移除，搜索统一走 LIKE。（备份/导出/恢复不受影响：
         * 这些流程只操作 `notes` 表，不依赖 FTS。）
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TRIGGER IF EXISTS notes_ai")
                db.execSQL("DROP TRIGGER IF EXISTS notes_ad")
                db.execSQL("DROP TRIGGER IF EXISTS notes_au")
                db.execSQL("DROP TABLE IF EXISTS note_fts")
            }
        }
    }
}
