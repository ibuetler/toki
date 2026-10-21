package com.hackinglab.toki

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hackinglab.toki.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI

@RunWith(AndroidJUnit4::class)
class TokiDeviceTest {
    @Test fun roomReplacementRemovesRevokedEventsAtomically() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val db = Room.inMemoryDatabaseBuilder(context, TokiDatabase::class.java).build()
        try {
            db.deadlines().insert(listOf(Deadline().apply { id = "revoked"; title = "Private" }))
            db.runInTransaction {
                db.deadlines().clear()
                db.deadlines().insert(listOf(Deadline().apply { id = "retained"; title = "Group event" }))
            }
            assertNull(db.deadlines().find("revoked"))
            assertEquals("Group event", db.deadlines().find("retained").title)
        } finally { db.close() }
    }
    @Test fun deletingOneCachedEventPreservesOthers() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val db = Room.inMemoryDatabaseBuilder(context, TokiDatabase::class.java).build()
        try {
            db.deadlines().insert(listOf(Deadline().apply { id = "delete-me" }, Deadline().apply { id = "keep-me" }))
            db.deadlines().delete("delete-me")
            assertNull(db.deadlines().find("delete-me"))
            assertNotNull(db.deadlines().find("keep-me"))
            assertEquals(1, db.deadlines().all().size)
        } finally { db.close() }
    }
    @Test fun birthdayMetadataSurvivesCacheRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val db = Room.inMemoryDatabaseBuilder(context, TokiDatabase::class.java).build()
        try {
            val birthday = BirthdaySpec(2, 29, 2000, 10, 15, "mar1").deadline("Alex", "Notes", true).apply { id = "birthday:cache" }
            db.deadlines().insert(listOf(birthday))
            val cached = db.deadlines().find(birthday.id)
            assertEquals("birthday", cached.kind)
            assertEquals(BirthdaySpec.from(birthday), BirthdaySpec.from(cached))
            assertTrue(cached.allDay)
            assertEquals("Notes", cached.description)
        } finally { db.close() }
    }
    @Test fun sessionUsesEncryptedStorageAndCanBeCleared() {
        // Separate preference namespace, using the process's actual storage UID.
        val context = object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                super.getSharedPreferences("instrumentation_" + name, mode)
        }
        val store = SessionStore(context)
        try {
            store.save(Session("test-secret-token", "test-user", "test@example.com"))
            assertEquals("test-secret-token", store.load()?.token)
            val raw = context.getSharedPreferences("session", 0).all.toString()
            assertFalse(raw.contains("test-secret-token"))
            store.clear()
            assertNull(store.load())
        } finally { store.clear() }
    }
    @Test fun deviceCanReachPocketBaseOverTls() {
        val connection = URI(PocketBase.BASE + "/api/health").toURL().openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            assertEquals(200, connection.responseCode)
        } finally { connection.disconnect() }
    }
}
