package com.hackinglab.toki

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hackinglab.toki.data.*
import com.hackinglab.toki.ui.theme.TOKITheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalendarSettingsTest {
    @get:Rule val screen = createComposeRule()
    @Test fun wildcardFormShowsPreviewAndSavesScope() {
        val event = Deadline().apply { kind = "calendar"; title = "Marina in CH"; sourceNames = "OST" }
        var saved: CalendarRule? = null
        screen.setContent { TOKITheme {
            CalendarRuleForm(CalendarRule(), listOf(CalendarSource(id="source",name="OST")), listOf(event),false,null,{}, { saved=it })
        } }
        screen.onNodeWithText("Title pattern").performTextInput("Marina *")
        screen.onNodeWithText("Matches 1 currently imported events").assertExists()
        screen.onNodeWithText("All my calendars").performClick()
        screen.onNodeWithText("OST").performClick()
        screen.onNodeWithText("Save rule").performScrollTo().performClick()
        screen.runOnIdle { assertEquals("Marina *",saved?.pattern);assertEquals("source",saved?.source);assertEquals("wildcard",saved?.matchType) }
    }
    @Test fun connectionFormSavesOnlyItsOwnSettings() {
        var saved: CalendarSource? = null
        screen.setContent { TOKITheme { CalendarSourceForm(CalendarSource(),false,null,{}, { saved=it }) } }
        screen.onNodeWithText("Calendar name").performTextInput("OST")
        screen.onNodeWithText("Published ICS URL").performTextInput("https://outlook.office365.com/test.ics")
        screen.onNodeWithText("Save calendar").performScrollTo().performClick()
        screen.runOnIdle { assertEquals("OST",saved?.name);assertEquals("https://outlook.office365.com/test.ics",saved?.url);assertTrue(saved?.enabled==true) }
    }
    @Test fun settingsListsConnectionsAndRulesWithoutDisplayingSecretUrls() {
        var deleted: Pair<String,Boolean>?=null
        val settings=CalendarSettings(listOf(CalendarSource(id="source",name="OST",url="https://outlook.office365.com/secret.ics")), listOf(CalendarRule(id="rule",pattern="Marina *")))
        screen.setContent { TOKITheme { CalendarSettingsScreen(TokiState(calendarSettings=settings),{}, {}, {}, {}, { id,source -> deleted=id to source }) } }
        screen.onNodeWithText("OST").assertExists()
        screen.onNodeWithText("https://outlook.office365.com/secret.ics").assertDoesNotExist()
        screen.onNodeWithTag("calendar-settings").performScrollToNode(hasText("Delete rule"))
        screen.onNodeWithText("Delete rule").performClick()
        screen.onNodeWithText("Confirm").performClick()
        screen.runOnIdle { assertEquals("rule" to false,deleted) }
    }
}
