package com.vx.anymaker.feature.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Grey/white squares that show where an image is transparent. */
internal fun Modifier.drawCheckers(): Modifier = drawBehind {
    val cell = 10.dp.toPx()
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if ((r + c) % 2 == 0) {
                drawRect(Color.White, Offset(c * cell, r * cell), Size(cell, cell))
            }
        }
    }
}
