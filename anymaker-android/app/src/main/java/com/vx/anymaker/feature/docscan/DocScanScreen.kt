package com.vx.anymaker.feature.docscan

import android.app.Activity
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.vx.anymaker.R
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.InfoRow
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SaveShareRow
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.text.asString
import java.io.File

class DocScanViewModel(app: Application) : ToolViewModel(app) {
    var pdf by mutableStateOf<File?>(null)
        private set
    var pages by mutableStateOf<List<File>>(emptyList())
        private set

    fun onResult(result: GmsDocumentScanningResult?) {
        if (result == null) return
        work {
            pdf = result.pdf?.uri?.let { store.importUri(it, "scan", "pdf") }
            pages = result.pages.orEmpty().mapIndexed { i, p -> store.importUri(p.imageUri, "scan_page_${i + 1}", "jpg") }
        }
    }

    fun onUnavailable() = showError(UiText.Res(R.string.docscan_unavailable))
}

@Composable
fun DocScanScreen(onBack: () -> Unit, vm: DocScanViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val activity = LocalContext.current as? Activity
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(100)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.onResult(GmsDocumentScanningResult.fromActivityResultIntent(r.data))
    }
    val start = {
        if (activity == null) {
            vm.onUnavailable()
        } else {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { vm.onUnavailable() }
        }
        Unit
    }

    ToolScaffold(stringResource(R.string.tool_doc_scan), onBack, snackbar, "screen_doc_scan") {
        PickPrompt(stringResource(R.string.docscan_start), Icons.Outlined.DocumentScanner, start, enabled = !vm.busy, body = stringResource(R.string.docscan_body))
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        vm.working?.let { MainButton("", {}, busyText = it.asString()) }
        vm.pdf?.let { pdf ->
            SectionCard(title = stringResource(R.string.docscan_pdf)) {
                InfoRow(pdf.name, stringResource(R.string.docscan_pages, vm.pages.size) + " · " + humanSize(pdf.length()))
            }
            SaveShareRow({ export.save(pdf) }, { export.share(pdf) }, !vm.busy)
        }
        if (vm.pages.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.docscan_images)) {
                InfoRow(stringResource(R.string.files_count, vm.pages.size), humanSize(vm.pages.sumOf { it.length() }))
            }
            SaveShareRow({ export.saveAll(vm.pages) }, { export.share(vm.pages) }, !vm.busy)
        }
    }
}
