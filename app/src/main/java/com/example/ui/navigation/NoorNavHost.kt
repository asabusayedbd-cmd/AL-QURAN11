package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.core.di.AppContainer
import com.example.feature.dua.DuaScreen
import com.example.feature.dua.DuaViewModel
import com.example.feature.explorer.ExplorerScreen
import com.example.feature.explorer.ExplorerViewModel
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.library.LibraryScreen
import com.example.feature.library.LibraryViewModel
import com.example.feature.reader.ReaderScreen
import com.example.feature.reader.ReaderViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel

const val ROUTE_HOME = "home"
const val ROUTE_EXPLORER = "explorer"
const val ROUTE_SEARCH = "search"
const val ROUTE_LIBRARY = "library"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_DUA = "dua"
const val ROUTE_READER = "reader/{surahId}/{ayahNumber}?page={targetPage}"

private data class TopNavDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun NoorAppRoot(
    appContainer: AppContainer,
    uiLanguageCode: String,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pendingPlaybackJump by appContainer.pendingPlaybackJump.collectAsStateWithLifecycle()

    LaunchedEffect(pendingPlaybackJump) {
        pendingPlaybackJump?.let { (sid, ayahNum) ->
            navController.navigate("reader/$sid/$ayahNum?page=0") {
                launchSingleTop = true
            }
            appContainer.clearPlaybackJump()
        }
    }

    val topDestinations = listOf(
        TopNavDestination(ROUTE_HOME, R.string.nav_home, Icons.Default.Home),
        TopNavDestination(ROUTE_EXPLORER, R.string.nav_quran, Icons.AutoMirrored.Filled.MenuBook),
        TopNavDestination(ROUTE_SEARCH, R.string.nav_search, Icons.Default.Search),
        TopNavDestination(ROUTE_DUA, R.string.nav_dua, Icons.Default.AutoAwesome),
        TopNavDestination(ROUTE_LIBRARY, R.string.nav_bookmarks, Icons.Default.Bookmark),
        TopNavDestination(ROUTE_SETTINGS, R.string.nav_settings, Icons.Default.Settings)
    )
    val isTopDestination = topDestinations.any { it.route == currentRoute }

    val layoutDirection = if (uiLanguageCode in setOf("ar", "ur")) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            val navigateTopLevel: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            val openReader: (Int, Int) -> Unit = { surahId, ayahNumber ->
                navController.navigate("reader/$surahId/$ayahNumber?page=0")
            }

            val openReaderAtPage: (Int, Int) -> Unit = { surahId, pageNumber ->
                navController.navigate("reader/$surahId/1?page=$pageNumber")
            }

            Scaffold(
                bottomBar = {
                    if (isTopDestination && !useNavRail) {
                        NavigationBar {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                val label = stringResource(dest.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = label) },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_bar_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isTopDestination && useNavRail) {
                        NavigationRail {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                val label = stringResource(dest.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = label) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_rail_${dest.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_HOME,
                            enterTransition = { fadeIn(animationSpec = tween(200)) },
                            exitTransition = { fadeOut(animationSpec = tween(180)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                            popExitTransition = { fadeOut(animationSpec = tween(180)) }
                        ) {
                            composable(ROUTE_HOME) {
                                val vm: HomeViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer {
                                            HomeViewModel(
                                                database = appContainer.database,
                                                preferencesRepository = appContainer.preferencesRepository
                                            )
                                        }
                                    }
                                )
                                HomeScreen(
                                    viewModel = vm,
                                    onOpenReader = openReader,
                                    onOpenExplorer = { navigateTopLevel(ROUTE_EXPLORER) },
                                    onOpenLibrary = { navigateTopLevel(ROUTE_LIBRARY) },
                                    onOpenDua = { navigateTopLevel(ROUTE_DUA) }
                                )
                            }

                            composable(ROUTE_DUA) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val vm: DuaViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer {
                                            DuaViewModel(
                                                context = context,
                                                database = appContainer.database,
                                                okHttpClient = appContainer.okHttpClient,
                                                seeder = appContainer.seeder,
                                                lineTranslator = appContainer.lineTranslator
                                            )
                                        }
                                    }
                                )
                                DuaScreen(
                                    viewModel = vm,
                                    onBack = null
                                )
                            }

                            composable(ROUTE_EXPLORER) {
                                val vm: ExplorerViewModel = viewModel(
                                    key = "explorer_main",
                                    factory = viewModelFactory {
                                        initializer {
                                            ExplorerViewModel(
                                                database = appContainer.database,
                                                preferencesRepository = appContainer.preferencesRepository,
                                                syncRepository = appContainer.quranSyncRepository,
                                                seeder = appContainer.seeder
                                            )
                                        }
                                    }
                                )
                                ExplorerScreen(
                                    viewModel = vm,
                                    initialFocusSearch = false,
                                    onOpenReader = openReader,
                                    onOpenPage = openReaderAtPage
                                )
                            }

                            composable(ROUTE_SEARCH) {
                                val vm: ExplorerViewModel = viewModel(
                                    key = "explorer_search",
                                    factory = viewModelFactory {
                                        initializer {
                                            ExplorerViewModel(
                                                database = appContainer.database,
                                                preferencesRepository = appContainer.preferencesRepository,
                                                syncRepository = appContainer.quranSyncRepository,
                                                seeder = appContainer.seeder
                                            )
                                        }
                                    }
                                )
                                ExplorerScreen(
                                    viewModel = vm,
                                    initialFocusSearch = true,
                                    onOpenReader = openReader,
                                    onOpenPage = openReaderAtPage
                                )
                            }

                            composable(ROUTE_LIBRARY) {
                                val vm: LibraryViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer {
                                            LibraryViewModel(
                                                database = appContainer.database,
                                                preferencesRepository = appContainer.preferencesRepository
                                            )
                                        }
                                    }
                                )
                                LibraryScreen(
                                    viewModel = vm,
                                    onOpenReader = openReader
                                )
                            }

                            composable(ROUTE_SETTINGS) {
                                val vm: SettingsViewModel = viewModel(
                                    factory = viewModelFactory {
                                        initializer {
                                            SettingsViewModel(
                                                database = appContainer.database,
                                                preferencesRepository = appContainer.preferencesRepository,
                                                syncRepository = appContainer.quranSyncRepository,
                                                audioController = appContainer.audioController,
                                                seeder = appContainer.seeder,
                                                lineTranslator = appContainer.lineTranslator
                                            )
                                        }
                                    }
                                )
                                SettingsScreen(viewModel = vm)
                            }

                            composable(
                                route = ROUTE_READER,
                                arguments = listOf(
                                    navArgument("surahId") { type = NavType.IntType },
                                    navArgument("ayahNumber") { type = NavType.IntType },
                                    navArgument("targetPage") {
                                        type = NavType.IntType
                                        defaultValue = 0
                                    }
                                )
                            ) { entry ->
                                val surahId = entry.arguments?.getInt("surahId") ?: 1
                                val ayahNumber = entry.arguments?.getInt("ayahNumber") ?: 1
                                val targetPage = entry.arguments?.getInt("targetPage") ?: 0
                                val vm: ReaderViewModel = viewModel(
                                    key = "reader_$surahId",
                                    factory = viewModelFactory {
                                        initializer {
                                            ReaderViewModel(
                                                initialSurahId = surahId,
                                                database = appContainer.database,
                                                preferencesRepository = appContainer.preferencesRepository,
                                                syncRepository = appContainer.quranSyncRepository,
                                                audioController = appContainer.audioController,
                                                seeder = appContainer.seeder,
                                                lineTranslator = appContainer.lineTranslator
                                            )
                                        }
                                    }
                                )
                                ReaderScreen(
                                    surahId = surahId,
                                    targetAyah = ayahNumber,
                                    targetPage = targetPage,
                                    viewModel = vm,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
