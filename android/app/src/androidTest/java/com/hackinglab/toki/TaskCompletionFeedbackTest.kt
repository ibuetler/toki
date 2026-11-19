package com.hackinglab.toki

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hackinglab.toki.data.TokiTask
import com.hackinglab.toki.ui.theme.TOKITheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** In-memory save states; never accesses real sessions, tasks or the backend. */
class TaskCompletionFeedbackTest {
    @get:Rule val screen = createComposeRule()
    @Test fun completionIsVisibleWhileSavingAndRollsBackOnFailure() {
        val pending = mutableStateOf<Boolean?>(null)
        val task = TokiTask().apply { id = "fixture"; title = "Book the workshop"; priority = "normal" }
        var requests = 0
        screen.setContent { TOKITheme {
            TaskCard(task, pending.value != null, System.currentTimeMillis(), { requests++; pending.value = it }, {}, {}, pendingCompletion = pending.value)
        } }
        screen.onNodeWithContentDescription("Complete Book the workshop").assertIsOff().performClick()
        screen.onNodeWithContentDescription("Reopen Book the workshop").assertIsOn().assertIsNotEnabled()
        screen.onNodeWithText("Private · Saving…").assertExists()
        screen.onNodeWithTag("task-fixture").assertIsDisplayed()
        screen.runOnIdle { assertEquals(1, requests); assertFalse(task.completed); pending.value = null }
        screen.onNodeWithContentDescription("Complete Book the workshop").assertIsOff().assertIsEnabled()
        screen.onNodeWithText("Private · Saving…").assertDoesNotExist()
    }
    @Test fun reopeningShowsUncheckedStateUntilSaveConfirms() {
        val pending = mutableStateOf<Boolean?>(null)
        val task = TokiTask().apply { id = "done"; title = "Send the notes"; priority = "normal"; completed = true }
        screen.setContent { TOKITheme {
            TaskCard(task, pending.value != null, System.currentTimeMillis(), { pending.value = it }, {}, {}, pendingCompletion = pending.value)
        } }
        screen.onNodeWithContentDescription("Reopen Send the notes").assertIsOn().performClick()
        screen.onNodeWithContentDescription("Complete Send the notes").assertIsOff().assertIsNotEnabled()
        screen.onNodeWithText("Private · Reopening…").assertExists()
        screen.runOnIdle { task.completed = false; pending.value = null }
        screen.onNodeWithContentDescription("Complete Send the notes").assertIsOff().assertIsEnabled()
        screen.onNodeWithText("Private · Reopening…").assertDoesNotExist()
    }
}
