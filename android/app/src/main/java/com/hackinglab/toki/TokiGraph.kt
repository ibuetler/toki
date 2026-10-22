package com.hackinglab.toki

import android.content.Context
import androidx.room.Room
import com.hackinglab.toki.data.*

class TokiGraph private constructor(context: Context) {
    val lock = Any()
    val database = Room.databaseBuilder(context, TokiDatabase::class.java, "toki.db").addMigrations(CACHE_MIGRATION, BIRTHDAY_CACHE_MIGRATION, TASK_CACHE_MIGRATION, CALENDAR_CACHE_MIGRATION, CALENDAR_VISIBILITY_MIGRATION).build()
    val sessions = SessionStore(context)
    val reminders = Reminders(context)
    val api = PocketBase()
    val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    fun reschedule(): Int = synchronized(lock) {
        if (sessions.load() == null) {
            // A missing/decrypt-failed session must never leave another user's cache behind.
            database.deadlines().clear()
            database.tasks().clear()
            reminders.rebuild(emptyList())
        } else reminders.rebuild(alarmRecords())
    }
    fun logout() = synchronized(lock) {
        reminders.cancelAll()
        sessions.clear()
        database.deadlines().clear()
        database.tasks().clear()
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
                database.tasks().clear()
                database.tasks().insert(snapshot.tasks)
            }
            prefs.edit().putBoolean("calendarHidingAvailable", snapshot.calendarHidingAvailable).putLong("lastSync", System.currentTimeMillis()).putStringSet("deletableIds", snapshot.deletableIds).putBoolean("tasksAvailable", snapshot.tasksAvailable).putBoolean("birthdaysAvailable", snapshot.birthdaysAvailable).commit()
            reminders.rebuild(alarmRecords())
        }
    }
    fun alarmRecords(): List<Deadline> = database.deadlines().all().filterNot { it.hidden } + database.tasks().all().mapNotNull { it.alarm() }
    fun setCalendarHidden(id: String, hidden: Boolean, userId: String) = synchronized(lock) {
        check(sessions.load()?.userId == userId) { "Session changed. Please sync." }
        database.deadlines().setHidden(id, hidden)
        reminders.rebuild(alarmRecords())
        Unit
    }
    fun saveLocalTask(task: TokiTask, userId: String) = synchronized(lock) {
        check(sessions.load()?.userId == userId) { "Session changed. Please sync." }
        database.tasks().insert(listOf(task))
        reminders.rebuild(alarmRecords())
        Unit
    }
    fun removeLocalTask(id: String) = synchronized(lock) {
        database.tasks().delete(id)
        reminders.rebuild(alarmRecords())
        Unit
    }
    fun removeLocalEvent(id: String) = synchronized(lock) {
        database.deadlines().delete(id)
        val allowed = prefs.getStringSet("deletableIds", emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet("deletableIds", allowed).commit()
        reminders.rebuild(alarmRecords())
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

        val BIRTHDAY_CACHE_MIGRATION = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deadlines ADD COLUMN kind TEXT NOT NULL DEFAULT 'deadline'")
                for (field in listOf("birthdayMonth", "birthdayDay", "birthYear", "birthdayMinute"))
                    db.execSQL("ALTER TABLE deadlines ADD COLUMN $field INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN birthdayHour INTEGER NOT NULL DEFAULT 9")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN leapDayPolicy TEXT NOT NULL DEFAULT 'feb28'")
            }
        }
        val TASK_CACHE_MIGRATION = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL, priority TEXT NOT NULL, completed INTEGER NOT NULL, reminderMillis INTEGER NOT NULL, createdMillis INTEGER NOT NULL)")
            }
        }
        val CALENDAR_CACHE_MIGRATION = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deadlines ADD COLUMN sourceNames TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN endMillis INTEGER NOT NULL DEFAULT 0")
            }
        }
        val CALENDAR_VISIBILITY_MIGRATION = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deadlines ADD COLUMN externalKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: TokiGraph? = null
        fun get(context: Context): TokiGraph = instance ?: synchronized(this) {
            instance ?: TokiGraph(context.applicationContext).also { instance = it }
        }
    }
}
