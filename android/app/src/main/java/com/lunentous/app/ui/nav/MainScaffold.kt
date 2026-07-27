package com.lunentous.app.ui.nav

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.calendar.CalendarScreen
import com.lunentous.app.ui.dashboard.DashboardScreen
import com.lunentous.app.ui.plant.PlantDetailScreen
import com.lunentous.app.ui.plant.PlantFormSheet
import com.lunentous.app.ui.plant.PlantFormTarget
import com.lunentous.app.ui.settings.SettingsScreen
import com.lunentous.app.ui.types.PhaseTypesScreen
import com.lunentous.app.ui.types.ReminderTypesScreen

private const val PLANT_LOCAL_ID_ARG = "plantLocalId"
private const val PLANT_DETAIL_ROUTE = "plant_detail/{$PLANT_LOCAL_ID_ARG}"
private fun plantDetailRoute(plantLocalId: Long) = "plant_detail/$plantLocalId"

/**
 * Adaptive shell: bottom NavigationBar (icons only) in portrait, side
 * NavigationRail (icons only) in landscape -- Material3's standard
 * adaptive pattern, switched on orientation per the Android plan.
 */
@Composable
fun MainScaffold(container: AppContainer) {
    val navController = rememberNavController()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Owned here (not inside Dashboard/PlantDetail) since both the
    // dashboard's FAB and the plant detail screen's edit action need to
    // open the same shared create/edit sheet.
    var plantFormTarget by remember { mutableStateOf<PlantFormTarget?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            NavigationRail {
                NavDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = currentRoute == destination.route,
                        onClick = { navigateTo(navController, destination) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!isLandscape) {
                    NavigationBar {
                        NavDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateTo(navController, destination) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = NavDestination.Dashboard.route,
                modifier = Modifier.padding(contentPadding),
            ) {
                NavDestination.entries.forEach { destination ->
                    composable(destination.route) {
                        when (destination) {
                            NavDestination.Settings -> SettingsScreen(container = container)
                            NavDestination.Dashboard -> DashboardScreen(
                                container = container,
                                onPlantClick = { plantLocalId -> navController.navigate(plantDetailRoute(plantLocalId)) },
                                onAddPlant = { plantFormTarget = PlantFormTarget.Create },
                            )
                            NavDestination.ReminderTypes -> ReminderTypesScreen(container = container)
                            NavDestination.PhaseTypes -> PhaseTypesScreen(container = container)
                            NavDestination.Calendar -> CalendarScreen(container = container)
                        }
                    }
                }

                composable(
                    route = PLANT_DETAIL_ROUTE,
                    arguments = listOf(navArgument(PLANT_LOCAL_ID_ARG) { type = NavType.LongType }),
                ) { backStackEntry ->
                    val plantLocalId = backStackEntry.arguments?.getLong(PLANT_LOCAL_ID_ARG) ?: return@composable
                    PlantDetailScreen(
                        container = container,
                        plantLocalId = plantLocalId,
                        onBack = { navController.popBackStack() },
                        onEdit = { plant -> plantFormTarget = PlantFormTarget.Edit(plant) },
                    )
                }
            }
        }
    }

    plantFormTarget?.let { target ->
        val existing = (target as? PlantFormTarget.Edit)?.plant
        PlantFormSheet(
            container = container,
            existing = existing,
            onDismiss = { plantFormTarget = null },
            onSaved = { plantLocalId ->
                plantFormTarget = null
                if (target is PlantFormTarget.Create) {
                    navController.navigate(plantDetailRoute(plantLocalId))
                }
            },
        )
    }
}

private fun navigateTo(navController: NavHostController, destination: NavDestination) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
