package com.qyf.rememberenglish.ui

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.qyf.rememberenglish.ui.add.AddWordScreen
import com.qyf.rememberenglish.ui.library.LibraryScreen
import com.qyf.rememberenglish.ui.library.WordDetailScreen
import com.qyf.rememberenglish.ui.mine.MyWordsScreen
import com.qyf.rememberenglish.ui.navigation.Route
import com.qyf.rememberenglish.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.qyf.rememberenglish.ui.profile.ProfileScreen
import com.qyf.rememberenglish.ui.study.StudyScreen

/** 应用主壳：底部 4 tab + NavHost + 通知 deep link（CLAUDE.md 屏幕清单） */
@Composable
fun RememberEnglishAppUi(
    onNewIntentListener: (Consumer<Intent>) -> Unit = {},
) {
    val navController = rememberNavController()

    // M3：通知点击（rememberenglish://study）从后台进来时导航到学习页
    DisposableEffect(navController, onNewIntentListener) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        onNewIntentListener(listener)
        onDispose { }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TOP_LEVEL_DESTINATIONS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TOP_LEVEL_DESTINATIONS.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(Route.TODAY) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(
                Route.TODAY,
                deepLinks = listOf(navDeepLink { uriPattern = "rememberenglish://study" }),
            ) { StudyScreen() }
            composable(Route.LIBRARY) {
                LibraryScreen(onWordClick = { navController.navigate(Route.wordDetail(it)) })
            }
            composable(Route.MINE) {
                MyWordsScreen(
                    onWordClick = { navController.navigate(Route.wordDetail(it)) },
                    onAddClick = { navController.navigate(Route.ADD_WORD) },
                )
            }
            composable(Route.PROFILE) { ProfileScreen() }
            composable(
                Route.WORD_DETAIL,
                arguments = listOf(navArgument("wordId") { type = NavType.LongType }),
            ) {
                WordDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.ADD_WORD) {
                AddWordScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
