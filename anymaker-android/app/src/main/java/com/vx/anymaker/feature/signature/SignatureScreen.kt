package com.vx.anymaker.feature.signature

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.ImageFormat
import com.vx.anymaker.core.image.SignatureCleaner
import com.vx.anymaker.feature.common.BitmapPanel
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SaveShareRow
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class SignMode(val label: Int) { DRAW(R.string.sign_draw), PHOTO(R.string.sign_photo) }

enum class Ink(val label: String, val color: Color) { BLACK("Black", Color(0xFF111111)), BLUE("Blue", Color(0xFF0B3D91)) }

class SignatureViewModel(app: Application) : ToolViewModel(app) {
    var mode by mutableStateOf(SignMode.DRAW)
    var ink by mutableStateOf(Ink.BLACK)
    val strokes = mutableStateListOf<List<Offset>>()
    var canvasSize by mutableStateOf(IntSize.Zero)
    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var threshold by mutableFloatStateOf(140f)
    var cleaned by mutableStateOf<Bitmap?>(null)
        private set
    var output by mutableStateOf<File?>(null)
        private set

    fun loadPhoto(uri: Uri) = work {
        photo = withContext(Dispatchers.IO) { Bitmaps.decode(getApplication(), uri, 2000) }
        clean()
    }

    fun clean() {
        val src = photo ?: return
        val t = threshold.toInt()
        val c = ink.color.toArgb()
        work {
            cleaned = withContext(Dispatchers.Default) { SignatureCleaner.clean(src, c, t) }
            output = null
        }
    }

    fun saveDrawing() {
        val size = canvasSize
        val lines = strokes.toList()
        if (lines.isEmpty() || size.width == 0) return
        val color = ink.color.toArgb()
        work {
            output = withContext(Dispatchers.Default) {
                // Render at 2x for a crisp signature, then trim the empty edges.
                val scale = 2f
                val bmp = Bitmap.createBitmap((size.width * scale).toInt(), (size.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = 6f * scale
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                lines.forEach { pts ->
                    val path = android.graphics.Path()
                    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * scale, p.y * scale) else path.lineTo(p.x * scale, p.y * scale) }
                    canvas.drawPath(path, paint)
                }
                val trimmed = SignatureCleaner.clean(bmp, color, threshold = 255)
                store.newFile("signature", "png").also { Bitmaps.write(trimmed, it, ImageFormat.PNG) }
            }
        }
    }

    fun saveCleaned() {
        val bmp = cleaned ?: return
        work { output = withContext(Dispatchers.IO) { store.newFile("signature", "png").also { Bitmaps.write(bmp, it, ImageFormat.PNG) } } }
    }
}

@Composable
fun SignatureScreen(onBack: () -> Unit, vm: SignatureViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::loadPhoto) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolScaffold(stringResource(R.string.tool_signature), onBack, snackbar, "screen_signature", scrollable = vm.mode != SignMode.DRAW) {
        ChoiceChips(SignMode.entries, vm.mode, { stringResource(it.label) }, { vm.mode = it }, !vm.busy)
        SectionCard(title = stringResource(R.string.sign_ink)) {
            Row(Modifier.padding(16.dp)) {
                ChoiceChips(Ink.entries, vm.ink, { it.label }, { vm.ink = it; if (vm.mode == SignMode.PHOTO) vm.clean() }, !vm.busy)
            }
        }
        when (vm.mode) {
            SignMode.DRAW -> {
                Text(stringResource(R.string.sign_draw_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DrawPad(vm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (vm.strokes.isNotEmpty()) vm.strokes.removeAt(vm.strokes.lastIndex) }) { Text(stringResource(R.string.action_undo)) }
                    TextButton(onClick = { vm.strokes.clear() }) { Text(stringResource(R.string.action_clear)) }
                }
                MainButton(stringResource(R.string.sign_save_png), vm::saveDrawing, enabled = vm.strokes.isNotEmpty(), busyText = vm.working?.asString(), icon = Icons.Outlined.Draw)
            }
            SignMode.PHOTO -> {
                val shown = vm.cleaned
                if (shown == null) {
                    PickPrompt(stringResource(R.string.action_choose_photo), Icons.Outlined.AddPhotoAlternate, launchPick, enabled = !vm.busy)
                } else {
                    BitmapPanel(shown, checker = true)
                    SectionCard(title = stringResource(R.string.sign_threshold)) {
                        Slider(vm.threshold, { vm.threshold = it }, onValueChangeFinished = vm::clean, valueRange = 60f..220f, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    TextButton(onClick = launchPick) { Text(stringResource(R.string.resize_change)) }
                    MainButton(stringResource(R.string.sign_save_png), vm::saveCleaned, busyText = vm.working?.asString())
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        vm.output?.let { out -> SaveShareRow({ export.save(out) }, { export.share(out) }, !vm.busy) }
    }
}

@Composable
private fun DrawPad(vm: SignatureViewModel) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val color = vm.ink.color
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .onSizeChanged { vm.canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { current = listOf(it) },
                    onDrag = { change, _ -> change.consume(); current = current + change.position },
                    onDragEnd = { if (current.size > 1) vm.strokes.add(current); current = emptyList() },
                    onDragCancel = { current = emptyList() },
                )
            }
            .testTag("sign_pad"),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            (vm.strokes + listOf(current)).forEach { pts ->
                if (pts.size > 1) {
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
