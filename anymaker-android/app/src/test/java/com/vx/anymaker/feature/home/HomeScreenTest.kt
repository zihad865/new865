package com.vx.anymaker.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vx.anymaker.ui.theme.AnymakerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var clicked: Tool? = null

    private fun show() = compose.setContent {
        AnymakerTheme(dynamicColor = false) { HomeScreen(onToolClick = { clicked = it }) }
    }

    @Test
    fun showsSectionsAndResize() {
        show()
        compose.onNodeWithText("Photo").assertIsDisplayed()
        compose.onNodeWithText("Resize").assertIsDisplayed()
        // A lazy grid only composes what is on screen, so check each header after scrolling to it.
        compose.onNodeWithTag("home_grid").performScrollToNode(hasTestTag("tool_qr_scan"))
        compose.onNodeWithText("Scan").assertIsDisplayed()
        compose.onNodeWithTag("home_grid").performScrollToNode(hasTestTag("tool_pdf_lock"))
        compose.onNodeWithText("PDF").assertIsDisplayed()
    }

    @Test
    fun searchFiltersTools() {
        show()
        compose.onNodeWithTag("home_search").performTextInput("pdf")
        compose.onNodeWithText("Resize").assertDoesNotExist()
        compose.onNodeWithText("Merge PDF").assertIsDisplayed()
    }

    @Test
    fun searchWithNoMatch_explains() {
        show()
        compose.onNodeWithTag("home_search").performTextInput("zzzz")
        compose.onNodeWithText("No tool matches “zzzz”").assertIsDisplayed()
    }

    @Test
    fun tappingToolReportsIt() {
        show()
        compose.onNodeWithTag("tool_resize").performClick()
        assertEquals("resize", clicked?.id)
    }
}
