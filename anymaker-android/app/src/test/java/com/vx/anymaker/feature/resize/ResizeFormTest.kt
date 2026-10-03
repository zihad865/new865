package com.vx.anymaker.feature.resize

import com.vx.anymaker.R
import com.vx.anymaker.core.data.ResizeSettings
import com.vx.anymaker.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeFormTest {

    @Test
    fun validForm_buildsOptions() {
        val (valid, errors) = ResizeForm(width = "1000", height = "", maxKb = "60.5", webp = true).validate()
        assertNotNull(valid)
        assertTrue(!errors.any)
        val o = ResizeForm(width = "1000", height = "", maxKb = "60.5", webp = true).toOptions(valid!!)
        assertEquals(1000, o.width)
        assertEquals(0, o.height)
        assertEquals(60.5, o.maxKb, 0.0)
        assertTrue(o.webp)
    }

    @Test
    fun widthOutOfRange_reportsRange() {
        val (valid, errors) = ResizeForm(width = "5").validate()
        assertNull(valid)
        assertEquals(UiText.Res(R.string.error_dimension_range, listOf(16, 20000)), errors.width)
    }

    @Test
    fun kbZeroOrBlank_isAnError() {
        assertNotNull(ResizeForm(maxKb = "0").validate().second.maxKb)
        assertNotNull(ResizeForm(maxKb = "").validate().second.maxKb)
        assertNotNull(ResizeForm(maxKb = "1.2.3").validate().second.maxKb)
    }

    @Test
    fun roundTripsThroughSettings() {
        val s = ResizeSettings(width = null, height = 300, keepAspect = false, neverReducePixels = true, maxKb = 20.0, webp = true, locked = true)
        val form = ResizeForm.from(s)
        assertEquals("", form.width)
        assertEquals("300", form.height)
        assertEquals("20", form.maxKb)
        assertEquals(s, form.toSettings(form.validate().first!!))
    }

    @Test
    fun aspectAppliesOnlyWithBothSides() {
        assertTrue(ResizeForm(width = "10", height = "10").aspectApplies)
        assertTrue(!ResizeForm(width = "10", height = "").aspectApplies)
    }
}
