package com.vx.anymaker.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureCleanerTest {

    @Test
    fun keepsInkMakesPaperTransparentAndTrims() {
        val paper = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(paper)
        c.drawColor(Color.rgb(235, 232, 225))
        c.drawRect(100f, 80f, 300f, 120f, Paint().apply { color = Color.rgb(20, 20, 30) })
        val out = SignatureCleaner.clean(paper, ink = Color.BLUE, threshold = 140, padding = 10)
        assertEquals(220, out.width)
        assertEquals(60, out.height)
        assertEquals(0, Color.alpha(out.getPixel(2, 2)))
        val inkPx = out.getPixel(110, 30)
        assertTrue(Color.alpha(inkPx) > 200)
        assertEquals(Color.BLUE and 0xFFFFFF, inkPx and 0xFFFFFF)
    }

    @Test
    fun blankPaperIsAnError() {
        val paper = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        assertThrows(ImageOpException::class.java) { SignatureCleaner.clean(paper) }
    }
}
