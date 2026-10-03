package com.vx.anymaker.feature.qr

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vx.anymaker.R
import com.vx.anymaker.core.image.Bitmaps
import com.vx.anymaker.core.image.ImageFormat
import com.vx.anymaker.core.qr.QrCodes
import com.vx.anymaker.core.qr.QrContent
import com.vx.anymaker.feature.common.BitmapPanel
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

enum class QrKind(val label: Int) { TEXT(R.string.qr_type_text), WIFI(R.string.qr_type_wifi), PHONE(R.string.qr_type_phone), EMAIL(R.string.qr_type_email) }

class QrCreateViewModel(app: Application) : ToolViewModel(app) {
    var kind by mutableStateOf(QrKind.TEXT)
    var text by mutableStateOf("")
    var ssid by mutableStateOf("")
    var password by mutableStateOf("")
    var security by mutableStateOf(QrContent.Wifi.Security.WPA)
    var hidden by mutableStateOf(false)
    var phone by mutableStateOf("")
    var email by mutableStateOf("")
    var subject by mutableStateOf("")
    var body by mutableStateOf("")
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var output by mutableStateOf<File?>(null)
        private set

    fun content(): QrContent = when (kind) {
        QrKind.TEXT -> QrContent.Text(text)
        QrKind.WIFI -> QrContent.Wifi(ssid, password, security, hidden)
        QrKind.PHONE -> QrContent.Phone(phone)
        QrKind.EMAIL -> QrContent.Email(email, subject, body)
    }

    fun generate() {
        val payload = content().payload()
        work {
            val bmp = withContext(Dispatchers.Default) { QrCodes.render(payload) }
            preview = bmp
            output = withContext(Dispatchers.IO) { store.newFile("qr_code", "png").also { Bitmaps.write(bmp, it, ImageFormat.PNG) } }
        }
    }
}

@Composable
fun QrCreateScreen(onBack: () -> Unit, vm: QrCreateViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)

    ToolScaffold(stringResource(R.string.tool_qr_create), onBack, snackbar, "screen_qr_create") {
        SectionCard(title = stringResource(R.string.qr_content)) {
            Column(Modifier.padding(16.dp)) {
                ChoiceChips(QrKind.entries, vm.kind, { stringResource(it.label) }, { vm.kind = it }, !vm.busy)
                val field = Modifier.fillMaxWidth().padding(top = 8.dp)
                when (vm.kind) {
                    QrKind.TEXT -> OutlinedTextField(vm.text, { vm.text = it }, field, label = { Text(stringResource(R.string.qr_text_hint)) }, minLines = 2)
                    QrKind.WIFI -> {
                        OutlinedTextField(vm.ssid, { vm.ssid = it }, field, label = { Text(stringResource(R.string.qr_ssid)) }, singleLine = true)
                        ChoiceChips(QrContent.Wifi.Security.entries, vm.security, { it.name }, { vm.security = it }, !vm.busy)
                        if (vm.security != QrContent.Wifi.Security.NONE) {
                            OutlinedTextField(vm.password, { vm.password = it }, field, label = { Text(stringResource(R.string.qr_password)) }, singleLine = true)
                        }
                        androidx.compose.foundation.layout.Row(Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.qr_hidden), modifier = Modifier.weight(1f).padding(top = 12.dp))
                            Switch(vm.hidden, { vm.hidden = it })
                        }
                    }
                    QrKind.PHONE -> OutlinedTextField(
                        vm.phone, { vm.phone = it }, field, label = { Text(stringResource(R.string.qr_phone)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    QrKind.EMAIL -> {
                        OutlinedTextField(
                            vm.email, { vm.email = it }, field, label = { Text(stringResource(R.string.qr_email)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        OutlinedTextField(vm.subject, { vm.subject = it }, field, label = { Text(stringResource(R.string.qr_subject)) }, singleLine = true)
                        OutlinedTextField(vm.body, { vm.body = it }, field, label = { Text(stringResource(R.string.qr_message)) }, minLines = 2)
                    }
                }
            }
        }
        vm.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
        MainButton(stringResource(R.string.qr_preview), vm::generate, busyText = vm.working?.asString(), icon = Icons.Outlined.QrCode)
        vm.preview?.let { BitmapPanel(it, Modifier.widthIn(max = 360.dp)) }
        vm.output?.let { out -> SaveShareRow({ export.save(out) }, { export.share(out) }, !vm.busy) }
    }
}
