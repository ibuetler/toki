package com.hackinglab.toki

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TaskDeviceTest {
    @Test fun cachedTaskPreservesReminderCompletionAndNotes() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val db = Room.inMemoryDatabaseBuilder(context, TokiDatabase::class.java).build()
        try {
            val task = TokiTask().apply {
                id = "fixture"; title = "A thought"; notes = "Private notes"; priority = "urgent"
                reminderMillis = 1_900_000_000_123L; createdMillis = 1_800_000_000_000L; completed = true
            }
            db.tasks().insert(listOf(task))
            val cached = requireNotNull(db.tasks().find(task.id))
            assertEquals(TaskDraft.from(task), TaskDraft.from(cached))
            assertEquals(task.createdMillis, cached.createdMillis); assertTrue(cached.completed)
            assertNull(cached.alarm())
        } finally { db.close() }
    }
    @Test fun completingTaskRemovesItsPendingAlarm() {
        // Separate alarm preferences and identifier; no user alarms are cancelled or played.
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("instrumentation_task_" + name, mode)
        }
        val reminders = Reminders(context)
        assumeTrue(reminders.ready())
        val task = TokiTask().apply { id = "instrumentation-fixture"; title = "Test only"; reminderMillis = System.currentTimeMillis() + 86_400_000L }
        try {
            assertEquals(1, reminders.rebuild(listOf(requireNotNull(task.alarm()))))
            assertTrue(context.getSharedPreferences("alarms", Context.MODE_PRIVATE).getStringSet("ids", emptySet())!!.contains("task:${task.id}"))
            task.completed = true
            assertEquals(0, reminders.rebuild(listOfNotNull(task.alarm())))
            assertTrue(context.getSharedPreferences("alarms", Context.MODE_PRIVATE).getStringSet("ids", emptySet())!!.isEmpty())
        } finally {
            reminders.rebuild(emptyList())
            context.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
