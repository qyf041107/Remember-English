package com.qyf.rememberenglish.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.qyf.rememberenglish.R

/** 路由常量（CLAUDE.md：底部导航 4 tab + 词详情 + 单词背诵 + 扫词添加 + 词黑名单） */
object Route {
    const val TODAY = "today"
    const val LIBRARY = "library"
    const val MINE = "mine"
    const val PROFILE = "profile"
    const val WORD_DETAIL = "word/{wordId}"
    const val WORD_STUDY = "word_study/{userWordId}"
    const val ADD_WORD = "add"
    const val BLACKLIST = "blacklist"

    fun wordDetail(wordId: Long) = "word/$wordId"

    fun wordStudy(userWordId: Long) = "word_study/$userWordId"
}

data class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Route.TODAY, R.string.tab_today, Icons.Filled.Home),
    TopLevelDestination(Route.LIBRARY, R.string.tab_library, Icons.AutoMirrored.Filled.List),
    // 星形专用于"星标"功能（用户 2026-09-16），本 tab 改用收藏心形；核心图标集无 Bookmark
    TopLevelDestination(Route.MINE, R.string.tab_mine, Icons.Filled.Favorite),
    TopLevelDestination(Route.PROFILE, R.string.tab_profile, Icons.Filled.Person),
)
