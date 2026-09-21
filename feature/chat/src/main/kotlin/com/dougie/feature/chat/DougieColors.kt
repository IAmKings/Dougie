package com.dougie.feature.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

internal val LocalTerminalTheme = staticCompositionLocalOf { false }

internal fun chatBodyFontFamily(terminalTheme: Boolean): FontFamily =
    if (terminalTheme) FontFamily.Monospace else FontFamily.Default

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

    object TerminalSkin {
        val Bg = Color(0xFF0D0F0F)
        val Bubble = Color(0xFF1A1C1C)
        val User = Color(0xFF6EF7F6)
        val Assistant = Color(0xFF00C853)
        val Thinking = Color(0xFFFFB300)
        val Fail = Color(0xFFFF5555)
        val Muted = Color(0xFF8E909C)
    }

    val Primary: Color @Composable get() = token(Light.Primary, Dark.Primary, TerminalSkin.User)
    val PrimaryContainer: Color @Composable get() =
        token(Light.PrimaryContainer, Dark.PrimaryContainer, TerminalSkin.Bubble)
    val OnPrimary: Color @Composable get() = token(Light.OnPrimary, Dark.OnPrimary, TerminalSkin.Bg)
    val OnPrimaryContainer: Color @Composable get() =
        token(Light.OnPrimaryContainer, Dark.OnPrimaryContainer, TerminalSkin.User)
    val Surface: Color @Composable get() = token(Light.Surface, Dark.Surface, TerminalSkin.Bg)
    val SurfaceContainer: Color @Composable get() =
        token(Light.SurfaceContainer, Dark.SurfaceContainer, TerminalSkin.Bubble)
    val SurfaceContainerLow: Color @Composable get() =
        token(Light.SurfaceContainerLow, Dark.SurfaceContainerLow, TerminalSkin.Bubble)
    val SurfaceContainerLowest: Color @Composable get() =
        token(Light.SurfaceContainerLowest, Dark.SurfaceContainerLowest, TerminalSkin.Bubble)
    val SurfaceContainerHigh: Color @Composable get() =
        token(Light.SurfaceContainerHigh, Dark.SurfaceContainerHigh, TerminalSkin.Bubble)
    val OnSurface: Color @Composable get() = token(Light.OnSurface, Dark.OnSurface, TerminalSkin.Assistant)
    val OnSurfaceVariant: Color @Composable get() =
        token(Light.OnSurfaceVariant, Dark.OnSurfaceVariant, TerminalSkin.Muted)
    val OutlineVariant: Color @Composable get() =
        token(Light.OutlineVariant, Dark.OutlineVariant, TerminalSkin.Muted)
    val Outline: Color @Composable get() = token(Light.Outline, Dark.Outline, TerminalSkin.Muted)
    val Error: Color @Composable get() = token(Light.Error, Dark.Error, TerminalSkin.Fail)
    val StatusThinking: Color @Composable get() =
        token(Light.StatusThinking, Dark.StatusThinking, TerminalSkin.Thinking)
    val StatusCompleted: Color @Composable get() =
        token(Light.StatusCompleted, Dark.StatusCompleted, TerminalSkin.Assistant)
    val StatusExecuting: Color @Composable get() =
        token(Light.StatusExecuting, Dark.StatusExecuting, TerminalSkin.Thinking)
    val TerminalBg: Color @Composable get() = token(Light.TerminalBg, Dark.TerminalBg, TerminalSkin.Bg)
    val SecondaryFixed: Color @Composable get() =
        token(Light.SecondaryFixed, Dark.SecondaryFixed, TerminalSkin.User)
    val TertiaryContainer: Color @Composable get() =
        token(Light.TertiaryContainer, Dark.TertiaryContainer, TerminalSkin.Muted)
}

@Composable
private fun token(light: Color, dark: Color, terminal: Color): Color = when {
    LocalTerminalTheme.current -> terminal
    isSystemInDarkTheme() -> dark
    else -> light
}
