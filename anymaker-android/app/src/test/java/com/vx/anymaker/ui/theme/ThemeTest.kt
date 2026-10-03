package com.vx.anymaker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightAndDarkBackgroundsResolve() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        compose.setContent {
            AnymakerTheme(darkTheme = false, dynamicColor = false) { light = MaterialTheme.colorScheme.background }
            AnymakerTheme(darkTheme = true, dynamicColor = false) { dark = MaterialTheme.colorScheme.background }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFFF3F6F8), light)
        assertEquals(Color(0xFF0E161C), dark)
    }
}
