package com.lunentous.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Mirrors web/src/components/ConfirmDialog.tsx's role -- a single reusable
 * confirm/cancel dialog rather than one-off AlertDialogs per screen. */
@Composable
fun ConfirmDialog(
    open: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    pending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !pending) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) { Text("Cancel") }
        },
    )
}
