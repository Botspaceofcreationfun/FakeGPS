package com.gpsfakepro.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gpsfakepro.R
import com.gpsfakepro.ui.locations.LocationsScreen
import com.gpsfakepro.ui.map.MapScreen
import com.gpsfakepro.ui.routes.RouteEditorScreen
import com.gpsfakepro.ui.routes.RoutesScreen
import com.gpsfakepro.ui.settings.SettingsScreen

object Routes {
    const val MAP = "map"
    const val LOCATIONS = "locations"
    const val ROUTES = "routes"
    const val SETTINGS = "settings"
    const val ROUTE_EDITOR = "route_editor"
    const val ROUTE_EDITOR_ARG = "routeId"
    const val ROUTE_EDITOR_PATTERN = "route_editor/{routeId}"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val topLevel = listOf(
        TopLevelDestination(Routes.MAP, R.string.nav_map, Icons.Filled.Map),
        TopLevelDestination(Routes.LOCATIONS, R.string.nav_locations, Icons.AutoMirrored.Filled.List),
        TopLevelDestination(Routes.ROUTES, R.string.nav_routes, Icons.Filled.Route),
        TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevel.map { it.route }) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.MAP) {
                MapScreen()
            }
            composable(Routes.LOCATIONS) {
                LocationsScreen(
                    onUseLocation = {
                        navController.navigate(Routes.MAP)
                    }
                )
            }
            composable(Routes.ROUTES) {
                RoutesScreen(
                    onEditRoute = { routeId ->
                        navController.navigate("${Routes.ROUTE_EDITOR}/$routeId")
                    },
                    onOpenMap = { navController.navigate(Routes.MAP) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.ROUTE_EDITOR_PATTERN,
                arguments = listOf(navArgument(Routes.ROUTE_EDITOR_ARG) { type = NavType.LongType; defaultValue = 0L })
            ) { entry ->
                val routeId = entry.arguments?.getLong(Routes.ROUTE_EDITOR_ARG) ?: 0L
                RouteEditorScreen(
                    routeId = routeId,
                    onDone = { navController.popBackStack() },
                    onStart = {
                        navController.popBackStack()
                        navController.navigate(Routes.MAP)
                    }
                )
            }
        }
    }
}
