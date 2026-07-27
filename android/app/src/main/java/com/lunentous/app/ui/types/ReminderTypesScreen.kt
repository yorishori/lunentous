package com.lunentous.app.ui.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.di.AppContainer

@Composable
fun ReminderTypesScreen(container: AppContainer) {
    val viewModel: ReminderTypesViewModel = viewModel(
        factory = viewModelFactory { initializer { ReminderTypesViewModel(container) } },
    )
    val rows by viewModel.rows.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()

    TypeManagerScreen(
        title = "Reminder Types",
        noun = "Reminder type",
        hasIcon = true,
        rows = rows,
        showArchived = showArchived,
        onShowArchivedChange = viewModel::setShowArchived,
        isSaving = viewModel.isSaving,
        error = viewModel.error,
        onSave = { existingLocalId, name, icon, color, onDone ->
            viewModel.save(existingLocalId, name, icon, color, onDone)
        },
        onToggleArchive = viewModel::toggleArchive,
    )
}
