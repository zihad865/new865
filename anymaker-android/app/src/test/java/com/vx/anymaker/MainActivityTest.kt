package com.vx.anymaker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesOnToolsHome() {
        compose.onNodeWithText("Anymaker").assertIsDisplayed()
        compose.onNodeWithTag("bottom_bar").assertIsDisplayed()
    }

    @Test
    fun resizeOpensAndBottomBarHides() {
        compose.onNodeWithTag("tool_resize").performClick()
        compose.onNodeWithTag("resize_screen").assertIsDisplayed()
        compose.onNodeWithTag("bottom_bar").assertDoesNotExist()
    }

    @Test
    fun filesTabShowsEmptyState() {
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithText("No files yet").assertIsDisplayed()
    }

    @Test
    fun unavailableToolShowsComingSoon() {
        compose.onNodeWithTag("tool_batch").performClick()
        compose.onNodeWithText("Batch is coming in a future update").assertIsDisplayed()
    }
}
