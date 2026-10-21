package com.hackinglab.toki

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.hackinglab.toki.data.Deadline
import com.hackinglab.toki.data.Session
import com.hackinglab.toki.ui.theme.TOKITheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic UI data: never reads sessions, accesses the backend, or schedules alarms. */
class TokiNavigationTest {
    @get:Rule val screen = createComposeRule()
    private fun render(dark: Boolean = false, reminded: Boolean = false, includePast: Boolean = false, includeBirthday: Boolean = false, birthdaysAvailable: Boolean = true, onCreate: (com.hackinglab.toki.data.NewEvent) -> Unit = {}, onDelete: (String) -> Unit = {}, onEdit: (String) -> Unit = {}, tasks: List<com.hackinglab.toki.data.TokiTask> = emptyList(), onSaveTask: (com.hackinglab.toki.data.TaskDraft, String?) -> Unit = { _, _ -> }, onCompleteTask: (String, Boolean) -> Unit = { _, _ -> }) {
        val start = System.currentTimeMillis() + 3_600_000
        val events = listOf(
            Deadline().apply { id = "one"; title = "Project milestone"; timezone = "Europe/Zurich"; enabled = true; reminderMinutes = if (reminded) 120 else 0; startMillis = start },
            Deadline().apply { id = "two"; title = "Family birthday"; groupName = "Family"; timezone = "Europe/Zurich"; enabled = false; startMillis = start + 86_400_000 }
        ).reversed() + if (includePast) listOf(Deadline().apply { id = "past"; title = "Earlier deadline"; timezone = "Europe/Zurich"; startMillis = start - 7_200_000 }) else emptyList()
        val birthday = com.hackinglab.toki.data.BirthdaySpec(java.time.LocalDate.now().monthValue, java.time.LocalDate.now().dayOfMonth, 2000).deadline("Alex", "", true).apply { id = "birthday:fixture" }
        val records = events + if (includeBirthday) listOf(birthday) else emptyList()
        screen.setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            TOKITheme(darkTheme = dark) {
                TokiScreen(TokiState(tasks = tasks, tasksAvailable = true, birthdaysAvailable = birthdaysAvailable, deletableIds = setOf("one"), deletePermissionsLoaded = true, ready = true, session = Session("preview", "preview", "hello@example.com"), deadlines = records,
                    notificationsAllowed = true, exactAllowed = true, fullScreenAllowed = true, scheduled = 1,
                    previews = records.associate { it.id to if (it.kind == "birthday") EventPreview(System.currentTimeMillis() - 3_600_000, visibleUntil = System.currentTimeMillis() + 3_600_000) else EventPreview(if (it.id == "past") null else it.startMillis, nextReminderStart = if (reminded && it.enabled) it.startMillis + 7 * 86_400_000L else it.startMillis) }),
                    onSaveTask = onSaveTask, onCompleteTask = onCompleteTask, onEdit = onEdit, onDelete = onDelete, onCreate = onCreate, onLogin = { _, _ -> }, onSync = {}, onLogout = {}, onTest = {}, onNotifications = {}, onExact = {}, onFullScreen = {})
            }
        }
    }
    @Test fun taskQuickCaptureNeedsOnlyTitleAndUsesNormalPriority() {
        var saved: com.hackinglab.toki.data.TaskDraft? = null
        render(onSaveTask = { draft, id -> org.junit.Assert.assertNull(id); saved = draft })
        screen.onNodeWithText("Tasks", useUnmergedTree = true).performScrollTo().performClick()
        screen.onNodeWithText("+  New task").performClick()
        screen.onNodeWithText("Task").performTextInput("Ask Peter about the VPN")
        screen.onNodeWithText("Notes (optional)").assertDoesNotExist()
        screen.onNodeWithTag("task-form").performScrollToNode(hasText("Add task"))
        screen.onNodeWithText("Add task").performClick()
        screen.runOnIdle {
            org.junit.Assert.assertEquals("Ask Peter about the VPN", saved?.title)
            org.junit.Assert.assertEquals("normal", saved?.priority)
            org.junit.Assert.assertEquals(0L, saved?.reminderMillis)
        }
    }
    @Test fun tasksSortByPriorityAndCompletedViewAllowsReopening() {
        fun task(id: String, priority: String, created: Long, completed: Boolean = false) = com.hackinglab.toki.data.TokiTask().apply {
            this.id = id; title = id; this.priority = priority; createdMillis = created; this.completed = completed
        }
        var changed: Pair<String, Boolean>? = null
        render(tasks = listOf(task("low", "low", 20), task("old-high", "high", 1), task("urgent", "urgent", 0),
            task("new-high", "high", 10), task("done", "normal", 0, true)), onCompleteTask = { id, done -> changed = id to done })
        screen.onNodeWithText("Tasks", useUnmergedTree = true).performScrollTo().performClick()
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("task-old-high"))
        val urgent = screen.onNodeWithTag("task-urgent").fetchSemanticsNode().boundsInRoot.top
        val newer = screen.onNodeWithTag("task-new-high").fetchSemanticsNode().boundsInRoot.top
        val older = screen.onNodeWithTag("task-old-high").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(urgent < newer && newer < older)
        snapshot("toki-tasks-active.png")
        screen.onNodeWithContentDescription("Complete urgent").performClick()
        screen.runOnIdle { org.junit.Assert.assertEquals("urgent" to true, changed) }
        screen.onNodeWithText("Completed").performScrollTo().performClick()
        screen.onNodeWithTag("task-done").performScrollTo()
        screen.onNodeWithContentDescription("Reopen done").performClick()
        screen.runOnIdle { org.junit.Assert.assertEquals("done" to false, changed) }
        snapshot("toki-tasks.png")
    }
    @Test fun taskEditPreservesNotesAndOptionalReminder() {
        val task = com.hackinglab.toki.data.TokiTask().apply { id = "one"; title = "Remember this"; notes = "Existing notes"; priority = "high"; reminderMillis = System.currentTimeMillis() + 3_600_000 }
        var saved: com.hackinglab.toki.data.TaskDraft? = null
        screen.setContent { TOKITheme { TaskForm(task, false, null, {}, { saved = it }) } }
        screen.onNodeWithText("Remember this").performTextReplacement("Updated thought")
        screen.onNodeWithTag("task-form").performScrollToNode(hasText("Save task"))
        screen.onNodeWithText("Save task").performClick()
        screen.runOnIdle { org.junit.Assert.assertEquals(com.hackinglab.toki.data.TaskDraft.from(task).copy(title = "Updated thought"), saved) }
    }
    @Test fun expandedRowOffersEditBesideDelete() {
        var selected: String? = null
        render(onEdit = { selected = it })
        screen.onNodeWithTag("event-one").performScrollTo().performClick()
        screen.onNodeWithText("Edit event").performScrollTo().performClick()
        screen.runOnIdle { org.junit.Assert.assertEquals("one", selected) }
        screen.onNodeWithText("Delete event").assertExists()
    }
    @Test fun editPrefillsAndPreservesCustomRecurrence() {
        val initial = com.hackinglab.toki.data.NewEvent("Existing meeting", "Existing notes", java.time.LocalDateTime.of(2027, 1, 12, 14, 30),
            "Europe/Zurich", reminderMinutes = 30, durationMinutes = 90, rrule = "FREQ=WEEKLY;BYDAY=MO,FR")
        var saved: com.hackinglab.toki.data.NewEvent? = null
        screen.setContent { TOKITheme { NewEventScreen(false, null, {}, { saved = it }, edit = com.hackinglab.toki.data.EditableEvent("one", initial, true)) } }
        screen.onNodeWithText("Edit event").assertIsDisplayed()
        screen.onNodeWithText("Existing meeting").performTextReplacement("Updated meeting")
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Custom repeat"))
        screen.onNodeWithText("Custom repeat").assertIsDisplayed()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Save changes"))
        screen.onNodeWithText("Save changes").performClick()
        org.junit.Assert.assertEquals(initial.copy(title = "Updated meeting"), saved)
    }
    @Test fun editBirthdayPrefillsDateAndCancelDoesNotSave() {
        val initial = com.hackinglab.toki.data.NewEvent("Alex", "", java.time.LocalDateTime.now(), birthday = com.hackinglab.toki.data.BirthdaySpec(10, 8, 1968, 10, 15))
        var cancelled = false
        var saved = false
        screen.setContent { TOKITheme { NewEventScreen(false, null, { cancelled = true }, { saved = true }, edit = com.hackinglab.toki.data.EditableEvent("birthday:one", initial)) } }
        screen.onNodeWithText("8.10.1968").assertExists()
        screen.onNodeWithText("Remind me at 10:15").assertExists()
        screen.onNodeWithText("Cancel").performClick()
        org.junit.Assert.assertTrue(cancelled)
        org.junit.Assert.assertFalse(saved)
    }
    private fun snapshot(name: String) {
        screen.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun agendaSearchAndTabs() {
        render()
        screen.onNodeWithText("NEXT ALARM").assertIsDisplayed()
        screen.onNodeWithText("NEXT EVENT").assertIsDisplayed()
        screen.onNodeWithTag("next-event-tile").assertIsDisplayed()
        org.junit.Assert.assertTrue(screen.onNodeWithTag("next-alarm-tile").fetchSemanticsNode().boundsInRoot.left <
            screen.onNodeWithTag("next-event-tile").fetchSemanticsNode().boundsInRoot.left)
        snapshot("toki-agenda.png")
        screen.onNodeWithText("Title, description or group").assertDoesNotExist()
        screen.onNodeWithContentDescription("Search events").performClick()
        screen.onNodeWithText("Title, description or group").performTextInput("Family")
        screen.onNodeWithText("Show results").performClick()
        screen.onNodeWithText("1 event").performScrollTo().assertIsDisplayed()
        screen.onNodeWithText("Family birthday").performScrollTo().performClick()
        screen.onNodeWithText("Off").assertIsDisplayed()
        screen.onNodeWithText("Clear search").performScrollTo().performClick()
        screen.onNodeWithText("2 events").performScrollTo().assertIsDisplayed()
        screen.onNodeWithText("Alarms", useUnmergedTree = true).performClick()
        screen.onNodeWithText("Alarms, on your terms.").assertIsDisplayed()
        snapshot("toki-alarms.png")
        screen.onNodeWithText("Account", useUnmergedTree = true).performClick()
        screen.onNodeWithText("hello@example.com").assertIsDisplayed()
    }
    @Test fun newEventDefaultsAndSubmission() {
        var saved: com.hackinglab.toki.data.NewEvent? = null
        render(onCreate = { saved = it })
        screen.onNodeWithText("+  New event").performClick()
        screen.onNodeWithText("Time zone").assertDoesNotExist()
        screen.onNode(hasText(java.time.ZoneId.systemDefault().id) and hasSetTextAction()).assertDoesNotExist()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Create event"))
        screen.onNodeWithText("Create event").assertIsNotEnabled()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Title"))
        screen.onNodeWithText("Title").performTextInput("Preview event")
        screen.onNodeWithText("Description (optional)").assertDoesNotExist()
        screen.onNodeWithText("+ Add description").performClick()
        screen.onNodeWithText("Description (optional)").performTextInput("Bring the project notes")
        screen.onNodeWithText("Hide description").performClick()
        screen.onNodeWithText("Description (optional)").assertDoesNotExist()
        screen.onNodeWithText("Edit description").assertIsDisplayed()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Create event"))
        screen.onNodeWithText("Create event").performClick()
        screen.runOnIdle {
            org.junit.Assert.assertEquals("Preview event", saved?.title)
            org.junit.Assert.assertEquals("Bring the project notes", saved?.description)
            org.junit.Assert.assertEquals(java.time.ZoneId.systemDefault().id, saved?.timezone)
            org.junit.Assert.assertEquals(true, saved?.enabled)
            org.junit.Assert.assertEquals(5, saved?.reminderMinutes)
        }
    }
    @Test fun remindedEventRemainsInUpcomingAgenda() {
        render(reminded = true)
        org.junit.Assert.assertTrue(screen.onNodeWithTag("next-event-tile").fetchSemanticsNode().boundsInRoot.left <
            screen.onNodeWithTag("next-alarm-tile").fetchSemanticsNode().boundsInRoot.left)
        screen.onNodeWithText("Your events").performScrollTo()
        screen.onNodeWithText("Due soon").performScrollTo().assertIsDisplayed()
        screen.onNodeWithTag("event-one").performScrollTo().performClick()
        screen.onNodeWithText("The reminder time has passed. This event stays here until its deadline.").performScrollTo().assertIsDisplayed()
    }
    @Test fun allEventsSortByDisplayedDateIncludingPast() {
        render(includePast = true)
        screen.onNodeWithTag("event-filters").performScrollToNode(hasText("All"))
        screen.onNodeWithText("All").performClick()
        screen.onNodeWithTag("event-past").performScrollTo()
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-two"))
        val past = screen.onNodeWithTag("event-past").fetchSemanticsNode().boundsInRoot.top
        val first = screen.onNodeWithTag("event-one").fetchSemanticsNode().boundsInRoot.top
        val second = screen.onNodeWithTag("event-two").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(past < first && first < second)
    }
    @Test fun deletionRequiresConfirmationAndCanBeCancelled() {
        val deleted = mutableListOf<String>()
        render(onDelete = { deleted += it })
        screen.onNodeWithTag("event-one").performScrollTo().performClick()
        screen.onNodeWithText("Delete event").performScrollTo().performClick()
        screen.onNodeWithText("Cancel").performClick()
        screen.runOnIdle { org.junit.Assert.assertTrue(deleted.isEmpty()) }
        screen.onNodeWithText("Delete event").performClick()
        screen.onNodeWithText("Delete permanently").performClick()
        screen.runOnIdle { org.junit.Assert.assertEquals(listOf("one"), deleted) }
    }
    @Test fun groupMemberCannotDeleteSharedEvent() {
        render()
        screen.onNodeWithTag("agenda-content").performScrollToNode(hasTestTag("event-two"))
        screen.onNodeWithTag("event-two").performClick()
        screen.onNodeWithText("Delete event").assertDoesNotExist()
        screen.onNodeWithText("Edit event").assertDoesNotExist()
    }
    @Test fun birthdayFormUsesNineAndUnknownAgeByDefault() {
        var saved: com.hackinglab.toki.data.NewEvent? = null
        render(onCreate = { saved = it })
        screen.onNodeWithText("+  New event").performClick()
        screen.onNodeWithText("Birthday").performClick()
        screen.onNodeWithText("Name").performTextInput("Alex")
        screen.onNodeWithText("Birth date").performTextInput("29.2")
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("March 1"))
        screen.onNodeWithText("March 1").performClick()
        screen.onNodeWithText("Reminder (min before)").assertDoesNotExist()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Remind me at 09:00"))
        screen.onNodeWithText("Remind me at 09:00").assertIsDisplayed()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Create birthday"))
        screen.onNodeWithText("Create birthday").performClick()
        screen.runOnIdle {
            org.junit.Assert.assertEquals("Alex", saved?.title)
            org.junit.Assert.assertEquals(0, saved?.birthday?.birthYear)
            org.junit.Assert.assertEquals(9, saved?.birthday?.hour)
            org.junit.Assert.assertEquals("mar1", saved?.birthday?.leapDayPolicy)
            org.junit.Assert.assertEquals(2, saved?.birthday?.month)
            org.junit.Assert.assertEquals(29, saved?.birthday?.day)
            org.junit.Assert.assertEquals("birthday", saved?.deadline()?.kind)
        }
    }
    @Test fun europeanBirthDateSubmitsCorrectDayMonthAndYear() {
        var saved: com.hackinglab.toki.data.NewEvent? = null
        render(onCreate = { saved = it })
        screen.onNodeWithText("+  New event").performClick()
        screen.onNodeWithText("Birthday").performClick()
        screen.onNodeWithText("Name").performTextInput("Alex")
        screen.onNodeWithText("Birth date").performTextInput("8.10.1968")
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Create birthday"))
        screen.onNodeWithText("Create birthday").performClick()
        screen.runOnIdle {
            org.junit.Assert.assertEquals(8, saved?.birthday?.day)
            org.junit.Assert.assertEquals(10, saved?.birthday?.month)
            org.junit.Assert.assertEquals(1968, saved?.birthday?.birthYear)
        }
    }
    @Test fun birthdayRemainsVisibleAfterReminderAndHasItsOwnFilter() {
        render(includeBirthday = true)
        screen.onNodeWithTag("next-birthday-tile").assertIsDisplayed()
        screen.onNodeWithTag("event-birthday:fixture").assertDoesNotExist()
        screen.onNodeWithTag("next-birthday-tile").performClick()
        screen.onNodeWithText("Birthdays").performScrollTo().performClick()
        screen.onNodeWithText("1 birthday").performScrollTo().assertIsDisplayed()
        screen.onNodeWithTag("event-birthday:fixture").performScrollTo().performClick()
        screen.onNodeWithText("The reminder time has passed. This birthday stays here all day.").performScrollTo().assertIsDisplayed()
        screen.onNodeWithText("Events").performScrollTo().performClick()
        screen.onNodeWithTag("event-birthday:fixture").assertDoesNotExist()
        screen.onNodeWithText("2 events").performScrollTo().assertIsDisplayed()
    }
    @Test fun oldBackendKeepsEventsAvailableButBirthdayCreationDisabled() {
        render(birthdaysAvailable = false)
        screen.onNodeWithText("+  New event").performClick()
        screen.onNodeWithText("Birthday").assertIsNotEnabled()
        screen.onNodeWithText("Title").assertIsDisplayed()
    }
    @Test fun darkAgendaRenders() { render(true); screen.onNodeWithText("NEXT ALARM").assertIsDisplayed(); snapshot("toki-dark.png") }
}
