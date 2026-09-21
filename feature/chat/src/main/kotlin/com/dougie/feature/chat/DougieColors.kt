package com.dougie.feature.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DougieColors {
    object Light {
        val Primary = Color(0xFF3D5198)
        val PrimaryContainer = Color(0xFF566AB2)
        val OnPrimary = Color(0xFFFFFFFF)
        val OnPrimaryContainer = Color(0xFFF0F0FF)
        val Surface = Color(0xFFF8FAF9)
        val SurfaceContainer = Color(0xFFEDEEED)
        val SurfaceContainerLow = Color(0xFFF2F4F3)
        val SurfaceContainerLowest = Color(0xFFFFFFFF)
        val SurfaceContainerHigh = Color(0xFFE7E8E8)
        val OnSurface = Color(0xFF191C1C)
        val OnSurfaceVariant = Color(0xFF444651)
        val OutlineVariant = Color(0xFFC5C5D2)
        val Outline = Color(0xFF757682)
        val Error = Color(0xFFBA1A1A)
        val StatusThinking = Color(0xFF566AB2)
        val StatusCompleted = Color(0xFF2E7D32)
        val StatusExecuting = Color(0xFFFFB300)
        val TerminalBg = Color(0xFF0D0F0F)
        val SecondaryFixed = Color(0xFF6EF7F6)
        val TertiaryContainer = Color(0xFF557280)
    }

    object Dark {
        val Primary = Color(0xFFB4C5FF)
        val PrimaryContainer = Color(0xFF3D5198)
        val OnPrimary = Color(0xFF1A2B6B)
        val OnPrimaryContainer = Color(0xFFF0F0FF)
        val Surface = Color(0xFF191C1C)
        val SurfaceContainer = Color(0xFF1D2121)
        val SurfaceContainerLow = Color(0xFF191C1C)
        val SurfaceContainerLowest = Color(0xFF0D0F0F)
        val SurfaceContainerHigh = Color(0xFF282A2A)
        val OnSurface = Color(0xFFE1E3E2)
        val OnSurfaceVariant = Color(0xFFC5C5D2)
        val OutlineVariant = Color(0xFF444651)
        val Outline = Color(0xFF8E909C)
        val Error = Color(0xFFFFB4AB)
        val StatusThinking = Color(0xFFB4C5FF)
        val StatusCompleted = Color(0xFF81C784)
        val StatusExecuting = Color(0xFFFFB300)
        val TerminalBg = Color(0xFF0D0F0F)
        val SecondaryFixed = Color(0xFF6EF7F6)
        val TertiaryContainer = Color(0xFFA4C8D8)
    }

    val Primary: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Primary else Light.Primary
    val PrimaryContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.PrimaryContainer else Light.PrimaryContainer
    val OnPrimary: Color @Composable get() = if (isSystemInDarkTheme()) Dark.OnPrimary else Light.OnPrimary
    val OnPrimaryContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.OnPrimaryContainer else Light.OnPrimaryContainer
    val Surface: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Surface else Light.Surface
    val SurfaceContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SurfaceContainer else Light.SurfaceContainer
    val SurfaceContainerLow: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SurfaceContainerLow else Light.SurfaceContainerLow
    val SurfaceContainerLowest: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SurfaceContainerLowest else Light.SurfaceContainerLowest
    val SurfaceContainerHigh: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SurfaceContainerHigh else Light.SurfaceContainerHigh
    val OnSurface: Color @Composable get() = if (isSystemInDarkTheme()) Dark.OnSurface else Light.OnSurface
    val OnSurfaceVariant: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.OnSurfaceVariant else Light.OnSurfaceVariant
    val OutlineVariant: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.OutlineVariant else Light.OutlineVariant
    val Outline: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Outline else Light.Outline
    val Error: Color @Composable get() = if (isSystemInDarkTheme()) Dark.Error else Light.Error
    val StatusThinking: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.StatusThinking else Light.StatusThinking
    val StatusCompleted: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.StatusCompleted else Light.StatusCompleted
    val StatusExecuting: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.StatusExecuting else Light.StatusExecuting
    val TerminalBg: Color @Composable get() = if (isSystemInDarkTheme()) Dark.TerminalBg else Light.TerminalBg
    val SecondaryFixed: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.SecondaryFixed else Light.SecondaryFixed
    val TertiaryContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Dark.TertiaryContainer else Light.TertiaryContainer
}
