package com.vx.anymaker.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Output formats every image tool offers. */
enum class ImageFormat(val ext: String, val mime: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
}

object Bitmaps {

    /**
     * Decodes [uri] upright (EXIF applied), no larger than [maxSide] on its long edge.
     * Uses ImageDecoder on Android 9+, which also reads HEIC/HEIF photos.
     */
    fun decode(context: Context, uri: Uri, maxSide: Int = 4096): Bitmap {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > maxSide) {
                        val s = maxSide.toFloat() / longest
                        decoder.setTargetSize((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageOpException("This file isn't a supported image.")
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw ImageOpException("This file isn't a supported image.")
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                    .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            return applyExif(bmp, orientation).let { scaleDown(it, maxSide) }
        } catch (e: ImageOpException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw ImageOpException("This photo is too large for the memory on this device.")
        } catch (e: IOException) {
            throw ImageOpException("Couldn't open this photo: ${e.message}", e)
        } catch (e: SecurityException) {
            throw ImageOpException("Anymaker isn't allowed to read this photo.", e)
        } catch (e: Exception) {
            throw ImageOpException("This file isn't a supported image.", e)
        }
    }

    fun decodeFile(file: File, maxSide: Int = 4096): Bitmap =
        decodePreview(file, maxSide) ?: throw ImageOpException("This file isn't a supported image.")

    fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide.toFloat() / longest
        val out = Bitmap.createScaledBitmap(src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true)
        if (out != src) src.recycle()
        return out
    }

    fun rotate(src: Bitmap, degrees: Float): Bitmap =
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { setRotate(degrees) }, true)

    fun flip(src: Bitmap, horizontal: Boolean): Bitmap =
        Bitmap.createBitmap(
            src, 0, 0, src.width, src.height,
            Matrix().apply { if (horizontal) setScale(-1f, 1f) else setScale(1f, -1f) }, true,
        )

    /** Draws [src] over a solid [color] (needed before saving transparent pixels as JPG). */
    fun flatten(src: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(color)
        c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Encodes [bmp] to [file]. JPG gets a white background; [quality] is ignored for PNG. */
    fun write(bmp: Bitmap, file: File, format: ImageFormat, quality: Int = 92) {
        val toWrite = if (format == ImageFormat.JPG && bmp.hasAlpha()) flatten(bmp, android.graphics.Color.WHITE) else bmp
        val compress = when (format) {
            ImageFormat.JPG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
        }
        try {
            FileOutputStream(file).use { out ->
                if (!toWrite.compress(compress, quality.coerceIn(1, 100), out)) throw ImageOpException("Couldn't encode the image.")
            }
        } catch (e: IOException) {
            file.delete()
            throw ImageOpException("Couldn't store the image: ${e.message}", e)
        } finally {
            if (toWrite != bmp) toWrite.recycle()
        }
    }

    private fun applyExif(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out != bmp) bmp.recycle()
        return out
    }
}
