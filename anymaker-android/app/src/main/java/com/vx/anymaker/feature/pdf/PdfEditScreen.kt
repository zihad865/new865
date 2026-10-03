package com.vx.anymaker.feature.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.ImageFormat
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.pdf.PdfEdit
import com.vx.anymaker.core.pdf.PdfTools
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
import kotlin.math.max
import kotlin.math.min

enum class EditTool(val label: Int, val hint: Int) {
    TEXT(R.string.edit_text, R.string.edit_text_hint),
    HIGHLIGHT(R.string.edit_highlight, R.string.edit_box_hint),
    WHITEOUT(R.string.edit_whiteout, R.string.edit_box_hint),
    IMAGE(R.string.edit_image, R.string.edit_image_hint),
}

private const val HIGHLIGHT = 0xFFFFEB3B.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

class PdfEditViewModel(app: Application) : ToolViewModel(app) {
    var input by mutableStateOf<File?>(null)
        private set
    var pageCount by mutableIntStateOf(0)
        private set
    var page by mutableIntStateOf(0)
        private set
    var pageBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var tool by mutableStateOf(EditTool.TEXT)
    var text by mutableStateOf("")
    var textSize by mutableFloatStateOf(14f)
    var image by mutableStateOf<File?>(null)
        private set
    val edits = mutableStateListOf<PdfEdit>()
    var output by mutableStateOf<File?>(null)
        private set

    fun open(uri: Uri) = work {
        val f = store.importInput(uri, "document.pdf")
        val count = withContext(Dispatchers.IO) { renderPageCount(f) }
        input = f
        pageCount = count
        edits.clear()
        output = null
        showPage(0)
    }

    fun showPage(index: Int) {
        val f = input ?: return
        if (index !in 0 until pageCount) return
        page = index
        work { pageBitmap = withContext(Dispatchers.IO) { renderPage(f, index, 1240) } }
    }

    fun pickImage(uri: Uri) = work {
        image = withContext(Dispatchers.IO) {
            val bmp = Bitmaps.decode(getApplication(), uri, 1200)
            store.newFile("pdf_stamp", "png").also { Bitmaps.write(bmp, it, ImageFormat.PNG) }
        }
    }

    /** A tap at fractions (x, y) of the page. */
    fun onTap(x: Float, y: Float) {
        when (tool) {
            EditTool.TEXT -> if (text.isNotBlank()) edits += PdfEdit.Text(page, x, y, text, textSize, android.graphics.Color.BLACK)
            EditTool.IMAGE -> image?.let { edits += PdfEdit.Picture(page, x, y, 0.3f, it) }
            else -> Unit
        }
        output = null
    }

    /** A drag from (x0, y0) to (x1, y1), page fractions. */
    fun onBox(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (tool != EditTool.HIGHLIGHT && tool != EditTool.WHITEOUT) return
        val l = min(x0, x1)
        val r = max(x0, x1)
        val t = min(y0, y1)
        val b = max(y0, y1)
        if (r - l < 0.01f || b - t < 0.005f) return
        edits += if (tool == EditTool.HIGHLIGHT) PdfEdit.Box(page, l, t, r, b, HIGHLIGHT, 0.4f) else PdfEdit.Box(page, l, t, r, b, WHITE, 1f)
        output = null
    }

    fun undo() {
        val i = edits.indexOfLast { it.page == page }
        if (i >= 0) edits.removeAt(i)
    }

    fun save() {
        val f = input ?: return
        val list = edits.toList()
        work {
            output = withContext(Dispatchers.IO) {
                store.newFile("${f.nameWithoutExtension}_edited", "pdf").also { PdfTools(getApplication()).applyEdits(f, it, list) }
            }
        }
    }

    private fun renderPageCount(f: File): Int = withRenderer(f) { it.pageCount }

    private fun renderPage(f: File, index: Int, width: Int): Bitmap = withRenderer(f) { r ->
        r.openPage(index).use { p ->
            val h = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also { bmp ->
                bmp.eraseColor(WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    private fun <T> withRenderer(f: File, block: (PdfRenderer) -> T): T = try {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(block) }
    } catch (e: SecurityException) {
        throw ImageOpException("This PDF is password-protected. Remove the password with Lock PDF first.", e)
    } catch (e: java.io.IOException) {
        throw ImageOpException("This file can't be opened as a PDF.", e)
    }
}

@Composable
fun PdfEditScreen(onBack: () -> Unit, vm: PdfEditViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::pickImage) }

    ToolScaffold(stringResource(R.string.tool_pdf_edit), onBack, snackbar, "screen_pdf_edit") {
        if (vm.input == null) {
            PickPrompt(stringResource(R.string.action_choose_pdf), Icons.Outlined.PictureAsPdf, { pickPdf.launch(arrayOf("application/pdf")) }, enabled = !vm.busy, body = stringResource(R.string.edit_limits))
        } else {
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    ChoiceChips(EditTool.entries, vm.tool, { stringResource(it.label) }, { vm.tool = it }, !vm.busy)
                    Text(stringResource(vm.tool.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    when (vm.tool) {
                        EditTool.TEXT -> {
                            OutlinedTextField(vm.text, { vm.text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(R.string.edit_text)) }, singleLine = true)
                            Text(stringResource(R.string.edit_text_size, vm.textSize.toInt()), modifier = Modifier.padding(top = 8.dp))
                            Slider(vm.textSize, { vm.textSize = it }, valueRange = 8f..36f)
                        }
                        EditTool.IMAGE -> TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text(stringResource(R.string.action_choose_photo) + if (vm.image != null) " ✓" else "")
                        }
                        else -> Unit
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showPage(vm.page - 1) }, enabled = vm.page > 0 && !vm.busy) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.edit_prev))
                }
                Text(stringResource(R.string.edit_page_of, vm.page + 1, vm.pageCount), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = vm::undo, enabled = vm.edits.any { it.page == vm.page }) { Text(stringResource(R.string.action_undo)) }
                IconButton(onClick = { vm.showPage(vm.page + 1) }, enabled = vm.page < vm.pageCount - 1 && !vm.busy) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.edit_next))
                }
            }
            vm.pageBitmap?.let { PageEditor(it, vm) }
            Text(stringResource(R.string.edit_count, vm.edits.size), style = MaterialTheme.typography.bodySmall)
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        if (vm.input != null) {
            MainButton(stringResource(R.string.edit_save), vm::save, enabled = vm.edits.isNotEmpty(), busyText = vm.working?.asString(), icon = Icons.Outlined.Save)
        }
        vm.output?.let { out -> SaveShareRow({ export.save(out) }, { export.share(out) }, !vm.busy) }
    }
}

/** The rendered page with the pending edits drawn on top; taps and drags add new ones. */
@Composable
private fun PageEditor(bitmap: Bitmap, vm: PdfEditViewModel) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    val thumbs = remember { mutableMapOf<File, Bitmap?>() }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .background(Color.White)
            .testTag("pdf_page"),
    ) {
        Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(vm.tool, vm.page) {
                    detectTapGestures { p -> vm.onTap(p.x / size.width, p.y / size.height) }
                }
                .pointerInput(vm.tool, vm.page) {
                    detectDragGestures(
                        onDragStart = { dragStart = it; dragNow = it },
                        onDrag = { change, _ -> change.consume(); dragNow = change.position },
                        onDragEnd = {
                            val a = dragStart
                            val b = dragNow
                            if (a != null && b != null) vm.onBox(a.x / size.width, a.y / size.height, b.x / size.width, b.y / size.height)
                            dragStart = null
                            dragNow = null
                        },
                        onDragCancel = { dragStart = null; dragNow = null },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            vm.edits.filter { it.page == vm.page }.forEach { e ->
                when (e) {
                    is PdfEdit.Box -> drawRect(
                        Color(e.color).copy(alpha = e.alpha), Offset(e.left * w, e.top * h), Size((e.right - e.left) * w, (e.bottom - e.top) * h),
                    )
                    is PdfEdit.Text -> {
                        // Page points to pixels: the page is shown at w pixels for its width in points (≈ 595 for A4).
                        val px = e.sizePt * w / 595f
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = e.color; textSize = px }
                        drawContext.canvas.nativeCanvas.drawText(e.text, e.x * w, e.y * h + px, paint)
                    }
                    is PdfEdit.Picture -> {
                        val bmp = thumbs.getOrPut(e.image) { runCatching { Bitmaps.decodeFile(e.image, 600) }.getOrNull() }
                        if (bmp != null) {
                            val iw = e.widthFraction * w
                            val ih = iw * bmp.height / bmp.width
                            drawImage(
                                bmp.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset((e.left * w).toInt(), (e.top * h).toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(iw.toInt(), ih.toInt()),
                            )
                        }
                    }
                }
            }
            val a = dragStart
            val b = dragNow
            if (a != null && b != null) {
                drawRect(
                    Color(0xFF0F7B74), Offset(min(a.x, b.x), min(a.y, b.y)), Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
    }
}

