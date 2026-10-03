package com.vx.anymaker.core.image

import android.net.Uri

/** Everything the resize flow does with files, behind one seam so the screen logic is testable. */
interface ImageOps {
    suspend fun importToCache(uri: Uri): ImportedImage
    suspend fun shrink(source: ImportedImage, options: Shrinker.Options, onProgress: (String) -> Unit): ShrinkOutput
    /** API 29+: writes into Pictures/Anymaker through MediaStore. */
    suspend fun saveToGallery(output: ShrinkOutput)
    /** Any API: writes into a document the user created with the system file picker. */
    suspend fun writeTo(target: Uri, output: ShrinkOutput)
    fun shareUri(output: ShrinkOutput): Uri
}
