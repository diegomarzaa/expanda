package dev.diego.expanda.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class ExpandaDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createMatchTables(db)
        createClipboardTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE snippets ADD COLUMN templates TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE snippets ADD COLUMN selection_mode TEXT NOT NULL DEFAULT 'FIRST'")
            db.execSQL("ALTER TABLE snippets ADD COLUMN template_index INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE snippets ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE snippets SET tags = TRIM(folder) WHERE TRIM(folder) <> ''")
        }
        if (oldVersion < 5) migrateLegacyMatches(db)
    }

    fun readMatches(): List<TextMatch> = queryMatches(readableDatabase)

    fun upsert(match: TextMatch): Long = upsert(writableDatabase, match)

    /** Reconciles one Espanso file while preserving Android metadata and statistics. */
    fun replaceSourceMatches(sourceFile: String, incoming: List<TextMatch>): Int {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val existing = queryMatches(db).filter { it.sourceFile == sourceFile }
            val consumed = mutableSetOf<Long>()
            incoming.forEachIndexed { index, parsed ->
                val old = existing.firstOrNull {
                    it.id !in consumed && it.sourceMatchIndex == parsed.sourceMatchIndex
                } ?: existing.firstOrNull {
                    it.id !in consumed && it.trigger.equals(parsed.trigger, ignoreCase = true)
                }
                old?.id?.let(consumed::add)
                val merged = parsed.copy(
                    id = old?.id ?: 0,
                    templateIndex = old?.templateIndex ?: 0,
                    usageCount = old?.usageCount ?: 0,
                    createdAt = old?.createdAt ?: parsed.createdAt,
                    updatedAt = old?.updatedAt ?: parsed.updatedAt,
                    sourceFile = sourceFile,
                    sourceMatchIndex = parsed.sourceMatchIndex ?: index,
                )
                upsert(db, merged, touchUpdatedAt = old == null)
            }
            existing.filterNot { it.id in consumed }.forEach {
                db.delete("matches", "id = ?", arrayOf(it.id.toString()))
            }
            db.setTransactionSuccessful()
            incoming.size
        } finally {
            db.endTransaction()
        }
    }

    /** Imports as one transaction, replacing an equal primary trigger. */
    fun importMatches(incoming: List<TextMatch>): Int {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val existing = queryMatches(db).toMutableList()
            incoming.forEach { imported ->
                val current = existing.firstOrNull { it.trigger.equals(imported.trigger, ignoreCase = true) }
                val saved = imported.copy(
                    id = current?.id ?: 0,
                    usageCount = current?.usageCount ?: imported.usageCount,
                    createdAt = current?.createdAt ?: imported.createdAt,
                )
                val id = upsert(db, saved)
                existing.removeAll { it.id == id }
                existing += saved.copy(id = id)
            }
            db.setTransactionSuccessful()
            incoming.size
        } finally {
            db.endTransaction()
        }
    }

    /** Replaces the complete match collection as one restore transaction. */
    fun replaceMatches(incoming: List<TextMatch>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("expansion_log", null, null)
            db.delete("matches", null, null)
            incoming.forEach { upsert(db, it.copy(id = 0), touchUpdatedAt = false) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun resetStatistics() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE matches SET usage_count = 0")
            db.delete("expansion_log", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("matches", "id = ?", arrayOf(id.toString()))
    }

    /** Changes runtime availability without making the match look newly edited. */
    fun setEnabled(match: TextMatch, enabled: Boolean) {
        writableDatabase.update(
            "matches",
            ContentValues().apply {
                put("payload", MatchJsonCodec.encode(match.copy(enabled = enabled)))
            },
            "id = ?",
            arrayOf(match.id.toString()),
        )
    }

    fun recordExpansion(
        matchId: Long,
        packageName: String,
        advanceSequence: Boolean,
        collectStatistics: Boolean = true,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE matches SET usage_count = usage_count + ?, template_index = template_index + ? WHERE id = ?",
                arrayOf(if (collectStatistics) 1 else 0, if (advanceSequence) 1 else 0, matchId),
            )
            if (collectStatistics) {
                db.insertOrThrow("expansion_log", null, ContentValues().apply {
                    put("match_id", matchId)
                    put("package_name", packageName)
                    put("expanded_at", System.currentTimeMillis())
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun latestClipboardText(): String? = readableDatabase.query(
        "clipboard_history",
        arrayOf("text"),
        null,
        null,
        null,
        null,
        "pinned DESC, created_at DESC",
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
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
        writableDatabase.delete("clipboard_history", null, null)
    }

    fun setClipboardPinned(id: Long, pinned: Boolean) {
        writableDatabase.update(
            "clipboard_history",
            ContentValues().apply { put("pinned", pinned) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    private fun queryMatches(db: SQLiteDatabase): List<TextMatch> = db.query(
        "matches", null, null, null, null, null, "updated_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                add(
                    MatchJsonCodec.decode(cursor.getString(cursor.getColumnIndexOrThrow("payload")), id).copy(
                        usageCount = cursor.getLong(cursor.getColumnIndexOrThrow("usage_count")),
                        templateIndex = cursor.getLong(cursor.getColumnIndexOrThrow("template_index")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                    ),
                )
            }
        }
    }

    private fun upsert(
        db: SQLiteDatabase,
        match: TextMatch,
        touchUpdatedAt: Boolean = true,
    ): Long {
        require(match.triggers.isNotEmpty() && match.triggers.all { it.pattern.isNotBlank() }) {
            "A match needs at least one non-empty trigger"
        }
        require(match.replacements.isNotEmpty()) { "A match needs at least one replacement" }
        val now = if (touchUpdatedAt) System.currentTimeMillis() else match.updatedAt
        val normalized = match.copy(updatedAt = now)
        val values = ContentValues().apply {
            put("payload", MatchJsonCodec.encode(normalized))
            put("usage_count", normalized.usageCount)
            put("template_index", normalized.templateIndex)
            put("created_at", normalized.createdAt)
            put("updated_at", now)
        }
        return if (match.id == 0L) {
            db.insertOrThrow("matches", null, values)
        } else {
            db.update("matches", values, "id = ?", arrayOf(match.id.toString()))
            match.id
        }
    }

    /** Converts every released 0.1/0.2 row once, then removes the old schema. */
    private fun migrateLegacyMatches(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE expansion_log RENAME TO expansion_log_legacy")
        db.execSQL("ALTER TABLE snippets RENAME TO snippets_legacy")
        createMatchTables(db, indexes = false)

        db.query("snippets_legacy", null, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.long("id")
                val primary = cursor.string("shortcut")
                val aliases = cursor.jsonStrings("aliases")
                val regex = cursor.string("regex_trigger")
                val isRegex = cursor.string("match_kind").equals("REGEX", ignoreCase = true)
                val triggers = if (isRegex) {
                    listOf(MatchTrigger(regex.ifBlank { primary }, TriggerKind.REGEX))
                } else {
                    (listOf(primary) + aliases).filter(String::isNotBlank).distinct()
                        .map { MatchTrigger(it, TriggerKind.TEXT) }
                }
                val replacements = buildList {
                    add(cursor.string("content"))
                    addAll(cursor.jsonStrings("templates"))
                }
                val legacy = TextMatch(
                    id = id,
                    triggers = triggers,
                    replacements = replacements,
                    label = cursor.string("label"),
                    tags = cursor.separatedSet("tags"),
                    searchTerms = cursor.separatedSet("search_terms"),
                    enabled = cursor.boolean("enabled", true),
                    options = MatchOptions(
                        caseSensitive = cursor.boolean("case_sensitive"),
                        activation = if (cursor.string("trigger_mode") == "INSTANT") {
                            TriggerActivation.IMMEDIATE
                        } else TriggerActivation.DELIMITER,
                        delimiters = cursor.string("delimiters").ifEmpty { " \n\t.,!?;:" },
                        leftWord = cursor.boolean("left_word"),
                        rightWord = cursor.boolean("right_word"),
                        propagateCase = cursor.boolean("propagate_case"),
                        uppercaseStyle = enumOrDefault(cursor.string("uppercase_style"), UppercaseStyle.CAPITALIZE),
                    ),
                    vars = cursor.variables("espanso_variables"),
                    excludedPackages = cursor.separatedSet("excluded_packages"),
                    selectionMode = enumOrDefault(cursor.string("selection_mode"), TemplateSelectionMode.FIRST),
                    templateIndex = cursor.long("template_index"),
                    usageCount = cursor.long("usage_count"),
                    createdAt = cursor.long("created_at", System.currentTimeMillis()),
                    updatedAt = cursor.long("updated_at", System.currentTimeMillis()),
                )
                db.insertOrThrow("matches", null, legacy.toValues(includeId = true))
            }
        }

        db.execSQL(
            "INSERT INTO expansion_log(id, match_id, package_name, expanded_at) " +
                "SELECT id, snippet_id, package_name, expanded_at FROM expansion_log_legacy",
        )
        db.execSQL("DROP TABLE expansion_log_legacy")
        db.execSQL("DROP TABLE snippets_legacy")
        createMatchIndexes(db)
    }

    private fun TextMatch.toValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("payload", MatchJsonCodec.encode(this@toValues))
        put("usage_count", usageCount)
        put("template_index", templateIndex)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun Cursor.has(column: String): Boolean = getColumnIndex(column) >= 0
    private fun Cursor.string(column: String): String =
        if (has(column)) getString(getColumnIndexOrThrow(column)).orEmpty() else ""
    private fun Cursor.long(column: String, default: Long = 0): Long =
        if (has(column)) getLong(getColumnIndexOrThrow(column)) else default
    private fun Cursor.boolean(column: String, default: Boolean = false): Boolean =
        if (has(column)) getInt(getColumnIndexOrThrow(column)) == 1 else default
    private fun Cursor.separatedSet(column: String): Set<String> =
        string(column).split(SEPARATOR).map(String::trim).filter(String::isNotBlank).toSet()
    private fun Cursor.jsonStrings(column: String): List<String> = runCatching {
        val array = JSONArray(string(column))
        buildList(array.length()) {
            for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }.getOrDefault(emptyList())
    private fun Cursor.variables(column: String): List<TemplateVariable> = runCatching {
        val array = JSONArray(string(column))
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    TemplateVariable(
                        name = item.optString("name"),
                        type = item.optString("type"),
                        paramsJson = item.optJSONObject("params")?.toString() ?: "{}",
                        dependsOn = item.optJSONArray("dependsOn").strings(),
                        injectVars = item.optBoolean("injectVars", true),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else buildList(length()) {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    companion object {
        private const val DATABASE_NAME = "expanda.db"
        private const val DATABASE_VERSION = 5
        private const val SEPARATOR = "\u001F"
        private const val MAX_CLIPBOARD_LENGTH = 100_000

        private fun createMatchTables(db: SQLiteDatabase, indexes: Boolean = true) {
            db.execSQL(
                """
                CREATE TABLE matches (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    payload TEXT NOT NULL,
                    usage_count INTEGER NOT NULL DEFAULT 0,
                    template_index INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE expansion_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    match_id INTEGER NOT NULL,
                    package_name TEXT NOT NULL,
                    expanded_at INTEGER NOT NULL,
                    FOREIGN KEY(match_id) REFERENCES matches(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            if (indexes) createMatchIndexes(db)
        }

        private fun createMatchIndexes(db: SQLiteDatabase) {
            db.execSQL("CREATE INDEX idx_expansion_log_time ON expansion_log(expanded_at DESC)")
        }

        private fun createClipboardTable(db: SQLiteDatabase) {
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

        private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
            runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
