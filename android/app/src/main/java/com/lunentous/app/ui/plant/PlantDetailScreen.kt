package com.lunentous.app.ui.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.data.local.entity.PlantEntity
import com.lunentous.app.data.remote.buildPhotoUrl
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.components.PlantAvatar
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Hero card (photo/info), edit, and archive/unarchive -- mirrors the top
 * section of web/src/pages/PlantDetail.tsx. Reminder rules, phase windows,
 * and the timeline feed land in the next build steps (Android plan's Build
 * ordering). */
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
        }
    }
}
