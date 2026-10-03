package com.vx.anymaker.testing

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** Writes a JPEG of random noise, which compresses poorly, so size limits are really exercised. */
fun writeNoiseJpeg(file: File, width: Int, height: Int, seed: Int = 7): File {
    val rnd = Random(seed)
    val pixels = IntArray(width * height) { 0xFF000000.toInt() or rnd.nextInt(0x1000000) }
    val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    bmp.recycle()
    return file
}
