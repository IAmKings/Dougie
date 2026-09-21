package com.dougie.feature.chat

import androidx.compose.ui.graphics.Color
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
}
