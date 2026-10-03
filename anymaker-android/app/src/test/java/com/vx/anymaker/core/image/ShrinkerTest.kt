package com.vx.anymaker.core.image

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vx.anymaker.testing.writeNoiseJpeg
import android.content.Context
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShrinkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun options(w: Int, h: Int, keepAspect: Boolean = true, kb: Double = 100.0, strict: Boolean = false) =
        Shrinker.Options().apply {
            width = w
            height = h
            this.keepAspect = keepAspect
            maxKb = kb
            strictResolution = strict
        }

    @Test
    fun targetSize_keepsAspectInsideBox() {
        assertArrayEquals(intArrayOf(1000, 750), Shrinker.targetSize(4000, 3000, options(1000, 1000)))
    }

    @Test
    fun targetSize_neverEnlarges() {
        assertArrayEquals(intArrayOf(800, 600), Shrinker.targetSize(800, 600, options(1600, 0)))
    }

    @Test
    fun targetSize_exactStretches() {
        assertArrayEquals(intArrayOf(300, 300), Shrinker.targetSize(4000, 3000, options(300, 300, keepAspect = false)))
    }

    @Test
    fun targetSize_heightOnlyKeepsProportion() {
        assertArrayEquals(intArrayOf(400, 300), Shrinker.targetSize(4000, 3000, options(0, 300)))
    }

    @Test
    fun formatKb_dropsTrailingZero() {
        assertEquals("100", Shrinker.formatKb(100.0))
        assertEquals("60.5", Shrinker.formatKb(60.5))
    }

    @Test
    fun run_fitsUnderBudget() {
        val src = writeNoiseJpeg(File(context.cacheDir, "noise.jpg"), 1200, 900)
        val result = Shrinker.run(src, options(1000, 1000, kb = 60.0)) { }
        assertTrue("got ${result.data.size} bytes", result.data.size <= 60 * 1024)
        assertTrue(result.width <= 1000 && result.height <= 1000)
    }

    @Test
    fun run_strictResolutionThatCannotFit_throws() {
        val src = writeNoiseJpeg(File(context.cacheDir, "noise2.jpg"), 1200, 900)
        try {
            Shrinker.run(src, options(1200, 900, kb = 1.0, strict = true)) { }
            fail("expected ShrinkException")
        } catch (e: Shrinker.ShrinkException) {
            assertTrue(e.message!!.contains("can't fit"))
        }
    }

    @Test
    fun run_unreadableFile_throws() {
        val src = File(context.cacheDir, "not-an-image.jpg").apply { writeText("hello") }
        try {
            Shrinker.run(src, options(100, 100)) { }
            fail("expected ShrinkException")
        } catch (e: Shrinker.ShrinkException) {
            assertTrue(e.message!!.contains("can't be read"))
        }
    }
}
