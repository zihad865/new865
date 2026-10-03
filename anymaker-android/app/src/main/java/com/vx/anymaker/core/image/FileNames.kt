package com.vx.anymaker.core.image

private const val MAX_STEM = 60

/**
 * File-system and share-safe stem: no directory, no extension, no leading dots, only
 * characters every share target accepts, bounded length. Falls back to "photo".
 */
fun sanitizeStem(name: String?): String {
    if (name == null) return "photo"
    var n = name.substringAfterLast('/')
    val dot = n.lastIndexOf('.')
    if (dot > 0) n = n.substring(0, dot)
    var stem = n.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().replace(Regex("^[.\\s]+"), "")
    if (stem.length > MAX_STEM) stem = stem.substring(0, MAX_STEM).trim()
    return stem.ifEmpty { "photo" }
}

/** Human-readable size: "512 B", "84.2 KB", "3.10 MB". */
fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
}
