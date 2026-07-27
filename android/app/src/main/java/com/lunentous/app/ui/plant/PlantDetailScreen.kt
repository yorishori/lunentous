package com.lunentous.app.ui.plant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.data.local.entity.PhaseTypeEntity
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.local.entity.PlantPhaseWindowEntity
import com.lunentous.app.data.local.entity.ReminderTypeEntity
import com.lunentous.app.data.remote.buildPhotoUrl
import com.lunentous.app.data.repository.ReminderRuleWithPeriods
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.components.PlantAvatar
import com.lunentous.app.ui.icons.iconFor
import com.lunentous.app.ui.theme.LunentousExtendedTheme

private sealed interface RuleFormTarget {
    data object Create : RuleFormTarget
    data class Edit(val rule: ReminderRuleWithPeriods) : RuleFormTarget
}

private sealed interface WindowFormTarget {
    data object Create : WindowFormTarget
    data class Edit(val window: PlantPhaseWindowEntity) : WindowFormTarget
}

/** Hero card (photo/info), edit, and archive/unarchive, plus the reminder
 * rules section -- mirrors web/src/pages/PlantDetail.tsx. Phase windows
 * and the timeline feed land in the next build steps (Android plan's
 * Build ordering). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    container: AppContainer,
    plantLocalId: Long,
    onBack: () -> Unit,
    onEdit: (PlantEntity) -> Unit,
) {
    val viewModel: PlantDetailViewModel = viewModel(
        key = "plant_detail_$plantLocalId",
        factory = viewModelFactory { initializer { PlantDetailViewModel(container, plantLocalId) } },
    )
    val plant by viewModel.plant.collectAsState()
    val reminderRules by viewModel.reminderRules.collectAsState()
    val reminderTypes by viewModel.reminderTypes.collectAsState()
    val phaseTypes by viewModel.phaseTypes.collectAsState()
    val phaseWindows by viewModel.phaseWindows.collectAsState()
    var ruleFormTarget by remember { mutableStateOf<RuleFormTarget?>(null) }
    var windowFormTarget by remember { mutableStateOf<WindowFormTarget?>(null) }
    val colors = LunentousExtendedTheme.colors
    val baseUrl = container.sessionStore.getBaseUrl()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plant?.name ?: "Plant", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = plant
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PlantAvatar(photoUrl = buildPhotoUrl(baseUrl, current.avatarPhotoPath), size = 84.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(current.name, style = MaterialTheme.typography.headlineSmall)
                            current.species?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                            }
                            current.location?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                }
                            }
                            current.acquiredDate?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                                    Text("since $it", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                }
                            }
                        }
                    }

                    current.generalNotes?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = 12.dp))
                    }

                    viewModel.error?.let {
                        Text(it, color = colors.overdue, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
                        OutlinedButton(onClick = { onEdit(current) }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Edit", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = viewModel::toggleArchive, enabled = !viewModel.isArchiving) {
                            if (current.archived) {
                                Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Unarchive", modifier = Modifier.padding(start = 4.dp))
                            } else {
                                Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Archive", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }

            PhaseWindowsSection(
                windows = phaseWindows,
                types = phaseTypes,
                onAdd = { windowFormTarget = WindowFormTarget.Create },
                onEdit = { window -> windowFormTarget = WindowFormTarget.Edit(window) },
            )

            ReminderRulesSection(
                rules = reminderRules,
                types = reminderTypes,
                onAdd = { ruleFormTarget = RuleFormTarget.Create },
                onEdit = { rule -> ruleFormTarget = RuleFormTarget.Edit(rule) },
            )
        }
    }

    ruleFormTarget?.let { target ->
        val existing = (target as? RuleFormTarget.Edit)?.rule
        val usedTypeLocalIds = reminderRules.map { it.rule.reminderTypeLocalId }.toSet()
        val selectableTypes = reminderTypes.filter { it.localId !in usedTypeLocalIds || it.localId == existing?.rule?.reminderTypeLocalId }
        ReminderRuleFormSheet(
            selectableTypes = selectableTypes,
            existing = existing,
            isSaving = viewModel.isSavingRule,
            error = viewModel.ruleError,
            onDismiss = { ruleFormTarget = null },
            onSave = { reminderTypeLocalId, defaultIntervalDays, periods ->
                viewModel.saveReminderRule(existing?.rule?.localId, reminderTypeLocalId, defaultIntervalDays, periods) {
                    ruleFormTarget = null
                }
            },
            onDelete = existing?.let { e ->
                { viewModel.deleteReminderRule(e.rule.localId) { ruleFormTarget = null } }
            },
        )
    }

    windowFormTarget?.let { target ->
        val existing = (target as? WindowFormTarget.Edit)?.window
        PhaseWindowFormSheet(
            phaseTypes = phaseTypes,
            existing = existing,
            isSaving = viewModel.isSavingWindow,
            error = viewModel.windowError,
            onDismiss = { windowFormTarget = null },
            onSave = { phaseTypeLocalId, startMonth, startDay, endMonth, endDay, notes ->
                viewModel.savePhaseWindow(existing?.localId, phaseTypeLocalId, startMonth, startDay, endMonth, endDay, notes) {
                    windowFormTarget = null
                }
            },
            onDelete = existing?.let { w ->
                { viewModel.deletePhaseWindow(w.localId) { windowFormTarget = null } }
            },
        )
    }
}

@Composable
private fun PhaseWindowsSection(
    windows: List<PlantPhaseWindowEntity>,
    types: List<PhaseTypeEntity>,
    onAdd: () -> Unit,
    onEdit: (PlantPhaseWindowEntity) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    val typesById = types.associateBy { it.localId }

    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Phase windows", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Add", modifier = Modifier.padding(start = 2.dp))
            }
        }

        if (windows.isEmpty()) {
            Text("No phase windows yet.", color = colors.textMuted, modifier = Modifier.padding(top = 4.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            windows.forEach { window ->
                val type = typesById[window.phaseTypeLocalId]
                val typeColor = type?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent

                Card(onClick = { onEdit(window) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(color = typeColor, shape = CircleShape))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type?.name ?: "Unknown type", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${window.startMonth}/${window.startDay} – ${window.endMonth}/${window.endDay}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Icon(Icons.Filled.Edit, contentDescription = "Edit phase window", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRulesSection(
    rules: List<ReminderRuleWithPeriods>,
    types: List<ReminderTypeEntity>,
    onAdd: () -> Unit,
    onEdit: (ReminderRuleWithPeriods) -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    val typesById = types.associateBy { it.localId }
    val hasAvailableType = types.any { type -> rules.none { it.rule.reminderTypeLocalId == type.localId } }

    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reminder rules", style = MaterialTheme.typography.titleLarge)
            if (hasAvailableType) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Add", modifier = Modifier.padding(start = 2.dp))
                }
            }
        }

        if (rules.isEmpty()) {
            Text("No reminder rules yet.", color = colors.textMuted, modifier = Modifier.padding(top = 4.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            rules.forEach { ruleWithPeriods ->
                val type = typesById[ruleWithPeriods.rule.reminderTypeLocalId]
                val typeColor = type?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent
                val interval = ruleWithPeriods.rule.defaultIntervalDays
                val summary = buildString {
                    append(if (interval != null) "Every $interval days" else "Paused by default")
                    if (ruleWithPeriods.overridePeriods.isNotEmpty()) {
                        append(" · ${ruleWithPeriods.overridePeriods.size} override${if (ruleWithPeriods.overridePeriods.size == 1) "" else "s"}")
                    }
                }

                Card(onClick = { onEdit(ruleWithPeriods) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(32.dp).background(color = typeColor.copy(alpha = 0.16f), shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(iconFor(type?.icon), contentDescription = null, tint = typeColor, modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type?.name ?: "Unknown type", style = MaterialTheme.typography.bodyMedium)
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                        Icon(Icons.Filled.Edit, contentDescription = "Edit reminder rule", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
