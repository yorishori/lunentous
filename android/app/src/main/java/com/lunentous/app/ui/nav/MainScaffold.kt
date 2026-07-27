package com.lunentous.app.ui.nav

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.settings.SettingsScreen

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
                        if (destination == NavDestination.Settings) {
                            SettingsScreen(sessionStore = container.sessionStore)
                        } else {
                            // Real screens land in later build phases -- see
                            // the Android plan's Build ordering. This proves
                            // the nav shell works end to end in the meantime.
                            PlaceholderScreen(destination.label)
                        }
                    }
                }
            }
        }
    }
}

private fun navigateTo(navController: NavHostController, destination: NavDestination) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
