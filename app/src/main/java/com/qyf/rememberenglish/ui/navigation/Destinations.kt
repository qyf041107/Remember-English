package com.qyf.rememberenglish.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.qyf.rememberenglish.R

/** 路由常量（CLAUDE.md：底部导航 4 tab + 词详情 + 扫词添加） */
object Route {
    const val TODAY = "today"
    const val LIBRARY = "library"
    const val MINE = "mine"
    const val PROFILE = "profile"
    const val WORD_DETAIL = "word/{wordId}"
    const val ADD_WORD = "add"

    fun wordDetail(wordId: Long) = "word/$wordId"
}

data class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Route.TODAY, R.string.tab_today, Icons.Filled.Home),
    TopLevelDestination(Route.LIBRARY, R.string.tab_library, Icons.Filled.List),
    TopLevelDestination(Route.MINE, R.string.tab_mine, Icons.Filled.Star),
    TopLevelDestination(Route.PROFILE, R.string.tab_profile, Icons.Filled.Person),
)
