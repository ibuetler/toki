package com.hackinglab.toki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class TokiScreenTest {
    @get:Rule val screen = createAndroidComposeRule<MainActivity>()
    @Test fun homeScreenRendersWithoutCrashing() {
        screen.waitUntil(10_000) {
            screen.onAllNodesWithText("TOKI").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        screen.onNodeWithText("TOKI").assertIsDisplayed()
        screen.onNodeWithText("Make time for what matters.").assertIsDisplayed()
    }
}
