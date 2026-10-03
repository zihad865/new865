package com.vx.anymaker.core.pdf

/**
 * Parses page selections like "1-3, 5, 8-" (1-based, inclusive; "8-" means to the end)
 * into ranges, validated against [pageCount]. Throws [IllegalArgumentException] with a
 * message suitable for the user.
 */
fun parsePageRanges(text: String, pageCount: Int): List<IntRange> {
    val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    require(parts.isNotEmpty()) { "Enter pages, for example 1-3, 5" }
    return parts.map { part ->
        val m = Regex("""^(\d+)\s*(?:-\s*(\d*))?$""").matchEntire(part)
            ?: throw IllegalArgumentException("“$part” isn't a page or range")
        val start = m.groupValues[1].toInt()
        val hasDash = part.contains('-')
        val end = when {
            !hasDash -> start
            m.groupValues[2].isEmpty() -> pageCount
            else -> m.groupValues[2].toInt()
        }
        require(start in 1..pageCount && end in 1..pageCount) { "Pages go from 1 to $pageCount" }
        require(start <= end) { "“$part” goes backwards" }
        start..end
    }
}
