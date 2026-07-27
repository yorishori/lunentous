package com.lunentous.app.ui.plant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.lunentous.app.data.local.entity.PhotoEntity
import com.lunentous.app.data.remote.photoDisplayModel
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/**
 * Every photo ever logged for this plant, across every timeline event --
 * a simple read-only grid, so this collects TimelineRepository's Flow
 * directly rather than adding a ViewModel just to pass it through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantGalleryScreen(container: AppContainer, plantLocalId: Long, onBack: () -> Unit) {
    val photos by remember(plantLocalId) { container.timelineRepository.observePhotosForPlant(plantLocalId) }
        .collectAsState(initial = emptyList())
    val baseUrl = container.sessionStore.getBaseUrl()
    var viewingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No photos logged for this plant yet.", color = LunentousExtendedTheme.colors.textMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(photos, key = { it.localId }) { photo ->
                    AsyncImage(
                        model = photoDisplayModel(baseUrl, photo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clickable { viewingPhoto = photo },
                    )
                }
            }
        }
    }

    viewingPhoto?.let { photo ->
        Dialog(onDismissRequest = { viewingPhoto = null }) {
            AsyncImage(
                model = photoDisplayModel(baseUrl, photo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clickable { viewingPhoto = null },
            )
        }
    }
}
