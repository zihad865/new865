package com.vx.anymaker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CropGeometryTest {

    @Test
    fun centeredWindowKeepsRatioInsideImage() {
        assertEquals(CropRect(250f, 0f, 1750f, 1500f), CropRect.centered(2000, 1500, 1f))
        assertEquals(CropRect(0f, 0f, 400f, 300f), CropRect.centered(400, 300, null))
        val tall = CropRect.centered(1000, 1000, 35f / 45f)
        assertEquals(1000f, tall.height, 0.01f)
        assertEquals(777.78f, tall.width, 0.01f)
    }

    @Test
    fun moveStaysInsideImage() {
        val c = CropRect(0f, 0f, 100f, 100f)
        assertEquals(CropRect(900f, 900f, 1000f, 1000f), dragCrop(c, Handle.MOVE, 5000f, 5000f, 1000f, 1000f, null, 32f))
    }

    @Test
    fun freeCornerResizeIsClampedToMinimum() {
        val c = CropRect(100f, 100f, 300f, 300f)
        assertEquals(CropRect(100f, 100f, 132f, 132f), dragCrop(c, Handle.BR, -500f, -500f, 1000f, 1000f, null, 32f))
    }

    @Test
    fun ratioCornerResizeKeepsRatioAnchoredAtOppositeCorner() {
        val c = CropRect(100f, 100f, 300f, 300f)
        val r = dragCrop(c, Handle.BR, 100f, 10f, 1000f, 1000f, 1f, 32f)
        assertEquals(100f, r.left, 0.01f)
        assertEquals(100f, r.top, 0.01f)
        assertEquals(r.width, r.height, 0.01f)
        assertEquals(300f, r.width, 0.01f)
    }
}
