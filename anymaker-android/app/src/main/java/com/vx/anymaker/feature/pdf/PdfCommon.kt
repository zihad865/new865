package com.vx.anymaker.feature.pdf

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vx.anymaker.R

/** One item in an orderable list (pages or files) with up, down and remove buttons. */
@Composable
internal fun PageRow(label: String, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onUp, enabled = enabled) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.action_move_up)) }
        IconButton(onClick = onDown, enabled = enabled) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.action_move_down)) }
        IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Outlined.Close, stringResource(R.string.action_remove)) }
    }
}
