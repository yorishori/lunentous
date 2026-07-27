package com.lunentous.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lunentous.app.ui.icons.ICON_NAMES
import com.lunentous.app.ui.icons.iconFor
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Compose equivalent of web/src/components/IconPicker.tsx -- a trigger
 * button that opens a searchable grid over the curated icon set. Uses a
 * plain Dialog rather than a second ModalBottomSheet since this is always
 * embedded inside a type form's own bottom sheet. */
@Composable
fun IconPicker(value: String?, onChange: (String) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    val colors = LunentousExtendedTheme.colors

    OutlinedButton(onClick = { dialogOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(iconFor(value), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(value ?: "Choose an icon", modifier = Modifier.padding(start = 8.dp))
    }

    if (dialogOpen) {
        var search by remember { mutableStateOf("") }
        val filtered = ICON_NAMES.filter { it.contains(search, ignoreCase = true) }

        Dialog(onDismissRequest = { dialogOpen = false }) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search icons") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                    if (filtered.isEmpty()) {
                        Text("No icons found", color = colors.textMuted, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().height(280.dp),
                        ) {
                            items(filtered) { name ->
                                val selected = name == value
                                IconButton(
                                    onClick = { onChange(name); dialogOpen = false },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        iconFor(name),
                                        contentDescription = name,
                                        tint = if (selected) colors.accent else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
