package com.vx.anymaker.feature.resize

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vx.anymaker.core.image.ImportedImage
import com.vx.anymaker.core.image.ShrinkOutput
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.theme.AnymakerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ResizeContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val image = ImportedImage(File("/nonexistent/a.img"), "a", 2_400_000, 4000, 3000)
    private val output = ShrinkOutput(File("/nonexistent/a_1000x750.jpg"), 1000, 750, 1000, 750, 58_000, 87, false)
    private var converts = 0
    private var saves = 0

    private fun show(state: ResizeUiState) = compose.setContent {
        AnymakerTheme(dynamicColor = false) {
            ResizeContent(
                state = state,
                snackbar = SnackbarHostState(),
                onBack = {},
                onPick = {},
                onFormChange = {},
                onLockChange = {},
                onConvert = { converts++ },
                onSave = { saves++ },
                onShare = {},
            )
        }
    }

    @Test
    fun emptyState_asksForPhotoAndDisablesConvert() {
        show(ResizeUiState())
        compose.onNodeWithText("Choose photo").assertIsDisplayed()
        compose.onNodeWithTag("convert_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun loadedState_showsOriginalAndConverts() {
        show(ResizeUiState(image = image))
        compose.onNodeWithText("Original  4000 × 3000 · 2.29 MB").assertIsDisplayed()
        compose.onNodeWithTag("convert_button").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, converts)
    }

    @Test
    fun doneState_showsResultAndSaves() {
        show(ResizeUiState(image = image, output = output))
        compose.onNodeWithTag("result_card").performScrollTo()
        compose.onNodeWithText("1000 × 750").assertIsDisplayed()
        compose.onNodeWithText("56.6 KB · JPG").assertIsDisplayed()
        compose.onNodeWithText("87% · Excellent").assertIsDisplayed()
        compose.onNodeWithTag("save_button").performScrollTo().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun errorState_showsMessage() {
        show(ResizeUiState(image = image, error = UiText.Raw("Can't fit this photo in 1 KB. Raise the size limit.")))
        compose.onNodeWithTag("error_text").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Can't fit this photo in 1 KB. Raise the size limit.").assertExists()
    }

    @Test
    fun busyState_showsProgressText() {
        show(ResizeUiState(image = image, working = UiText.Raw("Compressing 1000 × 750…")))
        compose.onNodeWithText("Compressing 1000 × 750…").performScrollTo().assertIsDisplayed()
    }
}
