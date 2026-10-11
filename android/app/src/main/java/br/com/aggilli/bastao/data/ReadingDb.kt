package br.com.aggilli.bastao.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Reading(
    val id: Long,
    val uuid: String,
    val session: String,
    val tag: String,
    val timestamp: Long,
    val synced: Boolean,
    val recovered: Boolean,
)

/**
 * Armazenamento local das leituras (funciona offline). Uma tag aparece uma vez por sessão.
 * Cada leitura tem um UUID: o servidor usa esse UUID para não gravar a mesma leitura duas vezes
 * quando o envio é repetido (queda de internet no meio da sincronização).
 */
class ReadingDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "bastao.db", null, 2) {

    companion object {
        @Volatile private var instance: ReadingDb? = null

        /** Instância única: o app e a sincronização em segundo plano usam a mesma conexão. */
        fun get(context: Context): ReadingDb =
            instance ?: synchronized(this) {
                instance ?: ReadingDb(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                session TEXT NOT NULL,
                tag TEXT NOT NULL,
                ts INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0,
                recovered INTEGER NOT NULL DEFAULT 0,
                UNIQUE(session, tag)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_readings_uuid ON readings(uuid)")
        db.execSQL("CREATE INDEX idx_readings_synced ON readings(synced)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE readings ADD COLUMN uuid TEXT")
            db.execSQL("ALTER TABLE readings ADD COLUMN recovered INTEGER NOT NULL DEFAULT 0")
            db.rawQuery("SELECT id FROM readings", null).use { c ->
                while (c.moveToNext()) {
                    val v = ContentValues().apply { put("uuid", UUID.randomUUID().toString()) }
                    db.update("readings", v, "id = ?", arrayOf(c.getLong(0).toString()))
                }
            }
            db.execSQL("CREATE UNIQUE INDEX idx_readings_uuid ON readings(uuid)")
            db.execSQL("CREATE INDEX idx_readings_synced ON readings(synced)")
        }
    }

    /** Retorna true se a leitura é nova na sessão, false se já existia (duplicada). */
    fun insert(session: String, tag: String, timestamp: Long, recovered: Boolean = false): Boolean {
        val values = ContentValues().apply {
            put("uuid", UUID.randomUUID().toString())
            put("session", session)
            put("tag", tag)
            put("ts", timestamp)
            put("recovered", if (recovered) 1 else 0)
        }
        val rowId = writableDatabase.insertWithOnConflict(
            "readings", null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return rowId != -1L
    }

    fun list(session: String): List<Reading> = query("session = ?", arrayOf(session), "id DESC", null)

    fun pendingSync(limit: Int = 200): List<Reading> = query("synced = 0", null, "id ASC", limit.toString())

    fun pendingCount(): Int = scalar("SELECT COUNT(*) FROM readings WHERE synced = 0", null)

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

    fun count(session: String): Int =
        scalar("SELECT COUNT(*) FROM readings WHERE session = ?", arrayOf(session))

    fun csv(session: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val sb = StringBuilder("sessao,brinco,data_hora,sincronizado\n")
        list(session).reversed().forEach {
            sb.append(it.session).append(',')
                .append(it.tag).append(',')
                .append(fmt.format(Date(it.timestamp))).append(',')
                .append(if (it.synced) "sim" else "nao").append('\n')
        }
        return sb.toString()
    }

    private fun scalar(sql: String, args: Array<String>?): Int =
        readableDatabase.rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun query(where: String, args: Array<String>?, order: String, limit: String?): List<Reading> {
        val out = mutableListOf<Reading>()
        readableDatabase.query(
            "readings", arrayOf("id", "uuid", "session", "tag", "ts", "synced", "recovered"),
            where, args, null, null, order, limit
        ).use { c ->
            while (c.moveToNext()) {
                out += Reading(
                    id = c.getLong(0),
                    uuid = c.getString(1),
                    session = c.getString(2),
                    tag = c.getString(3),
                    timestamp = c.getLong(4),
                    synced = c.getInt(5) == 1,
                    recovered = c.getInt(6) == 1,
                )
            }
        }
        return out
    }
}
