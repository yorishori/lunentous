package com.lunentous.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.remote.buildPhotoUrl
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.components.ConfirmDialog
import com.lunentous.app.ui.components.ReminderTypeQuickLogButton
import com.lunentous.app.ui.icons.iconFor
import com.lunentous.app.ui.plant.TimelineEntryFormSheet
import com.lunentous.app.ui.theme.LunentousExtendedTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onPlantClick: (Long) -> Unit,
    onAddPlant: () -> Unit,
    resetSignal: Int = 0,
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory { initializer { DashboardViewModel(container) } },
    )
    val uiState by viewModel.uiState.collectAsState()
    val colors = LunentousExtendedTheme.colors
    val upcomingTint = MaterialTheme.colorScheme.onSurface
    val baseUrl = container.sessionStore.getBaseUrl()
    val gridState = rememberLazyGridState()
    var untypedEntryPlant by remember { mutableStateOf<PlantEntity?>(null) }

    // Tapping the Dashboard nav item (from anywhere, including while
    // already here) bumps resetSignal -- scroll back to the top rather
    // than leaving the grid wherever it was.
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) gridState.animateScrollToItem(0)
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddPlant, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add plant") })
        },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (uiState.overdue.isNotEmpty()) {
                        fullSpanSection(
                            title = "Overdue",
                            icon = Icons.Filled.WarningAmber,
                            tint = colors.overdue,
                            tasks = uiState.overdue,
                            onMarkDone = viewModel::requestMarkDone,
                            onPlantClick = onPlantClick,
                        )
                    }
                    if (uiState.upcoming.isNotEmpty()) {
                        fullSpanSection(
                            title = "Next tasks",
                            icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            tint = upcomingTint,
                            tasks = uiState.upcoming,
                            onMarkDone = viewModel::requestMarkDone,
                            onPlantClick = onPlantClick,
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Plants", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    if (uiState.plants.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("No plants yet. Add your first one.", color = colors.textMuted)
                        }
                    }
                    items(uiState.plants, key = { it.plant.localId }) { plantCard ->
                        PlantGridCard(
                            data = plantCard,
                            photoUrl = buildPhotoUrl(baseUrl, plantCard.plant.avatarPhotoPath),
                            onClick = { onPlantClick(plantCard.plant.localId) },
                            onQuickLog = { type -> viewModel.requestMarkDoneForType(plantCard.plant, type) },
                            onAddUntyped = { untypedEntryPlant = plantCard.plant },
                        )
                    }
                }
            }
        }
    }

    val task = viewModel.confirmingTask
    ConfirmDialog(
        open = task != null,
        title = if (task?.isOneTime == true) "Mark complete?" else "Mark as done?",
        message = task?.let {
            if (it.isOneTime) {
                "This marks \"${it.reminderTypeName}\" complete for ${it.plantName}."
            } else {
                "This logs \"${it.reminderTypeName}\" for ${it.plantName} today and recalculates its next due date."
            }
        } ?: "",
        confirmLabel = if (task?.isOneTime == true) "Mark complete" else "Mark as done",
        pending = viewModel.isMarkingDone,
        onConfirm = viewModel::confirmMarkDone,
        onDismiss = viewModel::dismissConfirm,
    )

    untypedEntryPlant?.let { plant ->
        TimelineEntryFormSheet(
            reminderTypes = emptyList(),
            existing = null,
            fixedPlantLocalId = plant.localId,
            baseUrl = baseUrl,
            isSaving = viewModel.isSavingUntypedEntry,
            error = viewModel.untypedEntryError,
            onDismiss = { untypedEntryPlant = null },
            onSave = { plantLocalId, eventDate, _, text, photoFiles ->
                viewModel.logUntypedEntry(plantLocalId, eventDate, text, photoFiles) { untypedEntryPlant = null }
            },
            onDelete = null,
        )
    }
}

private fun LazyGridScope.fullSpanSection(
    title: String,
    icon: ImageVector,
    tint: Color,
    tasks: List<ReminderTask>,
    onMarkDone: (ReminderTask) -> Unit,
    onPlantClick: (Long) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(icon, contentDescription = null, tint = tint)
                Text(title, style = MaterialTheme.typography.titleLarge, color = tint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tasks.forEach { task ->
                    TaskRow(task = task, onMarkDone = { onMarkDone(task) }, onClick = { onPlantClick(task.plantLocalId) })
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: ReminderTask, onMarkDone: () -> Unit, onClick: () -> Unit) {
    val colors = LunentousExtendedTheme.colors
    val isOverdue = task.daysOverdue >= 0
    val label = when {
        task.daysOverdue == 0 -> "Due today"
        task.daysOverdue > 0 -> "${task.daysOverdue}d overdue"
        else -> "in ${-task.daysOverdue}d"
    }
    val badgeColor = if (isOverdue) { if (task.daysOverdue == 0) colors.dueToday else colors.overdue } else colors.ok

    val typeColor = task.reminderTypeColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent

    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(typeColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(task.reminderTypeIcon), contentDescription = null, tint = typeColor, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(task.plantName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task.reminderTypeName, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = badgeColor)
            FilledIconButton(onClick = onMarkDone, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Check, contentDescription = "Mark ${task.reminderTypeName} as done for ${task.plantName}", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A large top-of-card photo (rather than a small avatar beside the text)
 * is the point here -- plants are a visual, photo-driven thing to browse,
 * so the grid should read like a photo grid first and a task list
 * second. The bottom bar is one quick-log button per reminder type this
 * plant has a rule for (icon+color+plus badge, same control as the task
 * rows used to use), plus one more plain "+" for a journal-only entry
 * with no reminder type attached. */
@Composable
private fun PlantGridCard(
    data: PlantCardData,
    photoUrl: String?,
    onClick: () -> Unit,
    onQuickLog: (QuickLogType) -> Unit,
    onAddUntyped: () -> Unit,
) {
    val colors = LunentousExtendedTheme.colors
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            PlantCardImage(photoUrl = photoUrl, modifier = Modifier.fillMaxWidth().height(120.dp))
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(data.plant.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        data.plant.species?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textMuted)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    data.quickLogTypes.forEach { type ->
                        val typeColor = type.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: colors.accent
                        ReminderTypeQuickLogButton(
                            icon = iconFor(type.icon),
                            tint = typeColor,
                            contentDescription = "Log ${type.name}",
                            onClick = { onQuickLog(type) },
                            size = 40.dp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceHover)
                            .clickable(onClickLabel = "Add an untyped entry", onClick = onAddUntyped),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add an untyped entry", tint = colors.textMuted)
                    }
                }
            }
        }
    }
}

/** Same fallback icon/tint as PlantAvatar (mirrors PlantCard.tsx's avatar
 * placeholder) but rectangular and top-corner-rounded to sit flush with
 * the card's own shape, instead of a small circular crop. */
@Composable
private fun PlantCardImage(photoUrl: String?, modifier: Modifier = Modifier) {
    val colors = LunentousExtendedTheme.colors
    val shape = MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
    if (photoUrl != null) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(modifier = modifier.clip(shape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Spa, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
        }
    }
}
