package com.lunentous.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.ui.graphics.vector.ImageVector

/** The app's 5 top-level destinations -- mirrors the web topbar's nav
 * (web/src/components/Nav.tsx) exactly, just rendered as an icons-only
 * bottom bar (portrait) / rail (landscape) instead of a topbar. */
enum class NavDestination(val route: String, val label: String, val icon: ImageVector) {
    Dashboard(route = "dashboard", label = "Dashboard", icon = Icons.Default.Dashboard),
    Calendar(route = "calendar", label = "Calendar", icon = Icons.Default.CalendarMonth),
    ReminderTypes(route = "reminder_types", label = "Reminder Types", icon = Icons.Default.Notifications),
    PhaseTypes(route = "phase_types", label = "Phase Types", icon = Icons.Default.Spa),
    Settings(route = "settings", label = "Settings", icon = Icons.Default.Settings),
}
