package com.vx.anymaker.feature.ocr

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.ml.MlKit
import com.vx.anymaker.core.ml.TextScript
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.ChoiceChips
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OcrViewModel(app: Application) : ToolViewModel(app) {
    var script by mutableStateOf(TextScript.LATIN)
    var text by mutableStateOf("")
    var hasResult by mutableStateOf(false)
        private set
    private var lastUri: Uri? = null

    fun recognize(uri: Uri) {
        lastUri = uri
        val s = script
        work {
            val bmp = withContext(Dispatchers.IO) { Bitmaps.decode(getApplication(), uri, 3000) }
            val found = MlKit.recognizeText(bmp, s)
            bmp.recycle()
            text = found
            hasResult = true
            if (found.isBlank()) showError(UiText.Res(R.string.ocr_empty))
        }
    }

    fun setScriptAndRetry(s: TextScript) {
        script = s
        lastUri?.let { recognize(it) }
    }

    suspend fun saveTxt(): File = withContext(Dispatchers.IO) {
        store.newFile("text", "txt").also { it.writeText(text) }
    }
}

/** Where the camera writes a photo for recognition; shared through the FileProvider. */
private fun captureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "capture").apply { mkdirs() }
    val file = File(dir, "ocr.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

@Composable
fun OcrScreen(onBack: () -> Unit, vm: OcrViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.action_copied)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::recognize) }
    val shot = remember { captureUri(context) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.recognize(shot) }

    ToolScaffold(stringResource(R.string.tool_ocr), onBack, snackbar, "screen_ocr") {
        SectionCard(title = stringResource(R.string.ocr_script)) {
            Column(Modifier.padding(16.dp)) {
                ChoiceChips(
                    TextScript.entries, vm.script,
                    {
                        stringResource(
                            when (it) {
                                TextScript.LATIN -> R.string.ocr_latin
                                TextScript.DEVANAGARI -> R.string.ocr_devanagari
                                TextScript.CHINESE -> R.string.ocr_chinese
                                TextScript.JAPANESE -> R.string.ocr_japanese
                                TextScript.KOREAN -> R.string.ocr_korean
                            },
                        )
                    },
                    vm::setScriptAndRetry, !vm.busy,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !vm.busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.action_choose_photo)) }
            FilledTonalButton(
                onClick = { runCatching { camera.launch(shot) }.onFailure { vm.showError(UiText.Res(R.string.error_no_picker)) } },
                enabled = !vm.busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.action_take_photo)) }
        }
        vm.working?.let { MainButton("", {}, busyText = it.asString()) }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        if (vm.hasResult && vm.text.isNotBlank()) {
            OutlinedTextField(
                value = vm.text,
                onValueChange = { vm.text = it },
                label = { Text(stringResource(R.string.ocr_result)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).testTag("ocr_text"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("text", vm.text))
                    scope.launch { snackbar.showSnackbar(copied) }
                }) { Text(stringResource(R.string.action_copy)) }
                OutlinedButton(onClick = { export.shareText(vm.text) }) { Text(stringResource(R.string.resize_share)) }
                OutlinedButton(onClick = { scope.launch { export.save(vm.saveTxt()) } }) { Text(stringResource(R.string.ocr_save_txt)) }
            }
        }
    }
}
