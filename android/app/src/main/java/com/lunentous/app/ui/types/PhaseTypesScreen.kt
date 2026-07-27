package com.lunentous.app.ui.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.di.AppContainer

@Composable
fun PhaseTypesScreen(container: AppContainer) {
    val viewModel: PhaseTypesViewModel = viewModel(
        factory = viewModelFactory { initializer { PhaseTypesViewModel(container) } },
    )
    val rows by viewModel.rows.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()

    TypeManagerScreen(
        title = "Phase Types",
        noun = "Phase type",
        hasIcon = false,
        rows = rows,
        showArchived = showArchived,
        onShowArchivedChange = viewModel::setShowArchived,
        isSaving = viewModel.isSaving,
        error = viewModel.error,
        onSave = { existingLocalId, name, _, color, onDone ->
            viewModel.save(existingLocalId, name, color, onDone)
        },
        onToggleArchive = viewModel::toggleArchive,
    )
}
