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
    private fun render(dark: Boolean = false, reminded: Boolean = false, includePast: Boolean = false, onCreate: (com.hackinglab.toki.data.NewEvent) -> Unit = {}) {
        val start = System.currentTimeMillis() + 3_600_000
        val events = listOf(
            Deadline().apply { id = "one"; title = "Project milestone"; timezone = "Europe/Zurich"; enabled = true; reminderMinutes = if (reminded) 120 else 0; startMillis = start },
            Deadline().apply { id = "two"; title = "Family birthday"; groupName = "Family"; timezone = "Europe/Zurich"; enabled = false; startMillis = start + 86_400_000 }
        ).reversed() + if (includePast) listOf(Deadline().apply { id = "past"; title = "Earlier deadline"; timezone = "Europe/Zurich"; startMillis = start - 7_200_000 }) else emptyList()
        screen.setContent {
            TOKITheme(darkTheme = dark) {
                TokiScreen(TokiState(ready = true, session = Session("preview", "preview", "hello@example.com"), deadlines = events,
                    notificationsAllowed = true, exactAllowed = true, fullScreenAllowed = true, scheduled = 1,
                    previews = events.associate { it.id to EventPreview(if (it.id == "past") null else it.startMillis, nextReminderStart = if (reminded && it.enabled) it.startMillis + 7 * 86_400_000L else it.startMillis) }),
                    onCreate = onCreate, onLogin = { _, _ -> }, onSync = {}, onLogout = {}, onTest = {}, onNotifications = {}, onExact = {}, onFullScreen = {})
            }
        }
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
        screen.onNode(hasText(java.time.ZoneId.systemDefault().id) and hasSetTextAction()).assertIsDisplayed()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Create event"))
        screen.onNodeWithText("Create event").assertIsNotEnabled()
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Title"))
        screen.onNodeWithText("Title").performTextInput("Preview event")
        screen.onNodeWithTag("new-event-form").performScrollToNode(hasText("Create event"))
        screen.onNodeWithText("Create event").performClick()
        screen.runOnIdle {
            org.junit.Assert.assertEquals("Preview event", saved?.title)
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
        screen.onNodeWithText("All").performClick()
        screen.onNodeWithTag("event-past").performScrollTo()
        val past = screen.onNodeWithTag("event-past").fetchSemanticsNode().boundsInRoot.top
        val first = screen.onNodeWithTag("event-one").fetchSemanticsNode().boundsInRoot.top
        val second = screen.onNodeWithTag("event-two").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(past < first && first < second)
    }
    @Test fun darkAgendaRenders() { render(true); screen.onNodeWithText("NEXT ALARM").assertIsDisplayed(); snapshot("toki-dark.png") }
}
