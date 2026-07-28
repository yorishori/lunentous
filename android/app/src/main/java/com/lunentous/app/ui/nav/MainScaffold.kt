package com.lunentous.app.ui.nav

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.calendar.timeline.CareTimelineScreen
import com.lunentous.app.ui.dashboard.DashboardScreen
import com.lunentous.app.ui.plant.PlantDetailScreen
import com.lunentous.app.ui.plant.PlantFormSheet
import com.lunentous.app.ui.plant.PlantFormTarget
import com.lunentous.app.ui.plant.PlantGalleryScreen
import com.lunentous.app.ui.settings.SettingsScreen
import com.lunentous.app.ui.photos.importImageToLocalFile
import com.lunentous.app.ui.sync.SyncIssuesScreen
import com.lunentous.app.ui.sync.SyncStatusBar
import com.lunentous.app.ui.types.PhaseTypesScreen
import com.lunentous.app.ui.types.ReminderTypesScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLANT_LOCAL_ID_ARG = "plantLocalId"
private const val PLANT_DETAIL_ROUTE = "plant_detail/{$PLANT_LOCAL_ID_ARG}"
private fun plantDetailRoute(plantLocalId: Long) = "plant_detail/$plantLocalId"
private const val PLANT_GALLERY_ROUTE = "plant_gallery/{$PLANT_LOCAL_ID_ARG}"
private fun plantGalleryRoute(plantLocalId: Long) = "plant_gallery/$plantLocalId"
private const val SYNC_ISSUES_ROUTE = "sync_issues"

/**
 * Adaptive shell: bottom NavigationBar (icons only) in portrait, side
 * NavigationRail (icons only) in landscape -- Material3's standard
 * adaptive pattern, switched on orientation per the Android plan.
 */
@Composable
fun MainScaffold(container: AppContainer, deepLinkTarget: DeepLinkTarget? = null, onDeepLinkConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Owned here (not inside Dashboard/PlantDetail) since both the
    // dashboard's FAB and the plant detail screen's edit action need to
    // open the same shared create/edit sheet.
    var plantFormTarget by remember { mutableStateOf<PlantFormTarget?>(null) }

    // Set only by a ShareImage deep link, once the shared content:// URI
    // has been copied to a durable local File -- consumed by CalendarScreen
    // to pre-open the new-entry sheet with that photo attached.
    var sharedPhotoFile by remember { mutableStateOf<File?>(null) }

    // Set by the NewTimelineEntry deep link (widget "+" button, app
    // shortcut) -- consumed by CalendarScreen to pre-open the new-entry
    // sheet's plant picker immediately instead of landing on a blank
    // Calendar screen.
    var promptNewEntry by remember { mutableStateOf(false) }

    // Bumped every time the Dashboard nav item is tapped -- consumed by
    // DashboardScreen to scroll back to the top, so tapping it while
    // already there (or from anywhere else) always lands on a fresh view
    // rather than wherever the user had scrolled to.
    var dashboardResetSignal by remember { mutableStateOf(0) }
    val onNavItemClick: (NavDestination) -> Unit = { destination ->
        if (destination == NavDestination.Dashboard) dashboardResetSignal++
        navigateTo(navController, destination)
    }

    // Widget tap / app shortcut / share-to-app / notification tap all land
    // here as a DeepLinkTarget, parsed once from the launching Intent (see
    // MainActivity). Runs once per new target rather than on every
    // recomposition, and clears itself afterward so back-navigation or a
    // config change doesn't re-fire it.
    LaunchedEffect(deepLinkTarget) {
        when (val target = deepLinkTarget) {
            is DeepLinkTarget.PlantDetail -> navController.navigate(plantDetailRoute(target.plantLocalId))
            DeepLinkTarget.Calendar -> navigateTo(navController, NavDestination.Calendar)
            DeepLinkTarget.NewTimelineEntry -> {
                promptNewEntry = true
                navigateTo(navController, NavDestination.Calendar)
            }
            is DeepLinkTarget.ShareImage -> {
                val file = withContext(Dispatchers.IO) { importImageToLocalFile(context, target.uri) }
                if (file != null) {
                    sharedPhotoFile = file
                    navigateTo(navController, NavDestination.Calendar)
                }
            }
            null -> return@LaunchedEffect
        }
        onDeepLinkConsumed()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            NavigationRail {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    NavDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { onNavItemClick(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                Column {
                    SyncStatusBar(
                        container = container,
                        onOpenSyncIssues = { navController.navigate(SYNC_ISSUES_ROUTE) },
                        onOpenSettings = { navigateTo(navController, NavDestination.Settings) },
                    )
                    if (!isLandscape) {
                        NavigationBar {
                            NavDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { onNavItemClick(destination) },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                )
                            }
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
                                resetSignal = dashboardResetSignal,
                            )
                            NavDestination.ReminderTypes -> ReminderTypesScreen(container = container)
                            NavDestination.PhaseTypes -> PhaseTypesScreen(container = container)
                            NavDestination.Calendar -> CareTimelineScreen(
                                container = container,
                                sharedPhotoFile = sharedPhotoFile,
                                onSharedPhotoConsumed = { sharedPhotoFile = null },
                                promptNewEntry = promptNewEntry,
                                onNewEntryPromptConsumed = { promptNewEntry = false },
                            )
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
                        onGallery = { navController.navigate(plantGalleryRoute(plantLocalId)) },
                    )
                }

                composable(
                    route = PLANT_GALLERY_ROUTE,
                    arguments = listOf(navArgument(PLANT_LOCAL_ID_ARG) { type = NavType.LongType }),
                ) { backStackEntry ->
                    val plantLocalId = backStackEntry.arguments?.getLong(PLANT_LOCAL_ID_ARG) ?: return@composable
                    PlantGalleryScreen(
                        container = container,
                        plantLocalId = plantLocalId,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(SYNC_ISSUES_ROUTE) {
                    SyncIssuesScreen(container = container, onBack = { navController.popBackStack() })
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

/**
 * Plant Detail/Gallery are pushed as plain child routes (no popUpTo) on
 * top of whichever tab was active, so a tab's own entry is usually still
 * sitting further down the back stack rather than gone -- popping
 * straight back to it (if present) is simpler and more reliable than the
 * navigate()-with-popUpTo/restoreState dance below, which is really only
 * needed for switching to a tab that isn't currently on the stack at all.
 */
private fun navigateTo(navController: NavHostController, destination: NavDestination) {
    val poppedToExisting = navController.popBackStack(destination.route, false)
    if (!poppedToExisting) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
