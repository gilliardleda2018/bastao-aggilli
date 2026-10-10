package br.com.aggilli.bastao.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Reading(
    val id: Long,
    val session: String,
    val tag: String,
    val timestamp: Long,
    val synced: Boolean,
)

/** Armazenamento local das leituras (funciona offline). Uma tag aparece uma vez por sessão. */
class ReadingDb(context: Context) : SQLiteOpenHelper(context, "bastao.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session TEXT NOT NULL,
                tag TEXT NOT NULL,
                ts INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0,
                UNIQUE(session, tag)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Sem migrações na v1.
    }

    /** Retorna true se a leitura é nova na sessão, false se já existia (duplicada). */
    fun insert(session: String, tag: String, timestamp: Long): Boolean {
        val values = ContentValues().apply {
            put("session", session)
            put("tag", tag)
            put("ts", timestamp)
        }
        val rowId = writableDatabase.insertWithOnConflict(
            "readings", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return rowId != -1L
    }

    fun list(session: String): List<Reading> = query("session = ?", arrayOf(session), "id DESC")

    fun pendingSync(): List<Reading> = query("synced = 0", null, "id ASC")

    fun markSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val values = ContentValues().apply { put("synced", 1) }
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.update("readings", values, "id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(session: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM readings WHERE session = ?", arrayOf(session)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun csv(session: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val sb = StringBuilder("sessao,brinco,data_hora\n")
        list(session).reversed().forEach {
            sb.append(it.session).append(',')
                .append(it.tag).append(',')
                .append(fmt.format(Date(it.timestamp))).append('\n')
        }
        return sb.toString()
    }

    private fun query(where: String, args: Array<String>?, order: String): List<Reading> {
        val out = mutableListOf<Reading>()
        readableDatabase.query(
            "readings", arrayOf("id", "session", "tag", "ts", "synced"),
            where, args, null, null, order
        ).use { c ->
            while (c.moveToNext()) {
                out += Reading(
                    id = c.getLong(0),
                    session = c.getString(1),
                    tag = c.getString(2),
                    timestamp = c.getLong(3),
                    synced = c.getInt(4) == 1,
                )
            }
        }
        return out
    }
}
