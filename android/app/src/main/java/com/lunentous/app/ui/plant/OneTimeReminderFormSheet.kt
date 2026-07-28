package com.lunentous.app.ui.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lunentous.app.data.local.entity.OneTimeReminderEntity
import com.lunentous.app.ui.components.MonthDayPicker
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.time.LocalDate

/**
 * Untyped, no-log, per-plant reminder -- just a date + freeform text (e.g.
 * "give this plant to a friend", "buy a new pot"). Much simpler than
 * ReminderRuleFormSheet since there's no reminder type, interval, or
 * override periods to configure -- see the Android plan's offline-first
 * data layer notes for OneTimeReminderRepository's create/update pattern
 * this sheet drives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneTimeReminderFormSheet(
    existing: OneTimeReminderEntity?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (dueDate: String, text: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = LunentousExtendedTheme.colors

    val existingDate = existing?.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    var month by remember { mutableStateOf(existingDate.monthValue) }
    var day by remember { mutableStateOf(existingDate.dayOfMonth) }
    var year by remember { mutableStateOf(existingDate.year) }
    var text by remember { mutableStateOf(existing?.text ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (existing != null) "Edit one-time reminder" else "Add one-time reminder", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(top = 12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                MonthDayPicker(month = month, day = day, onChange = { m, d -> month = m; day = d })
                OutlinedTextField(
                    value = year.toString(),
                    onValueChange = { input -> input.filter { it.isDigit() }.toIntOrNull()?.let { if (it in 1..9999) year = it } },
                    label = { Text("Year") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("What's this reminder for?") },
                placeholder = { Text("e.g. Give this plant to a friend") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

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
                        // The month/day picker allows Feb 29 regardless of
                        // the separately-typed year field (it's year-
                        // agnostic, like the reminder-rule pickers) -- clamp
                        // to that year's actual last day of the month
                        // instead of letting LocalDate.of() throw.
                        val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
                        val dueDate = LocalDate.of(year, month, minOf(day, maxDay)).toString()
                        onSave(dueDate, text.trim())
                    },
                    enabled = !isSaving && text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (existing != null) "Save changes" else "Add reminder")
                }
            }
        }
    }
}
