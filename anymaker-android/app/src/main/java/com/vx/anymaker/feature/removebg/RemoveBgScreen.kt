package com.vx.anymaker.feature.removebg

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixHigh
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

/** Background choices; null color keeps transparency. */
enum class BgChoice(val label: Int, val color: Color?) {
    NONE(R.string.bg_transparent, null),
    WHITE(R.string.bg_white, Color.White),
    BLACK(R.string.bg_black, Color.Black),
    BLUE(R.string.bg_blue, Color(0xFF3F7FD6)),
    RED(R.string.bg_red, Color(0xFFD32F2F)),
    GREEN(R.string.bg_green, Color(0xFF2E7D32)),
}

class RemoveBgViewModel(app: Application) : ToolViewModel(app) {
    var source by mutableStateOf<Bitmap?>(null)
        private set
    var cutout by mutableStateOf<Bitmap?>(null)
        private set
    var background by mutableStateOf(BgChoice.NONE)
    var output by mutableStateOf<File?>(null)
        private set

    fun load(uri: Uri) = work {
        source = withContext(Dispatchers.IO) { Bitmaps.decode(getApplication(), uri, 2048) }
        cutout = null
        output = null
    }

    fun remove() {
        val src = source ?: return
        work {
            cutout = MlKit.cutOut(src)
            output = null
        }
    }

    fun save() {
        val cut = cutout ?: return
        val bg = background.color
        work {
            output = withContext(Dispatchers.Default) {
                if (bg == null) {
                    store.newFile("cutout", "png").also { Bitmaps.write(cut, it, ImageFormat.PNG) }
                } else {
                    val flat = Bitmaps.flatten(cut, bg.toArgb())
                    store.newFile("cutout", "jpg").also { Bitmaps.write(flat, it, ImageFormat.JPG, 95); flat.recycle() }
                }
            }
        }
    }
}

@Composable
fun RemoveBgScreen(onBack: () -> Unit, vm: RemoveBgViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::load) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolScaffold(stringResource(R.string.tool_remove_bg), onBack, snackbar, "screen_remove_bg") {
        val shown = vm.cutout ?: vm.source
        if (shown == null) {
            PickPrompt(stringResource(R.string.action_choose_photo), Icons.Outlined.AddPhotoAlternate, launchPick, enabled = !vm.busy, body = stringResource(R.string.bg_footer))
        } else {
            BitmapPanel(shown, checker = vm.cutout != null && vm.background.color == null, background = if (vm.cutout != null) vm.background.color else null)
            TextButton(onClick = launchPick, enabled = !vm.busy) { Text(stringResource(R.string.resize_change)) }
        }
        if (vm.cutout != null) {
            SectionCard(title = stringResource(R.string.bg_background)) {
                Row(Modifier.padding(16.dp)) {
                    ChoiceChips(BgChoice.entries, vm.background, { stringResource(it.label) }, { vm.background = it; }, !vm.busy)
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        if (vm.source != null && vm.cutout == null) {
            MainButton(stringResource(R.string.bg_remove), vm::remove, busyText = vm.working?.asString(), icon = Icons.Outlined.AutoFixHigh)
        }
        if (vm.cutout != null) {
            MainButton(stringResource(R.string.resize_save), vm::save, busyText = vm.working?.asString())
        }
        vm.output?.let { out -> SaveShareRow({ export.save(out) }, { export.share(out) }, !vm.busy) }
    }
}
