package com.vx.anymaker.core.image

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Turns a photo of a signature on paper into ink on a transparent background:
 * pixels darker than [threshold] (0-255 luminance) become [ink], everything else
 * becomes transparent, and the result is trimmed to the ink with [padding] pixels around it.
 */
object SignatureCleaner {

    fun clean(src: Bitmap, ink: Int = Color.BLACK, threshold: Int = 140, padding: Int = 12): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        val inkRgb = ink and 0x00FFFFFF
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = pixels[i]
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (Color.alpha(p) > 0 && lum < threshold) {
                    // Darker strokes get more opacity, which keeps edges smooth.
                    val alpha = (255 * (threshold - lum) / threshold.coerceAtLeast(1)).coerceIn(80, 255)
                    pixels[i] = (alpha shl 24) or inkRgb
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                } else {
                    pixels[i] = 0
                }
            }
        }
        if (maxX < 0) throw ImageOpException("No signature found. Use a darker pen or more light.")
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        val left = (minX - padding).coerceAtLeast(0)
        val top = (minY - padding).coerceAtLeast(0)
        val right = (maxX + padding).coerceAtMost(w - 1)
        val bottom = (maxY + padding).coerceAtMost(h - 1)
        val trimmed = Bitmap.createBitmap(out, left, top, right - left + 1, bottom - top + 1)
        if (trimmed != out) out.recycle()
        return trimmed
    }
}
