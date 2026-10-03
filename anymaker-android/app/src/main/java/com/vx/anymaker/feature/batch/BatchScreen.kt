package com.vx.anymaker.feature.batch

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.data.DataStoreResizeSettings
import com.vx.anymaker.core.data.appDataStore
import com.vx.anymaker.core.image.ImageOpException
import com.vx.anymaker.core.image.ImageRepository
import com.vx.anymaker.core.image.Shrinker
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.feature.resize.ResizeForm
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SaveShareRow
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchViewModel(app: Application) : ToolViewModel(app) {
    private val images = ImageRepository(app)

    var uris by mutableStateOf<List<Uri>>(emptyList())
        private set
    var form by mutableStateOf(ResizeForm())
        private set
    var errors by mutableStateOf(ResizeForm.Errors())
        private set
    var outputs by mutableStateOf<List<File>>(emptyList())
        private set
    var failed by mutableStateOf(0)
        private set
    var bytesIn by mutableStateOf(0L)
        private set

    init {
        viewModelScope.launch {
            val saved = DataStoreResizeSettings(app.appDataStore).settings.first()
            form = ResizeForm.from(saved).copy(locked = false)
        }
    }

    fun onPicked(list: List<Uri>) {
        if (list.isEmpty()) return
        uris = list
        outputs = emptyList()
        failed = 0
    }

    fun onFormChange(f: ResizeForm) {
        form = f
        errors = ResizeForm.Errors()
    }

    fun convert() {
        val (valid, errs) = form.validate()
        errors = errs
        if (valid == null || uris.isEmpty()) return
        val options = form.toOptions(valid)
        val list = uris
        work(UiText.Res(R.string.working_progress, listOf(0, list.size))) {
            val done = mutableListOf<File>()
            var fails = 0
            var inBytes = 0L
            list.forEachIndexed { i, uri ->
                progress(UiText.Res(R.string.working_progress, listOf(i + 1, list.size)))
                try {
                    val src = images.importToCache(uri)
                    inBytes += src.bytes
                    val result = withContext(Dispatchers.Default) { Shrinker.run(src.file, options) { } }
                    val out = store.newFile("${src.stem}_${result.width}x${result.height}", if (result.webp) "webp" else "jpg")
                    withContext(Dispatchers.IO) { out.writeBytes(result.data) }
                    done += out
                } catch (e: Shrinker.ShrinkException) {
                    fails++
                } catch (e: ImageOpException) {
                    fails++
                } catch (e: OutOfMemoryError) {
                    fails++
                }
            }
            outputs = done
            failed = fails
            bytesIn = inBytes
        }
    }
}

@Composable
fun BatchScreen(onBack: () -> Unit, vm: BatchViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { vm.onPicked(it) }
    val form = vm.form

    ToolScaffold(stringResource(R.string.tool_batch), onBack, snackbar, "screen_batch") {
        OutlinedButton(
            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.uris.isEmpty()) stringResource(R.string.action_choose_photos) else stringResource(R.string.selected_count, vm.uris.size))
        }

        SectionCard(title = stringResource(R.string.resize_size_header), footer = stringResource(R.string.batch_footer)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                NumField(form.width, stringResource(R.string.resize_width), vm.errors.width, !vm.busy, Modifier.weight(1f)) { vm.onFormChange(form.copy(width = it)) }
                NumField(form.height, stringResource(R.string.resize_height), vm.errors.height, !vm.busy, Modifier.weight(1f)) { vm.onFormChange(form.copy(height = it)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            NumField(form.maxKb, stringResource(R.string.resize_max_kb), vm.errors.maxKb, !vm.busy, Modifier.fillMaxWidth().padding(16.dp), decimal = true) {
                vm.onFormChange(form.copy(maxKb = it))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.padding(16.dp)) {
                ChoiceChips(listOf(false, true), form.webp, { if (it) "WEBP" else "JPG" }, { vm.onFormChange(form.copy(webp = it)) }, enabled = !vm.busy)
            }
        }

        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }

        MainButton(
            text = stringResource(R.string.batch_convert, vm.uris.size),
            onClick = vm::convert,
            enabled = vm.uris.isNotEmpty(),
            busyText = vm.working?.asString(),
            icon = Icons.Outlined.Collections,
        )

        if (vm.outputs.isNotEmpty() || vm.failed > 0) {
            val outBytes = vm.outputs.sumOf { it.length() }
            SectionCard(title = stringResource(R.string.resize_result)) {
                Text(
                    stringResource(R.string.result_summary, vm.outputs.size, humanSize(vm.bytesIn), humanSize(outBytes)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
                if (vm.failed > 0) {
                    Text(
                        stringResource(R.string.result_failed, vm.failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
            if (vm.outputs.isNotEmpty()) {
                SaveShareRow(onSave = { export.saveAll(vm.outputs) }, onShare = { export.share(vm.outputs) }, enabled = !vm.busy)
            }
        }
    }
}

@Composable
internal fun NumField(
    value: String,
    label: String,
    error: UiText?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { t -> onChange(t.filter { it.isDigit() || (decimal && it == '.') }.take(9)) },
        label = { Text(label) },
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e.asString()) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}
