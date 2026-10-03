package com.vx.anymaker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vx.anymaker.feature.files.FilesScreen
import com.vx.anymaker.feature.home.HomeScreen
import com.vx.anymaker.feature.resize.ResizeScreen
import com.vx.anymaker.feature.resize.ResizeViewModel
import com.vx.anymaker.feature.settings.SettingsScreen
import com.vx.anymaker.nav.Routes
import kotlinx.coroutines.launch

private data class TopTab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TopTab(Routes.HOME, R.string.tab_tools, Icons.Outlined.Apps, Icons.Filled.Apps),
    TopTab(Routes.FILES, R.string.tab_files, Icons.Outlined.Folder, Icons.Filled.Folder),
    TopTab(Routes.SETTINGS, R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AnymakerApp(resizeViewModel: ResizeViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // A photo shared from another app opens the resize screen.
    val resizeState by resizeViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(resizeState.openRequested) {
        if (resizeState.openRequested) {
            if (nav.currentDestination?.route != Routes.RESIZE) nav.navigate(Routes.RESIZE) { launchSingleTop = true }
            resizeViewModel.openHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current == null || current in Routes.topLevel) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("bottom_bar")) {
                    tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(onToolClick = { tool ->
                    val route = tool.route
                    if (route != null) {
                        nav.navigate(route) { launchSingleTop = true }
                    } else {
                        val name = resources.getString(tool.title)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(resources.getString(R.string.coming_soon_message, name))
                        }
                    }
                })
            }
            composable(Routes.FILES) { FilesScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.RESIZE) {
                ResizeScreen(viewModel = resizeViewModel, onBack = { nav.popBackStack() })
            }
        }
    }
}
