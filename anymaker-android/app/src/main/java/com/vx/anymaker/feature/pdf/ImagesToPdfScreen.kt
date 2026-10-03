package com.vx.anymaker.feature.pdf

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.core.pdf.PageSize
import com.vx.anymaker.core.pdf.PdfTools
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.InfoRow
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

class ImagesToPdfViewModel(app: Application) : ToolViewModel(app) {
    var uris by mutableStateOf<List<Uri>>(emptyList())
        private set
    var pageSize by mutableStateOf(PageSize.A4)
    var margin by mutableStateOf(true)
    var output by mutableStateOf<File?>(null)
        private set

    fun add(list: List<Uri>) {
        uris = uris + list
        output = null
    }

    fun move(index: Int, delta: Int) {
        val to = index + delta
        if (to !in uris.indices) return
        uris = uris.toMutableList().apply { add(to, removeAt(index)) }
        output = null
    }

    fun remove(index: Int) {
        uris = uris.toMutableList().apply { removeAt(index) }
        output = null
    }

    fun create() {
        val list = uris
        val size = pageSize
        val m = if (margin) 24f else 0f
        work {
            output = withContext(Dispatchers.Default) {
                val out = store.newFile("photos", "pdf")
                PdfTools(getApplication()).imagesToPdf(
                    images = list.map { uri -> { Bitmaps.decode(getApplication(), uri, 2200) } },
                    out = out,
                    size = size,
                    marginPt = m,
                    onPage = { p -> progressFrom(p, list.size) },
                )
                out
            }
        }
    }

    private fun progressFrom(page: Int, total: Int) {
        // Called on a background thread; snapshot state is safe to write from any thread.
        progress(UiText.Res(R.string.working_progress, listOf(page, total)))
    }
}

@Composable
fun ImagesToPdfScreen(onBack: () -> Unit, vm: ImagesToPdfViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { vm.add(it) }
    val launchPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolScaffold(stringResource(R.string.tool_images_to_pdf), onBack, snackbar, "screen_images_to_pdf") {
        if (vm.uris.isEmpty()) {
            PickPrompt(stringResource(R.string.action_choose_photos), Icons.Outlined.AddPhotoAlternate, launchPick, enabled = !vm.busy)
        } else {
            SectionCard(title = stringResource(R.string.selected_count, vm.uris.size)) {
                vm.uris.forEachIndexed { i, uri ->
                    PageRow(
                        label = stringResource(R.string.pdf_pages_label, i + 1) + " · " + (uri.lastPathSegment ?: ""),
                        onUp = { vm.move(i, -1) }, onDown = { vm.move(i, 1) }, onRemove = { vm.remove(i) }, enabled = !vm.busy,
                    )
                }
            }
            TextButton(onClick = launchPick, enabled = !vm.busy) { Text(stringResource(R.string.action_choose_photos)) }
        }
        SectionCard(title = stringResource(R.string.pdf_page_size)) {
            Column(Modifier.padding(16.dp)) {
                ChoiceChips(
                    PageSize.entries, vm.pageSize,
                    { stringResource(when (it) { PageSize.A4 -> R.string.pdf_size_a4; PageSize.LETTER -> R.string.pdf_size_letter; PageSize.FIT -> R.string.pdf_size_fit }) },
                    { vm.pageSize = it }, !vm.busy,
                )
                if (vm.pageSize != PageSize.FIT) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(R.string.pdf_margin), modifier = Modifier.weight(1f))
                        Switch(vm.margin, { vm.margin = it }, enabled = !vm.busy)
                    }
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(stringResource(R.string.pdf_create), vm::create, enabled = vm.uris.isNotEmpty(), busyText = vm.working?.asString(), icon = Icons.Outlined.PictureAsPdf)
        vm.output?.let { out ->
            SectionCard { InfoRow(out.name, humanSize(out.length())) }
            SaveShareRow({ export.save(out) }, { export.share(out) }, !vm.busy)
        }
    }
}
