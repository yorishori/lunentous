package com.lunentous.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lunentous.app.data.auth.SessionStore

/**
 * Web equivalent: pages/Settings.tsx, plus a "Server connection" section
 * that has no web counterpart -- the web app is always served from the
 * same origin as its API, so it never needs to ask where the server is.
 * Here, connecting is entirely optional: the app is fully usable
 * standalone (see the Android plan's "Server connection is optional").
 */
@Composable
fun SettingsScreen(sessionStore: SessionStore) {
    var connected by remember { mutableStateOf(sessionStore.hasSession()) }

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

        // Notification schedule, sync status, API key management, and
        // backup/export land here in later build phases.
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
