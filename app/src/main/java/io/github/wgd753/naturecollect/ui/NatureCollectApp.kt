package io.github.wgd753.naturecollect.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.wgd753.naturecollect.ui.board.BoardScreen
import io.github.wgd753.naturecollect.ui.calendar.CalendarScreen
import io.github.wgd753.naturecollect.ui.camera.CameraScreen
import io.github.wgd753.naturecollect.ui.collection.CollectionScreen
import io.github.wgd753.naturecollect.ui.detail.CaptureDetailScreen
import io.github.wgd753.naturecollect.ui.globe.GlobeScreen
import io.github.wgd753.naturecollect.ui.settings.SettingsScreen
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.InkSoft

private const val SETTINGS_ROUTE = "settings"

/** The five bottom tabs, in display order. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Board("board", "贴纸板", Icons.Outlined.AutoAwesomeMosaic),
    Camera("camera", "拍照", Icons.Outlined.PhotoCamera),
    Calendar("calendar", "日历", Icons.Outlined.CalendarMonth),
    Globe("globe", "地图", Icons.Outlined.Public),
    Collection("collection", "图鉴", Icons.AutoMirrored.Outlined.MenuBook),
}

private val slideIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))
}
private val slideOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200))
}

@Composable
fun NatureCollectApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = Tab.entries.any { tab -> currentDestination?.route == tab.route }
    val openCapture: (String) -> Unit = { id -> navController.navigate("capture/$id") }
    val selectTab: (Tab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) NavigationBar(containerColor = Card) {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = InkSoft,
                            unselectedTextColor = InkSoft,
                            indicatorColor = InkSoft.copy(alpha = 0.22f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Collection.route,
            modifier = Modifier.padding(innerPadding),
            // Quick cross-fades between tabs; detail pages slide in from the right.
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Tab.Board.route) { BoardScreen() }
            composable(Tab.Camera.route) { CameraScreen(onOpenCapture = openCapture) }
            composable(Tab.Calendar.route) { CalendarScreen(onOpenCapture = openCapture) }
            composable(Tab.Globe.route) { GlobeScreen(onOpenCapture = openCapture) }
            composable(Tab.Collection.route) {
                CollectionScreen(
                    onOpenCapture = openCapture,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable("capture/{id}", enterTransition = slideIn, popExitTransition = slideOut) { entry ->
                CaptureDetailScreen(
                    captureId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenBoard = { selectTab(Tab.Board) },
                    // Switching between encounters replaces this page instead of stacking up.
                    onOpenCapture = { id ->
                        navController.navigate("capture/$id") { popUpTo("capture/{id}") { inclusive = true } }
                    },
                )
            }
            composable(SETTINGS_ROUTE, enterTransition = slideIn, popExitTransition = slideOut) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
