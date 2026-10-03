package com.vx.anymaker.core.image

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vx.anymaker.testing.writeNoiseJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = ImageRepository(context, io = Dispatchers.Unconfined, compute = Dispatchers.Unconfined)

    @Test
    fun importThenShrink_writesResultUnderBudget() = runTest {
        val src = writeNoiseJpeg(File(context.filesDir, "Holiday photo.jpg"), 1600, 1200)
        val imported = repo.importToCache(Uri.fromFile(src))
        assertEquals(1600, imported.width)
        assertEquals(1200, imported.height)
        assertEquals(src.length(), imported.bytes)

        val options = Shrinker.Options().apply { width = 800; height = 800; maxKb = 50.0 }
        val out = repo.shrink(imported, options) { }
        assertTrue(out.file.isFile)
        assertTrue("got ${out.bytes}", out.bytes <= 50 * 1024)
        assertTrue(out.file.name.endsWith(".jpg"))
        assertTrue(out.file.name.startsWith("photo") || out.file.name.startsWith("Holiday"))
    }

    @Test
    fun importOfNonImage_failsWithReadableMessage() = runTest {
        val bad = File(context.filesDir, "notes.txt").apply { writeText("not a photo") }
        try {
            repo.importToCache(Uri.fromFile(bad))
            fail("expected ImageOpException")
        } catch (e: ImageOpException) {
            assertEquals("This file isn't a supported image.", e.message)
        }
        assertTrue(File(context.cacheDir, ImageRepository.SOURCES_DIR).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun repeatedResults_getUniqueNames() = runTest {
        val src = writeNoiseJpeg(File(context.filesDir, "a.jpg"), 400, 300)
        val imported = repo.importToCache(Uri.fromFile(src))
        val options = Shrinker.Options().apply { maxKb = 200.0 }
        val first = repo.shrink(imported, options) { }
        val second = repo.shrink(imported, options) { }
        assertTrue(first.file != second.file)
        assertTrue(first.file.isFile && second.file.isFile)
    }
}
