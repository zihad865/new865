package com.vx.anymaker.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.vx.anymaker.core.image.ImageOpException

/** What a QR code holds, and how each kind is written as the standard payload string. */
sealed interface QrContent {
    fun payload(): String

    data class Text(val text: String) : QrContent {
        override fun payload() = text
    }

    data class Wifi(val ssid: String, val password: String, val security: Security, val hidden: Boolean = false) : QrContent {
        enum class Security(val code: String) { WPA("WPA"), WEP("WEP"), NONE("nopass") }

        override fun payload(): String = buildString {
            append("WIFI:T:").append(security.code).append(';')
            append("S:").append(escape(ssid)).append(';')
            if (security != Security.NONE) append("P:").append(escape(password)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
    }

    data class Phone(val number: String) : QrContent {
        override fun payload() = "tel:" + number.filter { it.isDigit() || it == '+' }
    }

    data class Email(val address: String, val subject: String = "", val body: String = "") : QrContent {
        override fun payload(): String {
            val params = listOfNotNull(
                subject.takeIf { it.isNotBlank() }?.let { "subject=" + java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
                body.takeIf { it.isNotBlank() }?.let { "body=" + java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
            )
            return "mailto:" + address.trim() + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        }
    }

    companion object {
        /** Wi-Fi QR fields escape \ ; , : and " with a backslash. */
        fun escape(s: String): String = s.replace(Regex("""([\;,:"])"""), """\\$1""")
    }
}

object QrCodes {
    /** Renders [content] as a black-on-white square QR bitmap of [size] pixels with a quiet zone. */
    fun render(content: String, size: Int = 1024, dark: Int = Color.BLACK, light: Int = Color.WHITE): Bitmap {
        if (content.isEmpty()) throw ImageOpException("Enter something to put in the QR code.")
        val matrix = try {
            QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 2,
                ),
            )
        } catch (e: WriterException) {
            throw ImageOpException("This is too long for a QR code.", e)
        } catch (e: IllegalArgumentException) {
            throw ImageOpException("This is too long for a QR code.", e)
        }
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) pixels[row + x] = if (matrix[x, y]) dark else light
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
