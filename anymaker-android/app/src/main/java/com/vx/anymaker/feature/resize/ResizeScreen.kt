package com.vx.anymaker.feature.resize

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vx.anymaker.R
import com.vx.anymaker.core.image.ShrinkOutput
import com.vx.anymaker.core.image.decodePreview
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.ui.text.UiText
import com.vx.anymaker.ui.text.asString
import com.vx.anymaker.ui.theme.AnymakerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ResizeScreen(viewModel: ResizeViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }
    val createJpg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }
    val createWebp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { uri ->
        if (uri != null) viewModel.saveTo(uri)
    }

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }
    val noPicker = stringResource(R.string.error_no_picker)
    val chooserTitle = stringResource(R.string.resize_share_chooser)

    ResizeContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onPick = {
            try {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: ActivityNotFoundException) {
                viewModel.onPickerMissing(noPicker)
            }
        },
        onFormChange = viewModel::onFormChange,
        onLockChange = viewModel::onLockChange,
        onConvert = viewModel::convert,
        onSave = {
            val out = state.output ?: return@ResizeContent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                viewModel.saveToGallery()
            } else {
                try {
                    (if (out.webp) createWebp else createJpg).launch(out.file.name)
                } catch (e: ActivityNotFoundException) {
                    viewModel.onPickerMissing(noPicker)
                }
            }
        },
        onShare = {
            val out = state.output ?: return@ResizeContent
            val uri: Uri = viewModel.shareUri() ?: return@ResizeContent
            val send = Intent(Intent.ACTION_SEND).apply {
                type = out.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(out.file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(send, chooserTitle))
            } catch (e: ActivityNotFoundException) {
                viewModel.onPickerMissing(noPicker)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResizeContent(
    state: ResizeUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onFormChange: (ResizeForm) -> Unit,
    onLockChange: (Boolean) -> Unit,
    onConvert: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val form = state.form
    val editable = !form.locked && !state.busy

    Scaffold(
        modifier = Modifier.testTag("resize_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.resize_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoCard(state = state, enabled = !state.busy, onPick = onPick)

            Group(title = stringResource(R.string.resize_size_header), footer = stringResource(R.string.resize_size_footer)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    NumberField(
                        value = form.width,
                        label = stringResource(R.string.resize_width),
                        error = state.errors.width,
                        enabled = editable,
                        onChange = { onFormChange(form.copy(width = it)) },
                        modifier = Modifier.weight(1f).testTag("width_field"),
                    )
                    NumberField(
                        value = form.height,
                        label = stringResource(R.string.resize_height),
                        error = state.errors.height,
                        enabled = editable,
                        onChange = { onFormChange(form.copy(height = it)) },
                        modifier = Modifier.weight(1f).testTag("height_field"),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SwitchRow(
                    label = stringResource(R.string.resize_keep_aspect),
                    checked = form.keepAspect,
                    enabled = editable && form.aspectApplies,
                    onChange = { onFormChange(form.copy(keepAspect = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                SwitchRow(
                    label = stringResource(R.string.resize_never_reduce),
                    checked = form.neverReducePixels,
                    enabled = editable,
                    onChange = { onFormChange(form.copy(neverReducePixels = it)) },
                )
            }

            Group(title = stringResource(R.string.resize_file_header)) {
                NumberField(
                    value = form.maxKb,
                    label = stringResource(R.string.resize_max_kb),
                    error = state.errors.maxKb,
                    enabled = editable,
                    decimal = true,
                    onChange = { onFormChange(form.copy(maxKb = it)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("kb_field"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.resize_format), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.width(180.dp).alpha(if (editable) 1f else 0.5f)) {
                        SegmentedButton(
                            selected = !form.webp,
                            onClick = { onFormChange(form.copy(webp = false)) },
                            enabled = editable,
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stringResource(R.string.resize_jpg)) }
                        SegmentedButton(
                            selected = form.webp,
                            onClick = { onFormChange(form.copy(webp = true)) },
                            enabled = editable,
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stringResource(R.string.resize_webp)) }
                    }
                }
            }

            Group(
                footer = stringResource(if (form.locked) R.string.resize_lock_on_footer else R.string.resize_lock_off_footer),
            ) {
                SwitchRow(
                    label = stringResource(R.string.resize_lock),
                    checked = form.locked,
                    enabled = !state.busy,
                    leading = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onChange = onLockChange,
                    modifier = Modifier.testTag("lock_switch"),
                )
            }

            state.error?.let { err ->
                Text(
                    text = err.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("error_text"),
                )
            }

            Button(
                onClick = onConvert,
                enabled = state.canConvert,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("convert_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(state.working?.asString().orEmpty())
                } else {
                    Text(stringResource(if (form.locked && state.output != null) R.string.resize_convert_again else R.string.resize_convert))
                }
            }

            state.output?.let { out ->
                ResultCard(output = out, enabled = !state.busy, onSave = onSave, onShare = onShare)
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
    }
}

@Composable
private fun PhotoCard(state: ResizeUiState, enabled: Boolean, onPick: () -> Unit) {
    val file: File? = state.output?.file ?: state.image?.file
    val preview by produceState<Bitmap?>(initialValue = null, file) {
        value = file?.let { f -> withContext(Dispatchers.IO) { decodePreview(f) } }
    }
    val chooseLabel = stringResource(if (state.image == null) R.string.resize_choose else R.string.resize_change)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = chooseLabel, onClick = onPick)
                    .testTag("photo_card"),
            ) {
                val bmp = preview
                if (bmp != null) {
                    val desc = stringResource(R.string.resize_preview)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = desc,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = if (file != null) stringResource(R.string.resize_preview_unavailable) else chooseLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            state.image?.let { img ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "${stringResource(R.string.resize_original)}  ${img.width} × ${img.height} · ${humanSize(img.bytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onPick, enabled = enabled) { Text(stringResource(R.string.resize_change)) }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(output: ShrinkOutput, enabled: Boolean, onSave: () -> Unit, onShare: () -> Unit) {
    val tools = AnymakerColors.tools
    val (grade, gradeColor) = when {
        output.quality >= 80 -> stringResource(R.string.grade_excellent) to tools.good
        output.quality >= 60 -> stringResource(R.string.grade_good) to MaterialTheme.colorScheme.onSurface
        else -> stringResource(R.string.grade_low) to tools.warn
    }
    val tips = buildList {
        if (output.downscaled) add(stringResource(R.string.resize_tip_downscaled, output.requestedWidth, output.requestedHeight))
        if (output.quality < 60) add(stringResource(if (output.webp) R.string.resize_tip_low_webp else R.string.resize_tip_low_jpg))
    }
    Group(title = stringResource(R.string.resize_result), footer = tips.joinToString(" ").ifEmpty { null }) {
        Column(modifier = Modifier.testTag("result_card")) {
            ValueRow(stringResource(R.string.resize_dimensions), "${output.width} × ${output.height}")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ValueRow(
                stringResource(R.string.resize_file_size),
                "${humanSize(output.bytes)} · ${if (output.webp) "WEBP" else "JPG"}",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ValueRow(stringResource(R.string.resize_quality), stringResource(R.string.resize_quality_value, output.quality, grade), gradeColor)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                FilledTonalButton(
                    onClick = onSave,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_button"),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.resize_save))
                }
                FilledTonalButton(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("share_button"),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.resize_share))
                }
            }
        }
    }
}

@Composable
private fun Group(title: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column { content() }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    error: UiText?,
    enabled: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || (decimal && it == '.') }.take(9)) },
        label = { Text(label) },
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { e -> { Text(e.asString()) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp),
    ) {
        leading?.invoke()
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ValueRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}
