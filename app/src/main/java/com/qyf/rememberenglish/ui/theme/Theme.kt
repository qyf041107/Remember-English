package com.qyf.rememberenglish.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = White,
    primaryContainer = Blue100,
    onPrimaryContainer = Gray900,
    secondary = Teal300,
    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    outlineVariant = Gray300,
    error = Red400,
)

private val DarkColors = darkColorScheme(
    primary = Blue400,
    onPrimary = Gray900,
    primaryContainer = Gray700,
    onPrimaryContainer = Blue100,
    secondary = Teal300,
    surface = Gray900,
    onSurface = Gray100,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray300,
    outlineVariant = Gray500,
    error = Red400,
)

/** darkTheme 为 null 时跟随系统；非 null 时使用用户设置（M5） */
@Composable
fun RememberEnglishTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
