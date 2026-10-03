package com.vx.anymaker.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vx.anymaker.R

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column {
                val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    trailingContent = { Text(version, style = MaterialTheme.typography.bodyMedium) },
                    colors = colors,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_body)) },
                    colors = colors,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showLicenses = true },
                )
            }
        }
    }

    if (showLicenses) {
        val text = remember {
            runCatching {
                context.assets.open("licenses/PlusJakartaSans-OFL.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.settings_close)) } },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Plus Jakarta Sans", style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}
