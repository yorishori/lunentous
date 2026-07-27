package com.lunentous.app.ui.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunentous.app.data.local.entity.OverridePeriodEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.repository.ReminderRuleWithPeriods
import com.lunentous.app.ui.components.MonthDayPicker
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/**
 * Simplified into a single scrollable sheet rather than web/src/components/
 * ReminderRuleForm.tsx's 3-step wizard -- mobile bottom sheets don't need
 * the step affordance a full page did, per the Android plan's note that
 * these get "adapted to mobile conventions rather than copied pixel-for-pixel."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderRuleFormSheet(
    selectableTypes: List<ReminderTypeEntity>,
    existing: ReminderRuleWithPeriods?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (reminderTypeLocalId: Long, defaultIntervalDays: Int?, periods: List<OverridePeriodEntity>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LunentousExtendedTheme.colors

    var typeExpanded by remember { mutableStateOf(false) }
    var selectedTypeLocalId by remember { mutableStateOf(existing?.rule?.reminderTypeLocalId ?: selectableTypes.firstOrNull()?.localId ?: 0L) }
    var intervalText by remember { mutableStateOf(existing?.rule?.defaultIntervalDays?.toString() ?: "") }
    val periods = remember { existing?.overridePeriods.orEmpty().toMutableStateList() }

    val selectedType = selectableTypes.find { it.localId == selectedTypeLocalId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (existing != null) "Edit reminder rule" else "Add reminder rule", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 12.dp))

            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { if (existing == null) typeExpanded = it }) {
                OutlinedTextField(
                    value = selectedType?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = existing == null,
                    label = { Text("Reminder type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    selectableTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type.name) }, onClick = { selectedTypeLocalId = type.localId; typeExpanded = false })
                    }
                }
            }

            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                label = { Text("Default interval, in days") },
                placeholder = { Text("Blank = paused outside overrides") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )

            Text("Seasonal overrides (optional)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Text(
                "Override the default interval during specific date ranges.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            periods.forEachIndexed { index, period ->
                OverridePeriodRow(
                    period = period,
                    onChange = { periods[index] = it },
                    onRemove = { periods.removeAt(index) },
                )
            }
            OutlinedButton(onClick = { periods.add(OverridePeriodEntity(reminderRuleLocalId = 0, startMonth = 1, startDay = 1, endMonth = 1, endDay = 1, intervalDays = null)) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Add override", modifier = Modifier.padding(start = 4.dp))
            }

            error?.let { Text(it, color = colors.overdue, modifier = Modifier.padding(top = 12.dp)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, enabled = !isSaving, colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.overdue)) {
                        Text("Delete")
                    }
                }
                Spacer(Modifier.width(0.dp))
                Button(
                    onClick = {
                        onSave(selectedTypeLocalId, intervalText.toIntOrNull(), periods.toList())
                    },
                    enabled = !isSaving && selectedTypeLocalId != 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (existing != null) "Save changes" else "Create rule")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OverridePeriodRow(period: OverridePeriodEntity, onChange: (OverridePeriodEntity) -> Unit, onRemove: () -> Unit) {
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        MonthDayPicker(month = period.startMonth, day = period.startDay, onChange = { m, d -> onChange(period.copy(startMonth = m, startDay = d)) })
        Text("–", modifier = Modifier.padding(horizontal = 2.dp))
        MonthDayPicker(month = period.endMonth, day = period.endDay, onChange = { m, d -> onChange(period.copy(endMonth = m, endDay = d)) })
        NumberField(period.intervalDays ?: 0, { onChange(period.copy(intervalDays = it.takeIf { d -> d > 0 })) }, "Every d", 64.dp)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove override")
        }
    }
}

@Composable
private fun NumberField(value: Int, onChange: (Int) -> Unit, label: String, width: Dp) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.toIntOrNull() ?: 0) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(width),
    )
}
