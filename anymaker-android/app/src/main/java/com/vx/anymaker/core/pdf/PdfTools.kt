package com.vx.anymaker.core.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.vx.anymaker.R
import com.vx.anymaker.core.image.ImageOpException
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Page size for new PDFs. [FIT] makes each page the size of its image. */
enum class PageSize(val rect: PDRectangle?) { A4(PDRectangle.A4), LETTER(PDRectangle.LETTER), FIT(null) }

/** How hard Compress PDF works: JPEG quality and the longest side images are reduced to. */
enum class PdfCompression(val quality: Float, val maxSide: Int) {
    LOW(0.85f, 2400),
    MEDIUM(0.7f, 1600),
    HIGH(0.5f, 1100),
}

/**
 * Something to draw onto an existing page. Positions are fractions (0-1) of the page
 * width/height measured from the top-left corner, matching how the page is shown on screen.
 */
sealed interface PdfEdit {
    val page: Int

    data class Text(override val page: Int, val x: Float, val y: Float, val text: String, val sizePt: Float, val color: Int) : PdfEdit
    data class Box(override val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val alpha: Float) : PdfEdit
    data class Picture(override val page: Int, val left: Float, val top: Float, val widthFraction: Float, val image: File) : PdfEdit
}

/** PDF operations on files in app storage. All calls block; run them off the main thread. */
class PdfTools(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private fun open(file: File, password: String? = null): PDDocument = try {
        if (password == null) PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        else PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
    } catch (e: InvalidPasswordException) {
        throw PdfPasswordException()
    } catch (e: IOException) {
        throw ImageOpException("This file can't be opened as a PDF.", e)
    }

    fun pageCount(file: File, password: String? = null): Int = open(file, password).use { it.numberOfPages }

    fun isEncrypted(file: File): Boolean = try {
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.isEncrypted }
    } catch (e: InvalidPasswordException) {
        true
    } catch (e: IOException) {
        throw ImageOpException("This file can't be opened as a PDF.", e)
    }

    fun merge(inputs: List<File>, out: File) {
        if (inputs.size < 2) throw ImageOpException("Choose at least two PDFs.")
        inputs.forEach { if (isEncrypted(it)) throw ImageOpException("“${it.name}” is password-protected. Unlock it first.") }
        try {
            val merger = PDFMergerUtility()
            inputs.forEach { merger.addSource(it) }
            merger.destinationFileName = out.path
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        } catch (e: IOException) {
            out.delete()
            throw ImageOpException("Couldn't merge: ${e.message}", e)
        }
    }

    /** Writes the pages in [ranges] (1-based) of [input] into [out] as one PDF. */
    fun extract(input: File, ranges: List<IntRange>, out: File, password: String? = null) {
        open(input, password).use { src ->
            PDDocument().use { dst ->
                ranges.forEach { r -> r.forEach { p -> dst.importPage(src.getPage(p - 1)) } }
                save(dst, out)
            }
        }
    }

    /** Writes every page of [input] to its own PDF; [outFor] names the file for a 1-based page. */
    fun splitAll(input: File, outFor: (Int) -> File, password: String? = null): List<File> =
        open(input, password).use { src ->
            (1..src.numberOfPages).map { p ->
                PDDocument().use { dst ->
                    dst.importPage(src.getPage(p - 1))
                    outFor(p).also { save(dst, it) }
                }
            }
        }

    fun compress(input: File, out: File, level: PdfCompression, password: String? = null) {
        open(input, password).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val x = res.getXObject(name) as? PDImageXObject ?: continue
                    val bmp = try {
                        x.image
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    val longest = max(bmp.width, bmp.height)
                    val scaled = if (longest > level.maxSide) {
                        val s = level.maxSide.toFloat() / longest
                        Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()), true)
                    } else {
                        bmp
                    }
                    // Images with transparency keep it (lossless); others become JPEG.
                    val replacement = if (scaled.hasAlpha()) LosslessFactory.createFromImage(doc, scaled)
                    else JPEGFactory.createFromImage(doc, scaled, level.quality)
                    res.put(name, replacement)
                    if (scaled != bmp) scaled.recycle()
                    bmp.recycle()
                }
            }
            save(doc, out)
        }
        // Never hand back a "compressed" file that grew.
        if (out.length() >= input.length()) input.copyTo(out, overwrite = true)
    }

    fun lock(input: File, out: File, password: String) {
        if (password.length < 4) throw ImageOpException("Use a password of at least 4 characters.")
        open(input).use { doc ->
            if (doc.isEncrypted) throw ImageOpException("This PDF already has a password.")
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            save(doc, out)
        }
    }

    fun unlock(input: File, out: File, password: String) {
        open(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            save(doc, out)
        }
    }

    /**
     * Builds one PDF from [images] in order, loading one bitmap at a time so long documents
     * fit in memory. [marginPt] applies to fixed page sizes. [onPage] reports 1-based progress.
     */
    fun imagesToPdf(
        images: List<() -> Bitmap>,
        out: File,
        size: PageSize,
        marginPt: Float,
        jpegQuality: Float = 0.85f,
        onPage: (Int) -> Unit = {},
    ) {
        if (images.isEmpty()) throw ImageOpException("Choose at least one photo.")
        PDDocument().use { doc ->
            images.forEachIndexed { index, load ->
                onPage(index + 1)
                val bmp = load()
                val pageRect = size.rect?.let { base ->
                    // Turn the page sideways for landscape photos.
                    if (bmp.width > bmp.height) PDRectangle(base.height, base.width) else base
                } ?: PDRectangle(bmp.width * 72f / 150f, bmp.height * 72f / 150f)
                val page = PDPage(pageRect)
                doc.addPage(page)
                val img = if (bmp.hasAlpha()) LosslessFactory.createFromImage(doc, bmp) else JPEGFactory.createFromImage(doc, bmp, jpegQuality)
                val margin = if (size == PageSize.FIT) 0f else marginPt
                val boxW = pageRect.width - 2 * margin
                val boxH = pageRect.height - 2 * margin
                val scale = min(boxW / bmp.width, boxH / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, (pageRect.width - w) / 2, (pageRect.height - h) / 2, w, h)
                }
                bmp.recycle()
            }
            save(doc, out)
        }
    }

    fun applyEdits(input: File, out: File, edits: List<PdfEdit>, password: String? = null) {
        if (edits.isEmpty()) throw ImageOpException("Add something to the page first.")
        open(input, password).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            var font: PDFont? = null
            edits.groupBy { it.page }.forEach { (pageIndex, pageEdits) ->
                if (pageIndex !in 0 until doc.numberOfPages) return@forEach
                val page = doc.getPage(pageIndex)
                val box = page.cropBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (edit in pageEdits) {
                        when (edit) {
                            is PdfEdit.Box -> {
                                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = edit.alpha }
                                cs.saveGraphicsState()
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(Color.red(edit.color) / 255f, Color.green(edit.color) / 255f, Color.blue(edit.color) / 255f)
                                val x = box.lowerLeftX + edit.left * box.width
                                val yTop = box.upperRightY - edit.top * box.height
                                val w = (edit.right - edit.left) * box.width
                                val h = (edit.bottom - edit.top) * box.height
                                cs.addRect(x, yTop - h, w, h)
                                cs.fill()
                                cs.restoreGraphicsState()
                            }
                            is PdfEdit.Text -> {
                                val f = font ?: loadFont(doc).also { font = it }
                                cs.beginText()
                                cs.setFont(f, edit.sizePt)
                                cs.setNonStrokingColor(Color.red(edit.color) / 255f, Color.green(edit.color) / 255f, Color.blue(edit.color) / 255f)
                                val x = box.lowerLeftX + edit.x * box.width
                                val y = box.upperRightY - edit.y * box.height - edit.sizePt
                                cs.newLineAtOffset(x, y)
                                try {
                                    cs.showText(edit.text)
                                } catch (e: IllegalArgumentException) {
                                    throw ImageOpException("This text uses letters the PDF font can't show yet.", e)
                                }
                                cs.endText()
                            }
                            is PdfEdit.Picture -> {
                                val img = PDImageXObject.createFromFileByContent(edit.image, doc)
                                val w = edit.widthFraction * box.width
                                val h = w * img.height / img.width
                                val x = box.lowerLeftX + edit.left * box.width
                                val yTop = box.upperRightY - edit.top * box.height
                                cs.drawImage(img, x, yTop - h, w, h)
                            }
                        }
                    }
                }
            }
            save(doc, out)
        }
    }

    // Font files are stored uncompressed like raw resources, so openRawResource reads them directly.
    @SuppressLint("ResourceType")
    private fun loadFont(doc: PDDocument): PDFont =
        context.resources.openRawResource(R.font.plusjakartasans_regular).use { PDType0Font.load(doc, it) }

    private fun save(doc: PDDocument, out: File) {
        try {
            doc.save(out)
        } catch (e: IOException) {
            out.delete()
            throw ImageOpException("Couldn't write the PDF: ${e.message}", e)
        }
    }
}

/** The PDF needs a password, or the one given was wrong. */
class PdfPasswordException : Exception("This PDF is password-protected.")
