package com.hackinglab.toki

import android.content.Context
import androidx.room.Room
import com.hackinglab.toki.data.*

class TokiGraph private constructor(context: Context) {
    val lock = Any()
    val database = Room.databaseBuilder(context, TokiDatabase::class.java, "toki.db").addMigrations(CACHE_MIGRATION).build()
    val sessions = SessionStore(context)
    val reminders = Reminders(context)
    val api = PocketBase()
    val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    fun reschedule(): Int = synchronized(lock) {
        if (sessions.load() == null) {
            // A missing/decrypt-failed session must never leave another user's cache behind.
            database.deadlines().clear()
            reminders.rebuild(emptyList())
        } else reminders.rebuild(database.deadlines().all())
    }
    fun logout() = synchronized(lock) {
        reminders.cancelAll()
        sessions.clear()
        database.deadlines().clear()
        prefs.edit().clear().commit()
        Unit
    }
    fun sync(session: Session) {
        // List rules can hide an invalid session, so validate it explicitly first.
        val refreshed = api.refresh(session)
        val snapshot = api.deadlines(refreshed)
        val records = snapshot.records
        synchronized(lock) {
            check(sessions.load()?.userId == session.userId) { "Session changed. Please try again." }
            sessions.save(refreshed)
            database.runInTransaction {
                database.deadlines().clear()
                database.deadlines().insert(records)
            }
            prefs.edit().putLong("lastSync", System.currentTimeMillis()).putStringSet("deletableIds", snapshot.deletableIds).commit()
            reminders.rebuild(records)
        }
    }
    fun removeLocalEvent(id: String) = synchronized(lock) {
        database.deadlines().delete(id)
        val allowed = prefs.getStringSet("deletableIds", emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet("deletableIds", allowed).commit()
        reminders.rebuild(database.deadlines().all())
        Unit
    }
    companion object {
        val CACHE_MIGRATION = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deadlines ADD COLUMN exdates TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN rdates TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN recurrenceLoaded INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: TokiGraph? = null
        fun get(context: Context): TokiGraph = instance ?: synchronized(this) {
            instance ?: TokiGraph(context.applicationContext).also { instance = it }
        }
    }
}
