package com.vx.anymaker.feature.passport

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AddPhotoAlternate
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.ImageFormat
import com.vx.anymaker.core.ml.MlKit
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.CropOverlay
import com.vx.anymaker.ui.components.CropRect
import com.vx.anymaker.ui.components.InfoRow
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

enum class PassportBg(val label: Int, val color: Color?) {
    KEEP(R.string.passport_keep, null),
    WHITE(R.string.bg_white, Color.White),
    BLUE(R.string.bg_blue, Color(0xFFCFE3F7)),
}

class PassportViewModel(app: Application) : ToolViewModel(app) {
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var size by mutableStateOf(photoSizes[0])
        private set
    var crop by mutableStateOf(CropRect(0f, 0f, 1f, 1f))
    var background by mutableStateOf(PassportBg.WHITE)
    var single by mutableStateOf<File?>(null)
        private set
    var sheet by mutableStateOf<File?>(null)
        private set

    fun load(uri: Uri) = work {
        val bmp = withContext(Dispatchers.IO) { Bitmaps.decode(getApplication(), uri, 3000) }
        bitmap = bmp
        crop = CropRect.centered(bmp.width, bmp.height, size.ratio)
        single = null
        sheet = null
    }

    fun setPhotoSize(s: PhotoSize) {
        size = s
        bitmap?.let { crop = CropRect.centered(it.width, it.height, s.ratio) }
    }

    fun make() {
        val src = bitmap ?: return
        val c = crop
        val s = size
        val bg = background.color
        work {
            val cut = withContext(Dispatchers.Default) { cropBitmap(src, c) }
            val framed = if (bg != null) {
                val subject = MlKit.cutOut(cut)
                withContext(Dispatchers.Default) { Bitmaps.flatten(subject, bg.toArgb()) }
            } else {
                cut
            }
            val (one, many) = withContext(Dispatchers.Default) {
                val photo = Bitmap.createScaledBitmap(framed, s.widthPx, s.heightPx, true)
                val f1 = store.newFile("passport_${s.widthMm.toInt()}x${s.heightMm.toInt()}mm", "jpg").also { Bitmaps.write(photo, it, ImageFormat.JPG, 95) }
                val sheetBmp = printSheet(photo)
                val f2 = store.newFile("passport_sheet_4x6", "jpg").also { Bitmaps.write(sheetBmp, it, ImageFormat.JPG, 95) }
                sheetBmp.recycle()
                f1 to f2
            }
            single = one
            sheet = many
        }
    }
}

@Composable
fun PassportScreen(onBack: () -> Unit, vm: PassportViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::load) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val bmp = vm.bitmap

    ToolScaffold(stringResource(R.string.tool_passport), onBack, snackbar, "screen_passport") {
        SectionCard(title = stringResource(R.string.passport_country), footer = stringResource(R.string.passport_check)) {
            Column(Modifier.padding(16.dp)) {
                ChoiceChips(photoSizes, vm.size, { it.label }, vm::setPhotoSize, !vm.busy)
            }
        }
        if (bmp == null) {
            PickPrompt(stringResource(R.string.action_choose_photo), Icons.Outlined.AddPhotoAlternate, launchPick, enabled = !vm.busy, body = stringResource(R.string.passport_hint))
        } else {
            CropOverlay(bmp, vm.crop, vm.size.ratio, onChange = { vm.crop = it })
            Text(stringResource(R.string.passport_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = launchPick, enabled = !vm.busy) { Text(stringResource(R.string.resize_change)) }
            SectionCard(title = stringResource(R.string.passport_background), footer = stringResource(R.string.bg_footer)) {
                Row(Modifier.padding(16.dp)) {
                    ChoiceChips(PassportBg.entries, vm.background, { stringResource(it.label) }, { vm.background = it }, !vm.busy)
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        if (bmp != null) {
            MainButton(stringResource(R.string.passport_make), vm::make, busyText = vm.working?.asString(), icon = Icons.Outlined.AccountBox)
        }
        val one = vm.single
        val many = vm.sheet
        if (one != null && many != null) {
            SectionCard(title = stringResource(R.string.passport_single)) {
                InfoRow(vm.size.label, "${vm.size.widthPx} × ${vm.size.heightPx} px · 300 DPI")
            }
            SaveShareRow({ export.save(one) }, { export.share(one) }, !vm.busy)
            SectionCard(title = stringResource(R.string.passport_sheet)) {
                InfoRow("6 × 4 in", "1800 × 1200 px")
            }
            SaveShareRow({ export.save(many) }, { export.share(many) }, !vm.busy)
        }
    }
}
