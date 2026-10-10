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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.github.wgd753.naturecollect.ui.categories.CategoriesScreen
import io.github.wgd753.naturecollect.ui.onboarding.Onboarding
import androidx.compose.runtime.collectAsState
import io.github.wgd753.naturecollect.ui.theme.Forest
import io.github.wgd753.naturecollect.ui.theme.ForestDeep
import io.github.wgd753.naturecollect.ui.theme.Paper
import io.github.wgd753.naturecollect.ui.theme.Sun
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

private const val SETTINGS_ROUTE = "settings"
private const val CATEGORIES_ROUTE = "categories"

/** The five bottom tabs, in display order. */
enum class Tab(val route: String, private val zh: String, private val en: String, val icon: ImageVector) {
    Board("board", "贴纸板", "Board", Icons.Outlined.AutoAwesomeMosaic),
    Camera("camera", "拍照", "Camera", Icons.Outlined.PhotoCamera),
    Calendar("calendar", "日历", "Calendar", Icons.Outlined.CalendarMonth),
    Globe("globe", "地图", "Map", Icons.Outlined.Public),
    Collection("collection", "图鉴", "Collection", Icons.AutoMirrored.Outlined.MenuBook);

    val label: String get() = tr(zh, en)
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

    val container = rememberContainer()
    val onboarded by container.settings.onboarded.collectAsState()
    val apiKey by container.settings.apiKey.collectAsState()
    // Existing users who already have a key skip the walkthrough.
    if (!onboarded && apiKey == null) Onboarding(onDone = container.settings::setOnboarded)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = onTab,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                val current = Tab.entries.firstOrNull { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
                FloatingNavBar(current, selectTab)
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
                    onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
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
            composable(SETTINGS_ROUTE, enterTransition = slideIn, popExitTransition = slideOut) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
                )
            }
            composable(CATEGORIES_ROUTE, enterTransition = slideIn, popExitTransition = slideOut) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * A floating capsule instead of a flat tab bar: two tabs either side and a
 * raised sunflower shutter in the middle that opens the camera.
 */
@Composable
private fun FloatingNavBar(current: Tab?, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = ForestDeep, spotColor = ForestDeep)
                .clip(RoundedCornerShape(32.dp))
                .background(Forest)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Tab.Collection, current, onSelect)
            NavItem(Tab.Globe, current, onSelect)
            Spacer(Modifier.width(78.dp))
            NavItem(Tab.Calendar, current, onSelect)
            NavItem(Tab.Board, current, onSelect)
        }
        val cameraSelected = current == Tab.Camera
        val lift by animateDpAsState(if (cameraSelected) (-22).dp else (-14).dp, spring(dampingRatio = 0.5f), label = "shutterLift")
        Box(
            Modifier
                .offset(y = lift)
                .size(70.dp)
                .shadow(10.dp, CircleShape, spotColor = ForestDeep)
                .clip(CircleShape)
                .background(Sun)
                .border(5.dp, Paper, CircleShape)
                .clickable { onSelect(Tab.Camera) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = Tab.Camera.label, tint = ForestDeep, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: Tab, current: Tab?, onSelect: (Tab) -> Unit) {
    val selected = tab == current
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent, label = "navBg")
    val tint by animateColorAsState(if (selected) Sun else Color.White.copy(alpha = 0.62f), label = "navTint")
    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable { onSelect(tab) }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
        Text(tab.label, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}
