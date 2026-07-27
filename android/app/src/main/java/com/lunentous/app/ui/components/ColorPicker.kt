package com.lunentous.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lunentous.app.ui.theme.LunentousExtendedTheme

/** Catppuccin Mocha's accent colors -- the same family the app's own theme
 * is built from, mirroring web/src/components/ColorPicker.tsx's PALETTE. */
private val PALETTE = listOf(
    "#f5e0dc", "#f2cdcd", "#f5c2e7", "#cba6f7", "#f38ba8", "#eba0ac", "#fab387",
    "#f9e2af", "#a6e3a1", "#94e2d5", "#89dceb", "#74c7ec", "#89b4fa", "#b4befe",
)

private fun parseHex(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

@Composable
fun ColorPicker(value: String, onChange: (String) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    val colors = LunentousExtendedTheme.colors
    val swatchColor = remember(value) { parseHex(value) ?: colors.accent }

    OutlinedButton(onClick = { dialogOpen = true }, modifier = Modifier.fillMaxWidth()) {
        ColorSwatch(color = swatchColor, size = 18.dp, selected = false, borderColor = colors.text)
        Text(value, modifier = Modifier.padding(start = 8.dp))
    }

    if (dialogOpen) {
        var customHex by remember { mutableStateOf(value) }

        Dialog(onDismissRequest = { dialogOpen = false }) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(PALETTE) { hex ->
                            ColorSwatch(
                                color = parseHex(hex) ?: colors.accent,
                                size = 36.dp,
                                selected = hex.equals(value, ignoreCase = true),
                                borderColor = colors.text,
                                onClick = { onChange(hex); dialogOpen = false },
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Custom:", modifier = Modifier.padding(end = 8.dp))
                        OutlinedTextField(
                            value = customHex,
                            onValueChange = { customHex = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                if (parseHex(customHex) != null) {
                                    onChange(customHex)
                                    dialogOpen = false
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("Use")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, size: Dp, selected: Boolean, borderColor: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
            .border(if (selected) 2.dp else 0.dp, borderColor, CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}
