package com.vx.anymaker.feature.pdf

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.core.pdf.PdfCompression
import com.vx.anymaker.core.pdf.PdfTools
import com.vx.anymaker.core.pdf.parsePageRanges
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

private const val PDF = "application/pdf"

/** Shared state for tools that work on one chosen PDF. */
abstract class SinglePdfViewModel(app: Application) : ToolViewModel(app) {
    var input by mutableStateOf<File?>(null)
        protected set
    var pages by mutableStateOf(0)
        protected set
    var encrypted by mutableStateOf(false)
        protected set
    var outputs by mutableStateOf<List<File>>(emptyList())
        protected set
    protected val pdf by lazy { PdfTools(getApplication()) }

    fun open(uri: Uri) = work {
        val f = store.importInput(uri, "document.pdf")
        withContext(Dispatchers.IO) {
            encrypted = pdf.isEncrypted(f)
            pages = if (encrypted) 0 else pdf.pageCount(f)
        }
        input = f
        outputs = emptyList()
    }
}

@Composable
private fun PdfPicker(vm: SinglePdfViewModel, onPick: () -> Unit) {
    val file = vm.input
    if (file == null) {
        PickPrompt(stringResource(R.string.action_choose_pdf), Icons.Outlined.PictureAsPdf, onPick, enabled = !vm.busy)
    } else {
        SectionCard {
            InfoRow(file.name, if (vm.encrypted) "🔒" else stringResource(R.string.pdf_info, humanSize(file.length()), vm.pages))
        }
        TextButton(onClick = onPick, enabled = !vm.busy) { Text(stringResource(R.string.resize_change)) }
    }
}

@Composable
private fun Outputs(vm: SinglePdfViewModel, snackbar: SnackbarHostState) {
    val export = rememberExportController(snackbar)
    val outs = vm.outputs
    if (outs.isEmpty()) return
    SectionCard(title = stringResource(R.string.resize_result)) {
        if (outs.size == 1) {
            val before = vm.input?.length()
            InfoRow(outs[0].name, if (before != null) stringResource(R.string.size_before_after, humanSize(before), humanSize(outs[0].length())) else humanSize(outs[0].length()))
        } else {
            InfoRow(stringResource(R.string.files_count, outs.size), humanSize(outs.sumOf { it.length() }))
        }
    }
    SaveShareRow({ if (outs.size == 1) export.save(outs[0]) else export.saveAll(outs) }, { export.share(outs) }, !vm.busy)
}

// ---------------------------------------------------------------- Merge

class MergeViewModel(app: Application) : ToolViewModel(app) {
    var files by mutableStateOf<List<File>>(emptyList())
        private set
    var output by mutableStateOf<File?>(null)
        private set

    fun add(uris: List<Uri>) = work {
        files = files + uris.map { store.importInput(it, "document.pdf") }
        output = null
    }

    fun move(i: Int, d: Int) {
        val to = i + d
        if (to !in files.indices) return
        files = files.toMutableList().apply { add(to, removeAt(i)) }
    }

    fun remove(i: Int) {
        files = files.toMutableList().apply { removeAt(i) }
    }

    fun merge() {
        val list = files
        work {
            output = withContext(Dispatchers.IO) {
                store.newFile("merged", "pdf").also { PdfTools(getApplication()).merge(list, it) }
            }
        }
    }
}

@Composable
fun MergePdfScreen(onBack: () -> Unit, vm: MergeViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.add(it) }
    ToolScaffold(stringResource(R.string.tool_pdf_merge), onBack, snackbar, "screen_pdf_merge") {
        if (vm.files.isEmpty()) {
            PickPrompt(stringResource(R.string.action_choose_pdfs), Icons.Outlined.PictureAsPdf, { pick.launch(arrayOf(PDF)) }, enabled = !vm.busy)
        } else {
            SectionCard(title = stringResource(R.string.files_count, vm.files.size)) {
                vm.files.forEachIndexed { i, f -> PageRow("${i + 1}. ${f.name}", { vm.move(i, -1) }, { vm.move(i, 1) }, { vm.remove(i) }, !vm.busy) }
            }
            TextButton(onClick = { pick.launch(arrayOf(PDF)) }, enabled = !vm.busy) { Text(stringResource(R.string.action_choose_pdfs)) }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(stringResource(R.string.pdf_merge_run, vm.files.size), vm::merge, enabled = vm.files.size >= 2, busyText = vm.working?.asString(), icon = Icons.Outlined.Merge)
        vm.output?.let { out ->
            SectionCard { InfoRow(out.name, humanSize(out.length())) }
            SaveShareRow({ export.save(out) }, { export.share(out) }, !vm.busy)
        }
    }
}

// ---------------------------------------------------------------- Split

class SplitViewModel(app: Application) : SinglePdfViewModel(app) {
    var everyPage by mutableStateOf(false)
    var ranges by mutableStateOf("1")

    fun split() {
        val f = input ?: return
        if (encrypted) {
            showError(UiText.Res(R.string.pdf_wrong_password))
            return
        }
        val every = everyPage
        val parsed = if (every) emptyList() else try {
            parsePageRanges(ranges, pages)
        } catch (e: IllegalArgumentException) {
            showError(UiText.Raw(e.message ?: ""))
            return
        }
        work {
            outputs = withContext(Dispatchers.IO) {
                val stem = f.nameWithoutExtension
                if (every) pdf.splitAll(f, { p -> store.newFile("${stem}_page_$p", "pdf") })
                else listOf(store.newFile("${stem}_pages", "pdf").also { pdf.extract(f, parsed, it) })
            }
        }
    }
}

@Composable
fun SplitPdfScreen(onBack: () -> Unit, vm: SplitViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    ToolScaffold(stringResource(R.string.tool_pdf_split), onBack, snackbar, "screen_pdf_split") {
        PdfPicker(vm) { pick.launch(arrayOf(PDF)) }
        if (vm.input != null) {
            SectionCard(title = stringResource(R.string.pdf_split_mode)) {
                Column(Modifier.padding(16.dp)) {
                    ChoiceChips(listOf(false, true), vm.everyPage, { stringResource(if (it) R.string.pdf_split_every else R.string.pdf_split_ranges) }, { vm.everyPage = it }, !vm.busy)
                    if (!vm.everyPage) {
                        OutlinedTextField(
                            vm.ranges, { vm.ranges = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text(stringResource(R.string.pdf_split_pages_hint)) }, singleLine = true,
                        )
                    }
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(stringResource(R.string.pdf_split_run), vm::split, enabled = vm.input != null, busyText = vm.working?.asString(), icon = Icons.Outlined.ContentCut)
        Outputs(vm, snackbar)
    }
}

// ---------------------------------------------------------------- Compress

class CompressViewModel(app: Application) : SinglePdfViewModel(app) {
    var level by mutableStateOf(PdfCompression.MEDIUM)

    fun compress() {
        val f = input ?: return
        if (encrypted) {
            showError(UiText.Res(R.string.pdf_wrong_password))
            return
        }
        val l = level
        work {
            outputs = withContext(Dispatchers.Default) {
                listOf(store.newFile("${f.nameWithoutExtension}_compressed", "pdf").also { pdf.compress(f, it, l) })
            }
        }
    }
}

@Composable
fun CompressPdfScreen(onBack: () -> Unit, vm: CompressViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    ToolScaffold(stringResource(R.string.tool_pdf_compress), onBack, snackbar, "screen_pdf_compress") {
        PdfPicker(vm) { pick.launch(arrayOf(PDF)) }
        SectionCard(title = stringResource(R.string.pdf_compress_level), footer = stringResource(R.string.pdf_compress_footer)) {
            Column(Modifier.padding(16.dp)) {
                ChoiceChips(
                    PdfCompression.entries, vm.level,
                    { stringResource(when (it) { PdfCompression.LOW -> R.string.pdf_compress_low; PdfCompression.MEDIUM -> R.string.pdf_compress_medium; PdfCompression.HIGH -> R.string.pdf_compress_high }) },
                    { vm.level = it }, !vm.busy,
                )
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(stringResource(R.string.pdf_compress_run), vm::compress, enabled = vm.input != null, busyText = vm.working?.asString(), icon = Icons.Outlined.Compress)
        Outputs(vm, snackbar)
    }
}

// ---------------------------------------------------------------- Lock / unlock

class LockViewModel(app: Application) : SinglePdfViewModel(app) {
    var password by mutableStateOf("")
    var confirm by mutableStateOf("")

    fun apply() {
        val f = input ?: return
        val pw = password
        if (!encrypted && pw != confirm) {
            showError(UiText.Res(R.string.pdf_lock_mismatch))
            return
        }
        val unlocking = encrypted
        work {
            outputs = withContext(Dispatchers.IO) {
                val out = store.newFile(f.nameWithoutExtension + if (unlocking) "_unlocked" else "_locked", "pdf")
                try {
                    if (unlocking) pdf.unlock(f, out, pw) else pdf.lock(f, out, pw)
                } catch (e: Exception) {
                    out.delete()
                    throw e
                }
                listOf(out)
            }
            password = ""
            confirm = ""
        }
    }
}

@Composable
fun LockPdfScreen(onBack: () -> Unit, vm: LockViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    ToolScaffold(stringResource(R.string.tool_pdf_lock), onBack, snackbar, "screen_pdf_lock") {
        PdfPicker(vm) { pick.launch(arrayOf(PDF)) }
        if (vm.input != null) {
            SectionCard(
                title = stringResource(if (vm.encrypted) R.string.pdf_locked_status else R.string.pdf_unlocked_status),
                footer = if (vm.encrypted) null else stringResource(R.string.pdf_lock_footer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    val pwOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    OutlinedTextField(
                        vm.password, { vm.password = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.pdf_lock_password)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = pwOptions,
                    )
                    if (!vm.encrypted) {
                        OutlinedTextField(
                            vm.confirm, { vm.confirm = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(R.string.pdf_lock_confirm)) },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = pwOptions,
                        )
                    }
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(
            stringResource(if (vm.encrypted) R.string.pdf_unlock_run else R.string.pdf_lock_run), vm::apply,
            enabled = vm.input != null && vm.password.isNotEmpty(), busyText = vm.working?.asString(),
            icon = if (vm.encrypted) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
        )
        Outputs(vm, snackbar)
    }
}
