package com.example.floatclip

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor

class ClipboardDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "clipboard.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_NAME = "clipboard_items"
        const val COL_ID = "id"
        const val COL_TEXT = "text"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_IS_PINNED = "isPinned"
        const val COL_HASH = "hash"
        private const val MAX_ITEMS = 50
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEXT TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_IS_PINNED INTEGER DEFAULT 0,
                $COL_HASH TEXT NOT NULL UNIQUE
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertClipboardItem(text: String, timestamp: Long, isPinned: Boolean, hash: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TEXT, text)
            put(COL_TIMESTAMP, timestamp)
            put(COL_IS_PINNED, if (isPinned) 1 else 0)
            put(COL_HASH, hash)
        }
        val result = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (result != -1L) {
            trimToMaxItems(db)
        }
        return result != -1L
    }

    fun getAllItems(): List<ClipboardItem> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COL_IS_PINNED DESC, $COL_TIMESTAMP DESC")
        val items = mutableListOf<ClipboardItem>()
        if (cursor.moveToFirst()) {
            do {
                items.add(ClipboardItem(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    text = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEXT)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_PINNED)) == 1,
                    hash = cursor.getString(cursor.getColumnIndexOrThrow(COL_HASH))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return items
    }

    fun updatePinStatus(id: Long, pinned: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply { put(COL_IS_PINNED, if (pinned) 1 else 0) }
        db.update(TABLE_NAME, values, "$COL_ID=?", arrayOf(id.toString()))
    }

    fun deleteItem(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_NAME, "$COL_ID=?", arrayOf(id.toString()))
    }

    private fun trimToMaxItems(db: SQLiteDatabase) {
        val cursor = db.rawQuery(
            "SELECT $COL_ID FROM $TABLE_NAME WHERE $COL_IS_PINNED=0 ORDER BY $COL_TIMESTAMP DESC LIMIT -1 OFFSET $MAX_ITEMS",
            null
        )
        val idsToDelete = mutableListOf<Long>()
        if (cursor.moveToFirst()) {
            do {
                idsToDelete.add(cursor.getLong(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        for (id in idsToDelete) {
            db.delete(TABLE_NAME, "$COL_ID=?", arrayOf(id.toString()))
        }
    }
}

data class ClipboardItem(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val isPinned: Boolean,
    val hash: String
)
