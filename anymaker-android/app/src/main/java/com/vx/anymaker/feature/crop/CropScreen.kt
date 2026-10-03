package com.vx.anymaker.feature.crop

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.ImageFormat
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.CropOverlay
import com.vx.anymaker.ui.components.CropRect
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SaveShareRow
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.cropBitmap
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Aspect presets; null ratio means free. */
enum class CropShape(val label: String, val ratio: Float?) {
    FREE("", null), SQUARE("1:1", 1f), R4_3("4:3", 4f / 3f), R3_4("3:4", 3f / 4f), R16_9("16:9", 16f / 9f), R9_16("9:16", 9f / 16f),
}

class CropViewModel(app: Application) : ToolViewModel(app) {
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var crop by mutableStateOf(CropRect(0f, 0f, 1f, 1f))
    var shape by mutableStateOf(CropShape.FREE)
        private set
    var format by mutableStateOf(ImageFormat.JPG)
    var output by mutableStateOf<File?>(null)
        private set
    private var stem = "photo"

    fun load(uri: Uri) = work {
        val bmp = withContext(Dispatchers.IO) { Bitmaps.decode(getApplication(), uri, 4096) }
        stem = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "photo"
        bitmap = bmp
        output = null
        crop = CropRect.centered(bmp.width, bmp.height, shape.ratio)
    }

    fun setShape(s: CropShape) {
        shape = s
        bitmap?.let { crop = CropRect.centered(it.width, it.height, s.ratio) }
    }

    fun transform(op: (Bitmap) -> Bitmap) {
        val src = bitmap ?: return
        work {
            val out = withContext(Dispatchers.Default) { op(src) }
            bitmap = out
            output = null
            crop = CropRect.centered(out.width, out.height, shape.ratio)
        }
    }

    fun apply() {
        val src = bitmap ?: return
        val c = crop
        val fmt = format
        work {
            output = withContext(Dispatchers.Default) {
                val cut = cropBitmap(src, c)
                store.newFile("${stem}_crop", fmt.ext).also { Bitmaps.write(cut, it, fmt) }
            }
        }
    }
}

@Composable
fun CropScreen(onBack: () -> Unit, vm: CropViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::load) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val bmp = vm.bitmap

    ToolScaffold(stringResource(R.string.tool_crop), onBack, snackbar, "screen_crop") {
        if (bmp == null) {
            PickPrompt(stringResource(R.string.action_choose_photo), Icons.Outlined.AddPhotoAlternate, launchPick, enabled = !vm.busy)
        } else {
            CropOverlay(bmp, vm.crop, vm.shape.ratio, onChange = { vm.crop = it })
            Text(stringResource(R.string.crop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { vm.transform { Bitmaps.rotate(it, -90f) } }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.RotateLeft, stringResource(R.string.crop_rotate_left))
                }
                FilledTonalIconButton(onClick = { vm.transform { Bitmaps.rotate(it, 90f) } }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.RotateRight, stringResource(R.string.crop_rotate_right))
                }
                FilledTonalIconButton(onClick = { vm.transform { Bitmaps.flip(it, horizontal = true) } }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.Flip, stringResource(R.string.crop_flip_h))
                }
                FilledTonalIconButton(onClick = { vm.transform { Bitmaps.flip(it, horizontal = false) } }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.Flip, stringResource(R.string.crop_flip_v), modifier = Modifier.rotate(90f))
                }
                TextButton(onClick = launchPick, enabled = !vm.busy) { Text(stringResource(R.string.resize_change)) }
            }
            SectionCard(title = stringResource(R.string.crop_ratio)) {
                Row(Modifier.padding(16.dp)) {
                    ChoiceChips(CropShape.entries, vm.shape, { if (it == CropShape.FREE) stringResource(R.string.crop_free) else it.label }, vm::setShape, !vm.busy)
                }
            }
            SectionCard(title = stringResource(R.string.format_label)) {
                Row(Modifier.padding(16.dp)) {
                    ChoiceChips(ImageFormat.entries, vm.format, { it.name }, { vm.format = it }, !vm.busy)
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        if (bmp != null) {
            MainButton(stringResource(R.string.crop_apply), vm::apply, busyText = vm.working?.asString(), icon = Icons.Outlined.Crop)
        }
        vm.output?.let { out ->
            SaveShareRow(onSave = { export.save(out) }, onShare = { export.share(out) }, enabled = !vm.busy)
        }
    }
}
