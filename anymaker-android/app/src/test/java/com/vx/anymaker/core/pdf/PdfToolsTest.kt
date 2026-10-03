package com.vx.anymaker.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vx.anymaker.core.image.ImageOpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfToolsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tools = PdfTools(context)
    private fun file(name: String) = File(context.cacheDir, name)

    private fun noise(w: Int, h: Int, seed: Int): Bitmap {
        val rnd = Random(seed)
        return Bitmap.createBitmap(IntArray(w * h) { 0xFF000000.toInt() or rnd.nextInt(0x1000000) }, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun makePdf(name: String, pages: Int, seed: Int = 1): File =
        file(name).also { out -> tools.imagesToPdf(List(pages) { i -> { noise(800, 600, seed + i) } }, out, PageSize.A4, 24f) }

    @Test
    fun imagesToPdf_onePagePerImage() {
        val pdf = makePdf("photos.pdf", 3)
        assertEquals(3, tools.pageCount(pdf))
        assertTrue(pdf.length() > 1000)
    }

    @Test
    fun mergeKeepsAllPagesInOrder() {
        val a = makePdf("a.pdf", 2)
        val b = makePdf("b.pdf", 3, seed = 9)
        val out = file("merged.pdf")
        tools.merge(listOf(a, b), out)
        assertEquals(5, tools.pageCount(out))
    }

    @Test
    fun mergeNeedsTwoFiles() {
        assertThrows(ImageOpException::class.java) { tools.merge(listOf(makePdf("one.pdf", 1)), file("x.pdf")) }
    }

    @Test
    fun extractAndSplitAll() {
        val src = makePdf("src.pdf", 4)
        val out = file("part.pdf")
        tools.extract(src, listOf(1..2, 4..4), out)
        assertEquals(3, tools.pageCount(out))
        val parts = tools.splitAll(src, { p -> file("page_$p.pdf") })
        assertEquals(4, parts.size)
        parts.forEach { assertEquals(1, tools.pageCount(it)) }
    }

    @Test
    fun lockThenUnlock() {
        val src = makePdf("plain.pdf", 1)
        val locked = file("locked.pdf")
        tools.lock(src, locked, "s3cret")
        assertTrue(tools.isEncrypted(locked))
        assertThrows(PdfPasswordException::class.java) { tools.pageCount(locked) }
        assertThrows(PdfPasswordException::class.java) { tools.unlock(locked, file("bad.pdf"), "wrong") }
        val open = file("open.pdf")
        tools.unlock(locked, open, "s3cret")
        assertFalse(tools.isEncrypted(open))
        assertEquals(1, tools.pageCount(open))
    }

    @Test
    fun shortPasswordRejected() {
        assertThrows(ImageOpException::class.java) { tools.lock(makePdf("p.pdf", 1), file("l.pdf"), "abc") }
    }

    @Test
    fun compressNeverGrowsTheFile() {
        val src = file("big.pdf").also { out ->
            tools.imagesToPdf(listOf({ noise(2400, 1800, 3) }), out, PageSize.FIT, 0f, jpegQuality = 0.98f)
        }
        val out = file("small.pdf")
        tools.compress(src, out, PdfCompression.HIGH)
        assertTrue("${out.length()} vs ${src.length()}", out.length() <= src.length())
        assertEquals(1, tools.pageCount(out))
    }

    @Test
    fun editsAreWrittenAndPdfStaysValid() {
        val src = makePdf("edit.pdf", 2)
        val stamp = file("stamp.png").also { f ->
            Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .compress(Bitmap.CompressFormat.PNG, 100, f.outputStream())
        }
        val out = file("edited.pdf")
        tools.applyEdits(
            src, out,
            listOf(
                PdfEdit.Box(0, 0.1f, 0.1f, 0.5f, 0.2f, Color.WHITE, 1f),
                PdfEdit.Text(0, 0.1f, 0.1f, "Approved 2026", 14f, Color.BLACK),
                PdfEdit.Picture(1, 0.6f, 0.8f, 0.2f, stamp),
            ),
        )
        assertEquals(2, tools.pageCount(out))
        assertTrue(out.length() > src.length())
    }

    @Test
    fun nonPdfIsAReadableError() {
        val junk = file("junk.pdf").apply { writeText("hello") }
        assertThrows(ImageOpException::class.java) { tools.pageCount(junk) }
    }
}
