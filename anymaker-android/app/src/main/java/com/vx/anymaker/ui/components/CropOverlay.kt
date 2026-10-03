package com.vx.anymaker.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A crop window in image pixels. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        /** The largest centered window with [ratio] (width/height) inside a [w]×[h] image; null ratio = whole image. */
        fun centered(w: Int, h: Int, ratio: Float?): CropRect {
            if (ratio == null) return CropRect(0f, 0f, w.toFloat(), h.toFloat())
            var cw = w.toFloat()
            var ch = cw / ratio
            if (ch > h) {
                ch = h.toFloat()
                cw = ch * ratio
            }
            val l = (w - cw) / 2
            val t = (h - ch) / 2
            return CropRect(l, t, l + cw, t + ch)
        }
    }
}

internal enum class Handle { MOVE, TL, TR, BL, BR }

/**
 * Shows [bitmap] with a draggable crop window. Dragging inside moves it; dragging a corner
 * resizes it, keeping [ratio] (width/height) when one is set.
 */
@Composable
fun CropOverlay(bitmap: Bitmap, crop: CropRect, ratio: Float?, onChange: (CropRect) -> Unit, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val density = LocalDensity.current
    val touch = with(density) { 32.dp.toPx() }
    val minSide = 32f

    BoxWithConstraints(modifier = modifier.fillMaxWidth().heightIn(max = 460.dp), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = with(density) { 460.dp.toPx() }
        val scale = min(maxW / bitmap.width, maxH / bitmap.height)
        val viewW = bitmap.width * scale
        val viewH = bitmap.height * scale
        Box(Modifier.size(with(density) { viewW.toDp() }, with(density) { viewH.toDp() }).testTag("crop_overlay")) {
            Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier.fillMaxSize().pointerInput(bitmap) {
                    var handle = Handle.MOVE
                    detectDragGestures(
                        onDragStart = { p ->
                            val c = currentCrop
                            val corners = mapOf(
                                Handle.TL to Offset(c.left * scale, c.top * scale),
                                Handle.TR to Offset(c.right * scale, c.top * scale),
                                Handle.BL to Offset(c.left * scale, c.bottom * scale),
                                Handle.BR to Offset(c.right * scale, c.bottom * scale),
                            )
                            handle = corners.entries.firstOrNull { (it.value - p).getDistance() < touch }?.key ?: Handle.MOVE
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            val dx = drag.x / scale
                            val dy = drag.y / scale
                            onChange(dragCrop(currentCrop, handle, dx, dy, bitmap.width.toFloat(), bitmap.height.toFloat(), currentRatio, minSide))
                        },
                    )
                },
            ) {
                val r = Rect(crop.left * scale, crop.top * scale, crop.right * scale, crop.bottom * scale)
                clipRect(r.left, r.top, r.right, r.bottom, clipOp = ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = 0.55f))
                }
                drawRect(Color.White, r.topLeft, r.size, style = Stroke(width = 2.dp.toPx()))
                // Rule-of-thirds guides.
                for (i in 1..2) {
                    val x = r.left + r.width * i / 3
                    val y = r.top + r.height * i / 3
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
                }
                val h = 10.dp.toPx()
                listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach {
                    drawRect(Color.White, Offset(it.x - h / 2, it.y - h / 2), Size(h, h))
                }
            }
        }
    }
}

/** Pure geometry for one drag step; kept separate so it can be unit-tested. */
internal fun dragCrop(c: CropRect, handle: Handle, dx: Float, dy: Float, w: Float, h: Float, ratio: Float?, minSide: Float): CropRect {
    if (handle == Handle.MOVE) {
        val nx = (c.left + dx).coerceIn(0f, w - c.width)
        val ny = (c.top + dy).coerceIn(0f, h - c.height)
        return CropRect(nx, ny, nx + c.width, ny + c.height)
    }
    var l = c.left
    var t = c.top
    var r = c.right
    var b = c.bottom
    when (handle) {
        Handle.TL -> { l += dx; t += dy }
        Handle.TR -> { r += dx; t += dy }
        Handle.BL -> { l += dx; b += dy }
        Handle.BR -> { r += dx; b += dy }
        Handle.MOVE -> Unit
    }
    l = l.coerceIn(0f, c.right - minSide)
    r = r.coerceIn(c.left + minSide, w)
    t = t.coerceIn(0f, c.bottom - minSide)
    b = b.coerceIn(c.top + minSide, h)
    if (ratio != null) {
        // Follow whichever side moved more, then derive the other from the ratio, anchored at the opposite corner.
        var nw = r - l
        var nh = b - t
        if (abs(dx) >= abs(dy)) nh = nw / ratio else nw = nh * ratio
        val anchorRight = handle == Handle.TL || handle == Handle.BL
        val anchorBottom = handle == Handle.TL || handle == Handle.TR
        val ax = if (anchorRight) c.right else c.left
        val ay = if (anchorBottom) c.bottom else c.top
        val maxW = if (anchorRight) ax else w - ax
        val maxH = if (anchorBottom) ay else h - ay
        val s = min(1f, min(maxW / nw, maxH / nh))
        nw = max(minSide, nw * s)
        nh = max(minSide / ratio, nh * s)
        l = if (anchorRight) ax - nw else ax
        r = if (anchorRight) ax else ax + nw
        t = if (anchorBottom) ay - nh else ay
        b = if (anchorBottom) ay else ay + nh
    }
    return CropRect(l, t, r, b)
}

/** Crops [src] to [c] (image pixels), clamped to the bitmap. */
fun cropBitmap(src: Bitmap, c: CropRect): Bitmap {
    val l = c.left.toInt().coerceIn(0, src.width - 1)
    val t = c.top.toInt().coerceIn(0, src.height - 1)
    val w = c.width.toInt().coerceIn(1, src.width - l)
    val h = c.height.toInt().coerceIn(1, src.height - t)
    return Bitmap.createBitmap(src, l, t, w, h)
}
