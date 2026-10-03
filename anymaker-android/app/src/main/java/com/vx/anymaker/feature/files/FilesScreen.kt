package com.vx.anymaker.feature.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vx.anymaker.R
import com.vx.anymaker.core.files.OutputFile
import com.vx.anymaker.core.files.OutputStore
import com.vx.anymaker.core.image.humanSize
import com.vx.anymaker.ui.components.rememberExportController
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Everything the tools made, newest first, with save, share and delete. */
@Composable
fun FilesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { OutputStore(context) }
    val snackbar = remember { SnackbarHostState() }
    val export = rememberExportController(snackbar)
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<OutputFile>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { files = store.list() }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Box(modifier.fillMaxSize()) {
        val list = files
        if (list != null && list.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.files_empty_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.files_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (list != null) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("files_list"),
            ) {
                item {
                    Text(
                        stringResource(R.string.files_count, list.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list, key = { it.file.path }) { f ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            leadingContent = {
                                Icon(
                                    when {
                                        f.mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
                                        f.mime.startsWith("image/") -> Icons.Outlined.Image
                                        else -> Icons.Outlined.Description
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(humanSize(f.bytes) + " · " + dateFormat.format(Date(f.modified))) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { export.save(f.file) }) { Icon(Icons.Outlined.Download, stringResource(R.string.resize_save)) }
                                    IconButton(onClick = { export.share(f.file) }) { Icon(Icons.Outlined.Share, stringResource(R.string.resize_share)) }
                                    IconButton(onClick = { scope.launch { store.delete(f.file); reload++ } }) {
                                        Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                                    }
                                }
                            },
                            modifier = Modifier.clickable { export.share(f.file) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
