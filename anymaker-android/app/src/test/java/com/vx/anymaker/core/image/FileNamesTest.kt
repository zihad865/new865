package com.vx.anymaker.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun keepsPlainNames() {
        assertEquals("IMG_2041", sanitizeStem("IMG_2041.jpg"))
    }

    @Test
    fun stripsDirectoriesAndLeadingDots() {
        assertEquals("secret", sanitizeStem("/storage/emulated/0/..secret.png"))
    }

    @Test
    fun replacesNonAsciiAndBoundsLength() {
        val stem = sanitizeStem("٢٠٢٤ ছবি 😀 " + "x".repeat(100) + ".jpg")
        assertFalse(stem.contains('/'))
        assertTrue(stem.length <= 60)
        assertTrue(stem.all { it.isLetterOrDigit() && it.code < 128 || it in "._ -" })
    }

    @Test
    fun fallsBackToPhoto() {
        assertEquals("photo", sanitizeStem(null))
        assertEquals("photo", sanitizeStem("...."))
    }

    @Test
    fun humanSizeUnits() {
        assertEquals("512 B", humanSize(512))
        assertEquals("1.5 KB", humanSize(1536))
        assertEquals("2.00 MB", humanSize(2L * 1024 * 1024))
    }
}
