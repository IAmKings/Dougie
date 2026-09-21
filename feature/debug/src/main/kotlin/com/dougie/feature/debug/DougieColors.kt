package com.dougie.feature.debug

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DougieColors {
    object Light {
        val Primary = Color(0xFF3D5198)
        val Surface = Color(0xFFF8FAF9)
        val SurfaceContainer = Color(0xFFEDEEED)
        val SurfaceContainerLowest = Color(0xFFFFFFFF)
        val OnSurface = Color(0xFF191C1C)
        val OnSurfaceVariant = Color(0xFF444651)
        val OutlineVariant = Color(0xFFC5C5D2)
        val Error = Color(0xFFBA1A1A)
        val StatusCompleted = Color(0xFF2E7D32)
    }

    object Dark {
        val Primary = Color(0xFFB4C5FF)
        val Surface = Color(0xFF191C1C)
        val SurfaceContainer = Color(0xFF1D2121)
        val SurfaceContainerLowest = Color(0xFF0D0F0F)
        val OnSurface = Color(0xFFE1E3E2)
        val OnSurfaceVariant = Color(0xFFC5C5D2)
        val OutlineVariant = Color(0xFF444651)
        val Error = Color(0xFFFFB4AB)
        val StatusCompleted = Color(0xFF81C784)
    }

    val Primary: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Primary else Light.Primary
    val Surface: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Surface else Light.Surface
    val SurfaceContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SurfaceContainer else Light.SurfaceContainer
    val SurfaceContainerLowest: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SurfaceContainerLowest else Light.SurfaceContainerLowest
    val OnSurface: Color @Composable get() = if (isSystemInDarkTheme()) Dark.OnSurface else Light.OnSurface
    val OnSurfaceVariant: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.OnSurfaceVariant else Light.OnSurfaceVariant
    val OutlineVariant: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.OutlineVariant else Light.OutlineVariant
    val Error: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Error else Light.Error
    val StatusCompleted: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.StatusCompleted else Light.StatusCompleted
}
