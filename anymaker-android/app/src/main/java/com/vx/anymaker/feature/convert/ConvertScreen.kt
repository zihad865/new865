package com.vx.anymaker.feature.convert

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.ImageFormat
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.core.image.sanitizeStem
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SaveShareRow
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConvertViewModel(app: Application) : ToolViewModel(app) {
    var uris by mutableStateOf<List<Uri>>(emptyList())
        private set
    var format by mutableStateOf(ImageFormat.PNG)
    var quality by mutableIntStateOf(90)
    var outputs by mutableStateOf<List<File>>(emptyList())
        private set
    var failed by mutableIntStateOf(0)
        private set

    fun onPicked(list: List<Uri>) {
        if (list.isEmpty()) return
        uris = list
        outputs = emptyList()
        failed = 0
    }

    fun convert() {
        val list = uris
        val fmt = format
        val q = quality
        work {
            val done = mutableListOf<File>()
            var fails = 0
            list.forEachIndexed { i, uri ->
                progress(UiText.Res(R.string.working_progress, listOf(i + 1, list.size)))
                try {
                    done += withContext(Dispatchers.Default) {
                        val bmp = Bitmaps.decode(getApplication(), uri, 8192)
                        val stem = sanitizeStem(uri.lastPathSegment)
                        store.newFile(stem, fmt.ext).also { Bitmaps.write(bmp, it, fmt, q); bmp.recycle() }
                    }
                } catch (e: ImageOpException) {
                    fails++
                }
            }
            outputs = done
            failed = fails
        }
    }
}

@Composable
fun ConvertScreen(onBack: () -> Unit, vm: ConvertViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { vm.onPicked(it) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolScaffold(stringResource(R.string.tool_convert), onBack, snackbar, "screen_convert") {
        if (vm.uris.isEmpty()) {
            PickPrompt(stringResource(R.string.action_choose_photos), Icons.Outlined.AddPhotoAlternate, launchPick, body = stringResource(R.string.convert_footer))
        } else {
            Row {
                Text(stringResource(R.string.selected_count, vm.uris.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = launchPick, enabled = !vm.busy) { Text(stringResource(R.string.resize_change)) }
            }
        }
        SectionCard(title = stringResource(R.string.format_label), footer = stringResource(R.string.convert_footer)) {
            Column(Modifier.padding(16.dp)) {
                ChoiceChips(ImageFormat.entries, vm.format, { it.name }, { vm.format = it }, !vm.busy)
                if (vm.format != ImageFormat.PNG) {
                    Text(stringResource(R.string.quality_label, vm.quality), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    Slider(value = vm.quality.toFloat(), onValueChange = { vm.quality = it.toInt() }, valueRange = 40f..100f, enabled = !vm.busy)
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(stringResource(R.string.convert_run, vm.uris.size), vm::convert, enabled = vm.uris.isNotEmpty(), busyText = vm.working?.asString(), icon = Icons.Outlined.Transform)
        if (vm.outputs.isNotEmpty() || vm.failed > 0) {
            SectionCard(title = stringResource(R.string.resize_result)) {
                Text(
                    stringResource(R.string.files_count, vm.outputs.size) + " · " + humanSize(vm.outputs.sumOf { it.length() }),
                    modifier = Modifier.padding(16.dp),
                )
                if (vm.failed > 0) Text(stringResource(R.string.result_failed, vm.failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
            }
            if (vm.outputs.isNotEmpty()) SaveShareRow({ export.saveAll(vm.outputs) }, { export.share(vm.outputs) }, !vm.busy)
        }
    }
}
