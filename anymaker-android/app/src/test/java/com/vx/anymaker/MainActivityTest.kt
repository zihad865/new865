package com.vx.anymaker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
    fun everyToolOpensItsScreenAndComesBack() {
        val screens = mapOf(
            "batch" to "screen_batch", "crop" to "screen_crop", "convert" to "screen_convert",
            "remove_bg" to "screen_remove_bg", "passport" to "screen_passport", "doc_scan" to "screen_doc_scan",
            "ocr" to "screen_ocr", "qr_scan" to "screen_qr_scan", "qr_create" to "screen_qr_create",
            "signature" to "screen_signature", "images_to_pdf" to "screen_images_to_pdf", "pdf_edit" to "screen_pdf_edit",
            "pdf_merge" to "screen_pdf_merge", "pdf_split" to "screen_pdf_split", "pdf_compress" to "screen_pdf_compress",
            "pdf_lock" to "screen_pdf_lock",
        )
        screens.forEach { (tool, screen) ->
            compose.onNodeWithTag("home_grid").performScrollToNode(hasTestTag("tool_$tool"))
            compose.onNodeWithTag("tool_$tool").performClick()
            compose.onNodeWithTag(screen).assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("home_grid").assertExists()
        }
    }
}
