package com.hackinglab.toki

import android.graphics.Bitmap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.hackinglab.toki.data.*
import com.hackinglab.toki.ui.theme.TOKITheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Real Compose screens with fictional data. No backend, session storage, or alarms. */
class TokiDocumentationScreenshots {
    @get:Rule val screen = createComposeRule()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private fun at(days: Long, hour: Int) = today.plusDays(days).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun event(id: String, title: String, days: Long, hour: Int, group: String = "") = Deadline().apply {
        this.id = id; this.title = title; groupName = group; timezone = zone.id
        description = "Bring the project notes and agree on the next steps."
        enabled = true; reminderMinutes = 15; startMillis = at(days, hour); recurrenceLoaded = true
    }
    private fun birthday(id: String, name: String, days: Long, year: Int): Deadline {
        val date = today.plusDays(days)
        return BirthdaySpec(date.monthValue, date.dayOfMonth, year).deadline(name, "A good day to send a message.", true).apply { this.id = "birthday:$id" }
    }
    private fun task(id: String, title: String, priority: String, completed: Boolean = false) = TokiTask().apply {
        this.id = id; this.title = title; this.priority = priority; this.completed = completed
        notes = "A quick thought to follow up when there is time."; createdMillis = at(-1, 12)
    }
    private fun capture(name: String, tag: String? = null, scrollToHeading: Boolean = true) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        screen.waitForIdle()
        if (tag != null && scrollToHeading) {
            val heading = when (name) { "new-event" -> "New event"; "new-birthday" -> "New birthday"; "new-task" -> "New task"; else -> "Search TOKI" }
            screen.onNodeWithTag(tag).performScrollToNode(hasText(heading))
        }
        val node = if (tag == null) screen.onRoot() else screen.onNodeWithTag(tag)
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "documentation").apply { mkdirs() }
        node.captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun top() {
        if (screen.onAllNodesWithContentDescription("Back to top").fetchSemanticsNodes().isNotEmpty())
            screen.onNodeWithContentDescription("Back to top").performClick()
        else screen.onNodeWithTag("agenda-content").performScrollToIndex(0)
        screen.onNodeWithContentDescription("Search TOKI").assertIsDisplayed()
    }
    @Test fun captureCalendarGuide() {
        val sources = listOf(
            CalendarSource(id = "studio", name = "Studio", url = "https://outlook.office365.com/example/studio.ics", lastSuccess = "2026-10-22 12:00:00.000Z"),
            CalendarSource(id = "community", name = "Community", url = "https://outlook.office365.com/example/community.ics", lastSuccess = "2026-10-22 12:00:00.000Z"))
        val rules = listOf(CalendarRule(id = "travel", pattern = "Marina *"), CalendarRule(id = "holiday", pattern = "*Vacation*", source = "studio"))
        val imported = event("calendar:planning", "Project review", 1, 10).apply {
            kind = "calendar"; externalKey = "a".repeat(64); sourceNames = "Studio · Community"; reminderMinutes = 5
            description = "Review the demo milestones and agree on next steps."
        }
        val hidden = event("calendar:hidden", "Optional catch-up", 2, 11).apply {
            kind = "calendar"; externalKey = "b".repeat(64); sourceNames = "Community"; this.hidden = true; reminderMinutes = 5
            description = "An optional demonstration meeting."
        }
        val examples = listOf("Marina in CH", "Marina visiting friends").mapIndexed { index, title ->
            event("calendar:example$index", title, index + 3L, 12).apply {
                kind = "calendar"; externalKey = (index + 3).toString().repeat(64); sourceNames = "Studio"; reminderMinutes = 5
            }
        }
        val records = listOf(imported, hidden) + examples
        val state = TokiState(ready = true, calendarHidingAvailable = true,
            session = Session("fictional-preview", "demo", "demo@example.com"), deadlines = records,
            calendarSettings = CalendarSettings(sources, rules), deletePermissionsLoaded = true,
            exactAllowed = true, notificationsAllowed = true, fullScreenAllowed = true,
            scheduled = 3, lastSync = System.currentTimeMillis() - 120_000,
            previews = records.associate { it.id to EventPreview(it.startMillis, nextReminderStart = if (it.hidden) null else it.startMillis) })
        screen.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            TOKITheme(darkTheme = false) {
                TokiScreen(state, onLogin = { _, _ -> }, onSync = {}, onLogout = {}, onTest = {},
                    onNotifications = {}, onExact = {}, onFullScreen = {})
            }
        }
        screen.onNodeWithTag("next-event-tile").performClick()
        screen.onNodeWithText("Hide in TOKI").assertIsDisplayed()
        capture("imported-event")
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-filters"))
        screen.onNodeWithTag("event-filters").performScrollToNode(hasText("Hidden events"))
        screen.onNodeWithText("Hidden events").performClick()
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-calendar:hidden"))
        screen.onNodeWithTag("event-calendar:hidden").performClick()
        screen.onNodeWithText("Imported calendar event. Edit or delete it in Outlook, then sync TOKI after the next calendar import.").performScrollTo()
        screen.onNodeWithText("Restore event").assertIsDisplayed()
        capture("hidden-events")
        screen.onNodeWithText("Account", useUnmergedTree = true).performClick()
        screen.onNodeWithText("Calendars & exclusions").performScrollTo().assertIsDisplayed()
        screen.onNodeWithTag("agenda-content").performScrollToIndex(0)
        capture("account")
        screen.onNodeWithText("Calendars & exclusions").performScrollTo().performClick()
        screen.onNodeWithText("Studio").assertExists()
        capture("calendar-connections", "calendar-settings", false)
        screen.onNodeWithTag("calendar-settings").performScrollToIndex(8)
        screen.onNodeWithText("Marina *").assertIsDisplayed()
        capture("exclusion-rules", "calendar-settings", false)
        screen.onNodeWithText("Add exclusion").performScrollTo().performClick()
        screen.onNodeWithText("Title pattern").performTextInput("Marina *")
        screen.onNodeWithText("Matches 2 currently imported events").assertExists()
        capture("new-exclusion", "calendar-settings-form", false)
        screen.onNodeWithText("Cancel").performClick()
        screen.onNodeWithTag("calendar-settings").performScrollToIndex(0)
        screen.onNodeWithText("Add calendar").performScrollTo().performClick()
        screen.onNodeWithText("Calendar name").performTextInput("Workshop calendar")
        screen.onNodeWithText("Published ICS URL").performTextInput("https://outlook.office365.com/example/workshops.ics")
        capture("new-calendar", "calendar-settings-form", false)
        screen.onNodeWithText("Cancel").performClick()
        screen.onNodeWithTag("calendar-settings").performScrollToIndex(0)
        screen.onNodeWithText("Done").performClick()
    }
    @Test fun captureIllustratedGuide() {
        val events = listOf(event("planning", "Team planning", 1, 9), event("coffee", "Coffee with Alex", 2, 10),
            event("dentist", "Dentist appointment", 3, 15), event("hike", "Weekend hiking", 4, 8, "Friends"))
        val extraNames = listOf("Casey Adams", "Taylor Brooks", "Avery Clark", "Jordan Davis", "Robin Ellis", "Sam Foster", "Charlie Green", "Drew Hall", "Quinn Irving", "Lee Jones", "Skyler King", "Remy Lane", "Sage Moore", "Blair Nash", "Dakota Owen", "Rowan Price", "Logan Reed", "Finley Stone", "Ellis Turner", "Hayden Vale", "Emerson West", "Reese Young", "Cameron York", "Parker Woods")
        val birthdays = listOf(birthday("alex", "Alex Morgan", 2, 1992), birthday("sofia", "Sofia Bell", 5, 1988),
            birthday("jamie", "Jamie Chen", 12, 2000), birthday("riley", "Riley Park", 20, 0)) +
            (1..24).map { birthday("friend$it", extraNames[it - 1], 20L + it, 0) }
        val tasks = listOf(task("checkup", "Book the annual check-up", "urgent"), task("travel", "Review the travel plan", "high"),
            task("vpn", "Ask about VPN access", "normal"), task("photos", "Sort holiday photos", "low"),
            task("alexnotes", "Send Alex the meeting notes", "normal", true))
        val records = events + birthdays
        val state = TokiState(ready = true, session = Session("fictional-preview", "demo", "demo@example.com"),
            deadlines = records, tasks = tasks, birthdaysAvailable = true, tasksAvailable = true,
            deletableIds = events.filter { it.groupName.isBlank() }.map { it.id }.toSet() + birthdays.map { it.id }, deletePermissionsLoaded = true,
            exactAllowed = true, notificationsAllowed = true, fullScreenAllowed = true, scheduled = records.count { it.enabled }, lastSync = System.currentTimeMillis() - 120_000,
            previews = records.associate { record ->
                val next = if (record.kind == "birthday") BirthdaySpec.from(record).next(System.currentTimeMillis(), true)!!.startMillis else record.startMillis
                record.id to EventPreview(nextStart = next, nextReminderStart = next)
            })
        screen.setContent {
            val view = LocalView.current
            DisposableEffect(view) { val previous = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = previous } }
            TOKITheme(darkTheme = false) {
                TokiScreen(state, onLogin = { _, _ -> }, onSync = {}, onLogout = {}, onTest = {},
                    onNotifications = {}, onExact = {}, onFullScreen = {})
            }
        }
        capture("agenda")
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-dentist"))
        screen.onNodeWithTag("agenda-content").performScrollToIndex(4)
        capture("events")
        top()
        screen.onNodeWithText("+  New event").performClick()
        screen.onNodeWithText("Title").performTextInput("Project review")
        capture("new-event", "new-event-form")
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Cancel"))
        screen.onNodeWithText("Cancel").performClick()
        screen.onNodeWithText("Birthdays").performScrollTo().performClick()
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-birthday:jamie"))
        screen.onNodeWithTag("agenda-content").performScrollToIndex(4)
        capture("birthdays")
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-birthday:friend24"))
        capture("back-to-top")
        top()
        screen.onNodeWithText("+  New birthday").performClick()
        screen.onNodeWithText("Name").performTextInput("Alex Morgan")
        screen.onNodeWithText("Birth date").performTextInput("8.10.1992")
        capture("new-birthday", "new-event-form")
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Cancel"))
        screen.onNodeWithText("Cancel").performClick()
        screen.onNodeWithText("Tasks", useUnmergedTree = true).performScrollTo().performClick()
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("task-photos"))
        screen.onNodeWithTag("agenda-content").performScrollToIndex(4)
        capture("tasks")
        top()
        screen.onNodeWithText("+  New task").performClick()
        screen.onNodeWithText("Task").performTextInput("Ask about the workshop dates")
        capture("new-task", "task-form")
        screen.onNodeWithText("Cancel").performClick()
        screen.onNodeWithContentDescription("Search TOKI").performClick()
        screen.onNodeWithText("Search events, birthdays and tasks").performTextInput("Alex")
        capture("search", "search-results")
        screen.onNodeWithTag("search-results").performScrollToNode(hasText("Done"))
        screen.onNodeWithText("Done").performClick()
        screen.onNodeWithText("Alarms", useUnmergedTree = true).performClick()
        capture("alarms")
    }
}
