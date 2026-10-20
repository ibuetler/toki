package com.hackinglab.toki

import android.content.Context
import androidx.room.Room
import com.hackinglab.toki.data.*

class TokiGraph private constructor(context: Context) {
    val lock = Any()
    val database = Room.databaseBuilder(context, TokiDatabase::class.java, "toki.db").build()
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
        val records = api.deadlines(refreshed)
        synchronized(lock) {
            check(sessions.load()?.userId == session.userId) { "Session changed. Please try again." }
            sessions.save(refreshed)
            database.runInTransaction {
                database.deadlines().clear()
                database.deadlines().insert(records)
            }
            prefs.edit().putLong("lastSync", System.currentTimeMillis()).commit()
            reminders.rebuild(records)
        }
    }
    companion object {
        @Volatile private var instance: TokiGraph? = null
        fun get(context: Context): TokiGraph = instance ?: synchronized(this) {
            instance ?: TokiGraph(context.applicationContext).also { instance = it }
        }
    }
}
