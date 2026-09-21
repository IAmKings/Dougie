package com.dougie.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class DougieColorsTest {
    @Test
    fun lightPrimaryIsStitchBlue() {
        assertEquals(Color(0xFF3D5198), DougieColors.Light.Primary)
    }

    @Test
    fun darkSurfaceIsDeepAndOnSurfaceIsLight() {
        assertEquals(Color(0xFF191C1C), DougieColors.Dark.Surface)
        assertEquals(Color(0xFFE1E3E2), DougieColors.Dark.OnSurface)
    }

    @Test
    fun terminalSkinUsesAnsiRoleColors() {
        assertEquals(Color(0xFF0D0F0F), DougieColors.TerminalSkin.Bg)
        assertEquals(Color(0xFF1A1C1C), DougieColors.TerminalSkin.Bubble)
        assertEquals(Color(0xFF6EF7F6), DougieColors.TerminalSkin.User)
        assertEquals(Color(0xFF00C853), DougieColors.TerminalSkin.Assistant)
        assertEquals(Color(0xFFFFB300), DougieColors.TerminalSkin.Thinking)
        assertEquals(Color(0xFFFF5555), DougieColors.TerminalSkin.Fail)
        assertEquals(Color(0xFF8E909C), DougieColors.TerminalSkin.Muted)
    }

    @Test
    fun chatBodyFontIsMonospaceOnlyWhenTerminal() {
        assertEquals(FontFamily.Monospace, chatBodyFontFamily(true))
        assertEquals(FontFamily.Default, chatBodyFontFamily(false))
    }
}
