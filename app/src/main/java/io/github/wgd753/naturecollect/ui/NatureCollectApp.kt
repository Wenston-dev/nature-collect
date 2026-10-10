package io.github.wgd753.naturecollect.ui

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
import io.github.wgd753.naturecollect.ui.globe.GlobeScreen
import io.github.wgd753.naturecollect.ui.theme.Card
import io.github.wgd753.naturecollect.ui.theme.InkSoft

/** The five bottom tabs, in display order. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Board("board", "贴纸板", Icons.Outlined.AutoAwesomeMosaic),
    Camera("camera", "拍照", Icons.Outlined.PhotoCamera),
    Calendar("calendar", "日历", Icons.Outlined.CalendarMonth),
    Globe("globe", "地图", Icons.Outlined.Public),
    Collection("collection", "图鉴", Icons.AutoMirrored.Outlined.MenuBook),
}

@Composable
fun NatureCollectApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Card) {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = InkSoft,
                            unselectedTextColor = InkSoft,
                            indicatorColor = Color.Transparent,
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
        ) {
            composable(Tab.Board.route) { BoardScreen() }
            composable(Tab.Camera.route) { CameraScreen() }
            composable(Tab.Calendar.route) { CalendarScreen() }
            composable(Tab.Globe.route) { GlobeScreen() }
            composable(Tab.Collection.route) { CollectionScreen() }
        }
    }
}
