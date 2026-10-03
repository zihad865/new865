package com.vx.anymaker.feature.qr

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.ml.MlKit
import com.vx.anymaker.feature.common.PickPrompt
import com.vx.anymaker.feature.common.ToolViewModel
import com.vx.anymaker.ui.components.InfoRow
import com.vx.anymaker.ui.components.MainButton
import com.vx.anymaker.ui.components.SectionCard
import com.vx.anymaker.ui.components.ToolScaffold
import com.vx.anymaker.ui.components.rememberExportController
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.text.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** A decoded code reduced to what the screen shows. */
data class ScanResult(
    val kind: Kind,
    val raw: String,
    val url: String? = null,
    val ssid: String? = null,
    val password: String? = null,
    val security: String? = null,
    val phone: String? = null,
    val email: String? = null,
) {
    enum class Kind { LINK, WIFI, PHONE, EMAIL, TEXT }

    companion object {
        fun from(b: Barcode): ScanResult {
            val raw = b.rawValue ?: b.displayValue ?: ""
            return when (b.valueType) {
                Barcode.TYPE_URL -> ScanResult(Kind.LINK, raw, url = b.url?.url ?: raw)
                Barcode.TYPE_WIFI -> ScanResult(
                    Kind.WIFI, raw, ssid = b.wifi?.ssid, password = b.wifi?.password,
                    security = when (b.wifi?.encryptionType) {
                        Barcode.WiFi.TYPE_WPA -> "WPA"
                        Barcode.WiFi.TYPE_WEP -> "WEP"
                        else -> "Open"
                    },
                )
                Barcode.TYPE_PHONE -> ScanResult(Kind.PHONE, raw, phone = b.phone?.number ?: raw)
                Barcode.TYPE_EMAIL -> ScanResult(Kind.EMAIL, raw, email = b.email?.address ?: raw)
                else -> ScanResult(Kind.TEXT, raw)
            }
        }
    }
}

class QrScanViewModel(app: Application) : ToolViewModel(app) {
    var result by mutableStateOf<ScanResult?>(null)
        private set

    fun onScanned(b: Barcode) {
        result = ScanResult.from(b)
        clearError()
    }

    fun scanPhoto(uri: Uri) = work {
        val bmp = withContext(Dispatchers.IO) { Bitmaps.decode(getApplication(), uri, 2048) }
        val found = MlKit.scanBarcodes(bmp).firstOrNull()
        bmp.recycle()
        if (found == null) {
            result = null
            showError(UiText.Res(R.string.qr_none))
        } else {
            result = ScanResult.from(found)
        }
    }

    fun scanCamera(context: Context) = work {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build()
        val b = try {
            GmsBarcodeScanning.getClient(context, options).startScan().await()
        } catch (e: Exception) {
            // Cancelled by the user, or Play services missing: nothing to show.
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        if (b != null) onScanned(b)
    }
}

@Composable
fun QrScanScreen(onBack: () -> Unit, vm: QrScanViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.action_copied)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::scanPhoto) }
    val copy: (String) -> Unit = { text ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("qr", text))
        scope.launch { snackbar.showSnackbar(copied) }
    }
    val open: (Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            vm.showError(UiText.Res(R.string.error_no_picker))
        }
    }

    ToolScaffold(stringResource(R.string.tool_qr_scan), onBack, snackbar, "screen_qr_scan") {
        PickPrompt(stringResource(R.string.qr_scan_camera), Icons.Outlined.QrCodeScanner, { vm.scanCamera(context) }, enabled = !vm.busy)
        FilledTonalButton(
            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !vm.busy,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.qr_scan_photo)) }
        vm.working?.let { MainButton("", {}, busyText = it.asString()) }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        vm.result?.let { r ->
            val title = stringResource(
                when (r.kind) {
                    ScanResult.Kind.LINK -> R.string.qr_type_link
                    ScanResult.Kind.WIFI -> R.string.qr_type_wifi
                    ScanResult.Kind.PHONE -> R.string.qr_type_phone
                    ScanResult.Kind.EMAIL -> R.string.qr_type_email
                    ScanResult.Kind.TEXT -> R.string.qr_type_text
                },
            )
            SectionCard(title = title, footer = if (r.kind == ScanResult.Kind.LINK) stringResource(R.string.qr_link_check) else null) {
                Column(Modifier.testTag("qr_result")) {
                    when (r.kind) {
                        ScanResult.Kind.WIFI -> {
                            InfoRow(stringResource(R.string.qr_network), r.ssid.orEmpty())
                            InfoRow(stringResource(R.string.qr_password), r.password.orEmpty())
                            InfoRow(stringResource(R.string.qr_security), r.security.orEmpty())
                        }
                        else -> SelectionContainer {
                            Text(r.url ?: r.phone ?: r.email ?: r.raw, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (r.kind) {
                    ScanResult.Kind.LINK -> FilledTonalButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) }) {
                        Text(stringResource(R.string.qr_open_link))
                    }
                    ScanResult.Kind.PHONE -> FilledTonalButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.phone}"))) }) {
                        Text(stringResource(R.string.qr_call))
                    }
                    ScanResult.Kind.EMAIL -> FilledTonalButton(onClick = { open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${r.email}"))) }) {
                        Text(stringResource(R.string.qr_type_email))
                    }
                    else -> Unit
                }
                OutlinedButton(onClick = { copy(if (r.kind == ScanResult.Kind.WIFI) r.password.orEmpty() else r.url ?: r.raw) }) {
                    Text(stringResource(R.string.action_copy))
                }
                OutlinedButton(onClick = { export.shareText(r.raw) }) { Text(stringResource(R.string.resize_share)) }
            }
        }
    }
}
