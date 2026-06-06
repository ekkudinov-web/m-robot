package com.minfinrobot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Тема приложения. Кладёт изумрудно-золотую палитру в M3 colorScheme,
 * благодаря чему ВСЕ стандартные Card/Button/Text/OutlinedTextField на
 * существующих экранах автоматически перекрашиваются — без переписывания.
 *
 * Тема всегда тёмная (luxury-изумруд), независимо от системной настройки.
 */
private val DarkEmeraldScheme = darkColorScheme(
    primary = AppColors.Gold,
    onPrimary = AppColors.TextOnGold,
    primaryContainer = AppColors.GoldDeep,
    onPrimaryContainer = AppColors.TextPrimary,

    secondary = AppColors.GoldBright,
    onSecondary = AppColors.TextOnGold,
    secondaryContainer = AppColors.SurfaceElevated,
    onSecondaryContainer = AppColors.TextPrimary,

    tertiary = AppColors.Buy,
    onTertiary = AppColors.TextOnGold,

    background = AppColors.BackgroundMid,
    onBackground = AppColors.TextPrimary,

    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurfaceVariant = AppColors.TextSecondary,

    error = AppColors.Error,
    onError = AppColors.TextPrimary,

    outline = AppColors.GoldDim,
    outlineVariant = AppColors.DividerGold
)

@Composable
fun MinfinRobotTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkEmeraldScheme,
        typography = AppTypography,
        content = content
    )
}
