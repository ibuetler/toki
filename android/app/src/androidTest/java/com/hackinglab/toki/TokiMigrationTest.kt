package com.hackinglab.toki

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.hackinglab.toki.data.TokiDatabase
import org.junit.Assert.*
import org.junit.Test

class TokiMigrationTest {
    @Test fun versionOneCacheMigratesWithoutDroppingEvents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE deadlines (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, groupName TEXT NOT NULL, timezone TEXT NOT NULL, rrule TEXT NOT NULL, startMillis INTEGER NOT NULL, reminderMinutes INTEGER NOT NULL, enabled INTEGER NOT NULL, allDay INTEGER NOT NULL)")
            db.execSQL("INSERT INTO deadlines VALUES ('keep','Weekly meeting','','Family','Europe/Zurich','FREQ=WEEKLY',1793001600000,5,1,0)")
            db.version = 1
        }
        val upgraded = Room.databaseBuilder(context, TokiDatabase::class.java, name)
            .addMigrations(TokiGraph.CACHE_MIGRATION, TokiGraph.BIRTHDAY_CACHE_MIGRATION, TokiGraph.TASK_CACHE_MIGRATION, TokiGraph.CALENDAR_CACHE_MIGRATION).build()
        try {
            val event = upgraded.deadlines().find("keep")
            assertEquals("Weekly meeting", event.title)
            assertEquals("Family", event.groupName)
            assertEquals("FREQ=WEEKLY", event.rrule)
            assertEquals("", event.exdates)
            assertEquals("", event.rdates)
            assertFalse(event.recurrenceLoaded)
            assertEquals("deadline", event.kind)
            assertEquals(9, event.birthdayHour)
            assertTrue(upgraded.tasks().all().isEmpty())
            val task = com.hackinglab.toki.data.TokiTask().apply { id = "task-fixture"; title = "Preserved thought"; priority = "urgent" }
            upgraded.tasks().insert(listOf(task))
            assertEquals("urgent", upgraded.tasks().find(task.id).priority)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
