package com.vx.anymaker.core.qr

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.vx.anymaker.core.image.ImageOpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrCodesTest {

    @Test
    fun wifiPayloadEscapesSpecialCharacters() {
        val p = QrContent.Wifi("Cafe;Net", "p:a\\ss", QrContent.Wifi.Security.WPA).payload()
        assertEquals("""WIFI:T:WPA;S:Cafe\;Net;P:p\:a\\ss;;""", p)
        assertEquals("WIFI:T:nopass;S:Open;H:true;;", QrContent.Wifi("Open", "ignored", QrContent.Wifi.Security.NONE, hidden = true).payload())
    }

    @Test
    fun phoneAndEmailPayloads() {
        assertEquals("tel:+8801711000000", QrContent.Phone("+880 1711-000000").payload())
        assertEquals("mailto:a@b.co?subject=Hello%20there", QrContent.Email(" a@b.co ", "Hello there").payload())
        assertEquals("mailto:a@b.co", QrContent.Email("a@b.co").payload())
    }

    @Test
    fun renderedCodeDecodesBack() {
        val text = "https://example.com/anymaker?x=1"
        val bmp = QrCodes.render(text, 600)
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        assertEquals(Color.WHITE, px[0])
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px))))
        assertEquals(text, decoded.text)
    }

    @Test
    fun emptyContentIsRejected() {
        assertThrows(ImageOpException::class.java) { QrCodes.render("") }
    }
}
