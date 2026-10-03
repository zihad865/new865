package com.vx.anymaker.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {

    private val titles = mapOf(
        "resize" to "Resize", "pdf_merge" to "Merge PDF", "qr_scan" to "Scan QR", "passport" to "Passport Photo",
    )
    private fun title(t: Tool) = titles[t.id] ?: t.id

    @Test
    fun emptyQuery_returnsAll() {
        assertEquals(allTools, filterTools(allTools, "  ", ::title))
    }

    @Test
    fun matchesTitleIgnoringCase() {
        val ids = filterTools(allTools, "merge", ::title).map { it.id }
        assertEquals(listOf("pdf_merge"), ids)
    }

    @Test
    fun matchesKeywords() {
        val ids = filterTools(allTools, "kb", ::title).map { it.id }
        assertTrue("resize" in ids)
        assertTrue(filterTools(allTools, "visa", ::title).any { it.id == "passport" })
    }

    @Test
    fun everyToolShipsWithUniqueIdAndRoute() {
        assertTrue(allTools.all { it.available })
        assertEquals(allTools.size, allTools.map { it.id }.toSet().size)
        assertEquals(allTools.size, allTools.map { it.route }.toSet().size)
        assertEquals(17, allTools.size)
    }
}
