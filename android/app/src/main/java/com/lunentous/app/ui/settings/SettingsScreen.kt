package com.lunentous.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lunentous.app.data.remote.dto.ApiKeyDto
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.theme.LunentousExtendedTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Web equivalent: pages/Settings.tsx (API keys, backup export), plus a
 * "Server connection" section that has no web counterpart -- the web app
 * is always served from the same origin as its API, so it never needs to
 * ask where the server is. Here, connecting is entirely optional: the app
 * is fully usable standalone (see the Android plan's "Server connection is
 * optional"). API keys and export both require a connection.
 */
@Composable
fun SettingsScreen(container: AppContainer) {
    val sessionStore = container.sessionStore
    var connected by remember { mutableStateOf(sessionStore.hasSession()) }
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })

    LaunchedEffect(connected) {
        if (connected) viewModel.loadApiKeys()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Server connection", style = MaterialTheme.typography.titleMedium)
                if (connected) {
                    ConnectedStatus(
                        serverUrl = sessionStore.getBaseUrl().orEmpty(),
                        onDisconnect = {
                            sessionStore.clear()
                            connected = false
                        },
                    )
                } else {
                    ConnectForm(
                        onConnected = { url, key ->
                            sessionStore.saveSession(url, key)
                            connected = true
                        },
                    )
                }
            }
        }

        if (connected) {
            Card(shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("API keys", style = MaterialTheme.typography.titleMedium)
                    ApiKeysSection(viewModel)
                }
            }

            Card(shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    BackupSection(container)
                }
            }
        }

        // Notification schedule and sync status land in later build phases.
    }
}

@Composable
private fun ConnectedStatus(serverUrl: String, onDisconnect: () -> Unit) {
    Text(
        "Connected to $serverUrl",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onDisconnect) {
        Text("Disconnect")
    }
}

@Composable
private fun ConnectForm(onConnected: (serverUrl: String, apiKey: String) -> Unit) {
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    val canSubmit = serverUrl.isNotBlank() && apiKey.isNotBlank()

    Text(
        "The app works fully offline without this -- connect only if you " +
            "want to sync with a self-hosted Lunentous server.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Server address") },
        placeholder = { Text("192.168.1.50:8080") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text("API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onConnected(serverUrl, apiKey) },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Connect")
    }
}

@Composable
private fun ApiKeysSection(viewModel: SettingsViewModel) {
    val colors = LunentousExtendedTheme.colors
    var label by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label (e.g. android-phone)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { viewModel.createApiKey(label); label = "" }, enabled = !viewModel.isSavingKey) {
            Text("Create")
        }
    }

    viewModel.keysError?.let { Text(it, color = colors.overdue, style = MaterialTheme.typography.bodySmall) }

    viewModel.createdToken?.let { token ->
        Card(shape = MaterialTheme.shapes.small) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("New key created — copy it now, it won't be shown again:", style = MaterialTheme.typography.bodySmall)
                Text(token, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::dismissCreatedToken) { Text("Done") }
            }
        }
    }

    if (viewModel.apiKeys.isEmpty()) {
        Text("No API keys yet.", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            viewModel.apiKeys.forEach { key -> ApiKeyRow(key, onRevoke = { viewModel.revokeApiKey(key.id) }) }
        }
    }
}

@Composable
private fun ApiKeyRow(key: ApiKeyDto, onRevoke: () -> Unit) {
    val colors = LunentousExtendedTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(key.label ?: "(unlabeled)", style = MaterialTheme.typography.bodyMedium)
            Text(key.createdAt, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        OutlinedButton(onClick = onRevoke) { Text("Revoke") }
    }
}

@Composable
private fun BackupSection(container: AppContainer) {
    val colors = LunentousExtendedTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportSuccess by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            exportError = null
            exportSuccess = false
            container.accountRepository.exportBackup()
                .onSuccess { body ->
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        } ?: error("Could not open the chosen file")
                    }.onSuccess { exportSuccess = true }
                        .onFailure { exportError = it.message ?: "Failed to write export" }
                }
                .onFailure { exportError = it.message ?: "Failed to download export" }
            isExporting = false
        }
    }

    Text("Download a full export of the database and photo library.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    Button(
        onClick = { launcher.launch("lunentous-export-${LocalDate.now()}.tar.gz") },
        enabled = !isExporting,
    ) {
        Text(if (isExporting) "Exporting…" else "Download export")
    }
    exportError?.let { Text(it, color = colors.overdue, style = MaterialTheme.typography.bodySmall) }
    if (exportSuccess) {
        Text("Export downloaded", color = colors.ok, style = MaterialTheme.typography.bodySmall)
    }
}
