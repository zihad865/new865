package com.vx.anymaker.feature.passport

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

/** A printed photo size in millimetres, rendered at 300 DPI. */
data class PhotoSize(val label: String, val widthMm: Float, val heightMm: Float) {
    val ratio: Float get() = widthMm / heightMm
    val widthPx: Int get() = mmToPx(widthMm)
    val heightPx: Int get() = mmToPx(heightMm)

    companion object {
        const val DPI = 300
        fun mmToPx(mm: Float): Int = (mm / 25.4f * DPI).roundToInt()
    }
}

/** Common passport and visa photo sizes. Users must still check their authority's rules. */
val photoSizes = listOf(
    PhotoSize("US / India 2×2 in", 50.8f, 50.8f),
    PhotoSize("UK / EU / Schengen 35×45 mm", 35f, 45f),
    PhotoSize("Australia / NZ 35×45 mm", 35f, 45f),
    PhotoSize("Canada 50×70 mm", 50f, 70f),
    PhotoSize("China 33×48 mm", 33f, 48f),
    PhotoSize("Japan 35×45 mm", 35f, 45f),
    PhotoSize("Malaysia 35×50 mm", 35f, 50f),
    PhotoSize("Visa 2×2 in (51×51 mm)", 51f, 51f),
)

/**
 * Tiles [photo] on a 6×4 inch landscape sheet at 300 DPI with thin cut lines and 2 mm gaps,
 * as many as fit.
 */
fun printSheet(photo: Bitmap): Bitmap {
    val sheetW = 6 * PhotoSize.DPI
    val sheetH = 4 * PhotoSize.DPI
    val gap = PhotoSize.mmToPx(2f)
    val cols = ((sheetW - gap) / (photo.width + gap)).coerceAtLeast(1)
    val rows = ((sheetH - gap) / (photo.height + gap)).coerceAtLeast(1)
    val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.WHITE)
    val usedW = cols * photo.width + (cols - 1) * gap
    val usedH = rows * photo.height + (rows - 1) * gap
    val x0 = (sheetW - usedW) / 2
    val y0 = (sheetH - usedH) / 2
    val line = Paint().apply { color = Color.rgb(200, 200, 200); strokeWidth = 1f }
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val x = x0 + c * (photo.width + gap)
            val y = y0 + r * (photo.height + gap)
            canvas.drawBitmap(photo, x.toFloat(), y.toFloat(), null)
            canvas.drawRect(x - 0.5f, y - 0.5f, x + photo.width + 0.5f, y + photo.height + 0.5f, line.apply { style = Paint.Style.STROKE })
        }
    }
    return sheet
}
