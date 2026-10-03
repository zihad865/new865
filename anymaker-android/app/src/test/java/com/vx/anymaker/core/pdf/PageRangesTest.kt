package com.vx.anymaker.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageRangesTest {

    @Test
    fun parsesSinglesRangesAndOpenEnds() {
        assertEquals(listOf(1..3, 5..5, 8..10), parsePageRanges("1-3, 5 ,8-", 10))
    }

    @Test
    fun rejectsOutOfRangeBackwardsAndJunk() {
        assertEquals("Pages go from 1 to 4", assertThrows(IllegalArgumentException::class.java) { parsePageRanges("2-9", 4) }.message)
        assertEquals("“3-1” goes backwards", assertThrows(IllegalArgumentException::class.java) { parsePageRanges("3-1", 4) }.message)
        assertEquals("“a” isn't a page or range", assertThrows(IllegalArgumentException::class.java) { parsePageRanges("a", 4) }.message)
        assertThrows(IllegalArgumentException::class.java) { parsePageRanges(" , ", 4) }
    }
}
