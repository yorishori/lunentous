package com.lunentous.app.ui.calendar

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.lunentous.app.data.local.entity.PlantEntity

/** Compose equivalent of web/src/components/MultiSelect.tsx, used here for
 * Calendar's plant filter. */
@Composable
fun PlantMultiSelect(plants: List<PlantEntity>, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val label = when {
        selected.isEmpty() -> "All plants"
        selected.size == 1 -> plants.find { it.localId == selected.first() }?.name ?: "All plants"
        else -> "${selected.size} selected"
    }

    OutlinedButton(onClick = { expanded = true }) {
        Text(label)
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = androidx.compose.ui.Modifier.padding(start = 4.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (plants.isEmpty()) {
            DropdownMenuItem(text = { Text("No plants") }, onClick = {}, enabled = false)
        }
        plants.forEach { plant ->
            DropdownMenuItem(
                text = { Text(plant.name) },
                leadingIcon = {
                    Checkbox(
                        checked = plant.localId in selected,
                        onCheckedChange = { checked ->
                            onChange(if (checked) selected + plant.localId else selected - plant.localId)
                        },
                    )
                },
                onClick = {
                    val checked = plant.localId !in selected
                    onChange(if (checked) selected + plant.localId else selected - plant.localId)
                },
            )
        }
    }
}
