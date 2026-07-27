package com.lunentous.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

// Year-agnostic (these are recurring annual month/day ranges with no year
// attached), so February is always treated as having 29 days rather than
// picking a reference year -- lets a window's end genuinely reach Feb 29.
private val DAYS_IN_MONTH = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/**
 * Replaces a bare pair of numeric month/day fields with an actual small
 * calendar popover -- used for reminder rule override periods and phase
 * windows, both month/day ranges with no year attached (mirrors
 * web/src/components/MonthDayPicker.tsx).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthDayPicker(month: Int, day: Int, onChange: (month: Int, day: Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var viewMonth by remember(expanded) { mutableStateOf(month) }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text("${MONTH_NAMES[month - 1]} $day")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(
                modifier = Modifier.width(240.dp).fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewMonth = if (viewMonth == 1) 12 else viewMonth - 1 }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
                Text(MONTH_NAMES[viewMonth - 1], style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { viewMonth = if (viewMonth == 12) 1 else viewMonth + 1 }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }
            FlowRow(
                modifier = Modifier.width(240.dp).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (d in 1..DAYS_IN_MONTH[viewMonth - 1]) {
                    val selected = viewMonth == month && d == day
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onChange(viewMonth, d); expanded = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current,
                        )
                    }
                }
            }
        }
    }
}
