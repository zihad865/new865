package com.vx.anymaker.core.image

import java.io.File

/** A picked photo copied into app storage, with its upright (EXIF-corrected) size. */
data class ImportedImage(
    val file: File,
    val stem: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
)

/** A converted photo written to app storage, ready to save or share. */
data class ShrinkOutput(
    val file: File,
    val width: Int,
    val height: Int,
    val requestedWidth: Int,
    val requestedHeight: Int,
    val bytes: Long,
    val quality: Int,
    val webp: Boolean,
) {
    val mimeType: String get() = if (webp) "image/webp" else "image/jpeg"
    val downscaled: Boolean get() = width != requestedWidth || height != requestedHeight
}

/** Thrown with a message that can be shown to the user as is. */
class ImageOpException(message: String, cause: Throwable? = null) : Exception(message, cause)
