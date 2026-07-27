package com.lunentous.app.ui.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lunentous.app.ui.icons.iconFor
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Shared list UI for reminder_types/phase_types, mirroring
 * web/src/components/TypeManager.tsx's parameterization. */
@Composable
fun TypeManagerScreen(
    title: String,
    noun: String,
    hasIcon: Boolean,
    rows: List<TypeRow>,
    showArchived: Boolean,
    onShowArchivedChange: (Boolean) -> Unit,
    isSaving: Boolean,
    error: String?,
    onSave: (existingLocalId: Long?, name: String, icon: String?, color: String, onDone: () -> Unit) -> Unit,
    onToggleArchive: (TypeRow) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    var formTarget by remember { mutableStateOf<TypeFormTarget?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { formTarget = TypeFormTarget.Create },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add ${noun.lowercase()}") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            ) {
                Checkbox(checked = showArchived, onCheckedChange = onShowArchivedChange)
                Text("Show archived", modifier = Modifier.padding(start = 4.dp))
            }

            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing here yet.", color = colors.textMuted)
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.localId }) { row ->
                        TypeRowCard(
                            row = row,
                            hasIcon = hasIcon,
                            onClick = { formTarget = TypeFormTarget.Edit(row) },
                            onToggleArchive = { onToggleArchive(row) },
                        )
                    }
                }
            }
        }
    }

    formTarget?.let { target ->
        val existing = (target as? TypeFormTarget.Edit)?.row
        TypeFormSheet(
            noun = noun,
            hasIcon = hasIcon,
            existing = existing,
            isSaving = isSaving,
            error = error,
            onDismiss = { formTarget = null },
            onSave = { name, icon, color ->
                onSave(existing?.localId, name, icon, color) { formTarget = null }
            },
        )
    }
}

private sealed interface TypeFormTarget {
    data object Create : TypeFormTarget
    data class Edit(val row: TypeRow) : TypeFormTarget
}

@Composable
private fun TypeRowCard(row: TypeRow, hasIcon: Boolean, onClick: () -> Unit, onToggleArchive: () -> Unit) {
    val colors = LunentousExtendedTheme.colors
    val typeColor = row.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent

    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(typeColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (hasIcon) {
                    Icon(iconFor(row.icon), contentDescription = null, tint = typeColor, modifier = Modifier.size(18.dp))
                } else {
                    Box(Modifier.size(10.dp).background(typeColor, CircleShape))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Used by ${row.usageCount} plant${if (row.usageCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            IconButton(onClick = onToggleArchive) {
                if (row.archived) {
                    Icon(Icons.Filled.Unarchive, contentDescription = "Unarchive ${row.name}", tint = colors.textMuted)
                } else {
                    Icon(Icons.Filled.Archive, contentDescription = "Archive ${row.name}", tint = colors.textMuted)
                }
            }
        }
    }
}
