package dev.diego.expanda.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

class ExpandaDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE snippets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                shortcut TEXT NOT NULL,
                content TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                folder TEXT NOT NULL DEFAULT 'General',
                tags TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                case_sensitive INTEGER NOT NULL DEFAULT 0,
                trigger_mode TEXT NOT NULL DEFAULT 'DELIMITER',
                delimiters TEXT NOT NULL,
                excluded_packages TEXT NOT NULL DEFAULT '',
                usage_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                templates TEXT NOT NULL DEFAULT '',
                selection_mode TEXT NOT NULL DEFAULT 'FIRST',
                template_index INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX idx_snippet_shortcut ON snippets(shortcut COLLATE NOCASE)")
        db.execSQL(
            """
            CREATE TABLE expansion_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                snippet_id INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                expanded_at INTEGER NOT NULL,
                FOREIGN KEY(snippet_id) REFERENCES snippets(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_expansion_log_time ON expansion_log(expanded_at DESC)")
        db.execSQL(
            """
            CREATE TABLE clipboard_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_clipboard_time ON clipboard_history(pinned DESC, created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Keep the v1 content column as the first template. The empty
            // default means old rows need no data rewrite and remain readable
            // even if an upgrade is interrupted between individual ALTERs.
            db.execSQL("ALTER TABLE snippets ADD COLUMN templates TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE snippets ADD COLUMN selection_mode TEXT NOT NULL DEFAULT 'FIRST'")
            db.execSQL("ALTER TABLE snippets ADD COLUMN template_index INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE snippets ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            // A folder represented one classification, so it becomes the first tag.
            db.execSQL("UPDATE snippets SET tags = TRIM(folder) WHERE TRIM(folder) <> ''")
        }
    }

    fun readSnippets(): List<Snippet> = readableDatabase.query(
        "snippets", null, null, null, null, null, "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Snippet(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        shortcut = cursor.getString(cursor.getColumnIndexOrThrow("shortcut")),
                        content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                        label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                        tags = decodeSet(cursor.getString(cursor.getColumnIndexOrThrow("tags"))),
                        enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
                        caseSensitive = cursor.getInt(cursor.getColumnIndexOrThrow("case_sensitive")) == 1,
                        triggerMode = TriggerMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("trigger_mode"))),
                        delimiters = cursor.getString(cursor.getColumnIndexOrThrow("delimiters")),
                        excludedPackages = decodeSet(cursor.getString(cursor.getColumnIndexOrThrow("excluded_packages"))),
                        usageCount = cursor.getLong(cursor.getColumnIndexOrThrow("usage_count")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        templates = decodeTemplates(cursor.getString(cursor.getColumnIndexOrThrow("templates"))),
                        selectionMode = runCatching {
                            TemplateSelectionMode.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow("selection_mode")),
                            )
                        }.getOrDefault(TemplateSelectionMode.FIRST),
                        templateIndex = cursor.getLong(cursor.getColumnIndexOrThrow("template_index")),
                    ),
                )
            }
        }
    }

    fun upsert(snippet: Snippet): Long {
        val values = snippet.toValues()
        return if (snippet.id == 0L) {
            writableDatabase.insertOrThrow("snippets", null, values)
        } else {
            writableDatabase.update("snippets", values, "id = ?", arrayOf(snippet.id.toString()))
            snippet.id
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("snippets", "id = ?", arrayOf(id.toString()))
    }

    fun recordExpansion(snippetId: Long, packageName: String, collectStatistics: Boolean = true) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL(
                "UPDATE snippets SET usage_count = usage_count + ?, " +
                    "template_index = CASE WHEN selection_mode = 'SEQUENTIAL' " +
                    "THEN template_index + 1 ELSE template_index END WHERE id = ?",
                arrayOf(if (collectStatistics) 1 else 0, snippetId),
            )
            if (collectStatistics) {
                writableDatabase.insertOrThrow(
                    "expansion_log", null,
                    ContentValues().apply {
                        put("snippet_id", snippetId)
                        put("package_name", packageName)
                        put("expanded_at", System.currentTimeMillis())
                    },
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun readClipboardHistory(): List<ClipboardEntry> = readableDatabase.query(
        "clipboard_history", null, null, null, null, null, "pinned DESC, created_at DESC", "200",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                ClipboardEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1,
                ),
            )
        }
    }

    fun addClipboardText(text: String) {
        if (text.isBlank()) return
        val latest = readableDatabase.query(
            "clipboard_history", arrayOf("text"), null, null, null, null, "created_at DESC", "1",
        ).use { if (it.moveToFirst()) it.getString(0) else null }
        if (latest == text) return
        writableDatabase.insertOrThrow("clipboard_history", null, ContentValues().apply {
            put("text", text.take(MAX_CLIPBOARD_LENGTH))
            put("created_at", System.currentTimeMillis())
            put("pinned", false)
        })
        writableDatabase.execSQL(
            "DELETE FROM clipboard_history WHERE pinned = 0 AND id NOT IN " +
                "(SELECT id FROM clipboard_history ORDER BY created_at DESC LIMIT 200)",
        )
    }

    fun deleteClipboardEntry(id: Long) {
        writableDatabase.delete("clipboard_history", "id = ?", arrayOf(id.toString()))
    }

    fun clearClipboardHistory() {
        writableDatabase.delete("clipboard_history", "pinned = 0", null)
    }

    fun setClipboardPinned(id: Long, pinned: Boolean) {
        writableDatabase.update("clipboard_history", ContentValues().apply { put("pinned", pinned) }, "id = ?", arrayOf(id.toString()))
    }

    private fun Snippet.toValues() = ContentValues().apply {
        put("shortcut", shortcut.trim())
        put("content", content)
        put("label", label.trim())
        val normalizedTags = tags.map(String::trim).filter(String::isNotBlank).toSortedSet(String.CASE_INSENSITIVE_ORDER)
        // Keep the legacy column populated for older external database readers.
        put("folder", normalizedTags.firstOrNull() ?: "General")
        put("tags", normalizedTags.joinToString(SEPARATOR))
        put("enabled", enabled)
        put("case_sensitive", caseSensitive)
        put("trigger_mode", triggerMode.name)
        put("delimiters", delimiters)
        put("excluded_packages", excludedPackages.sorted().joinToString(SEPARATOR))
        put("usage_count", usageCount)
        put("created_at", createdAt)
        put("updated_at", System.currentTimeMillis())
        put("templates", encodeTemplates(templates))
        put("selection_mode", selectionMode.name)
        put("template_index", templateIndex)
    }

    companion object {
        private const val DATABASE_NAME = "expanda.db"
        private const val DATABASE_VERSION = 3
        private const val SEPARATOR = "\u001F"
        private const val MAX_CLIPBOARD_LENGTH = 100_000

        private fun decodeSet(value: String): Set<String> =
            value.split(SEPARATOR).filter(String::isNotBlank).toSet()

        private fun encodeTemplates(value: List<String>): String =
            JSONArray().apply { value.forEach(::put) }.toString()

        private fun decodeTemplates(value: String): List<String> {
            if (value.isBlank()) return emptyList()
            return runCatching {
                JSONArray(value).let { array ->
                    buildList(array.length()) {
                        for (index in 0 until array.length()) add(array.optString(index))
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
