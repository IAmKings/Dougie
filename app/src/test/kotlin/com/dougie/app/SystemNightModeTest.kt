package com.dougie.app

import android.app.UiModeManager
import com.dougie.core.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemNightModeTest {

    @Test
    fun mapsPreferencesToSystemNightModes() {
        assertEquals(
            UiModeManager.MODE_NIGHT_NO,
            systemNightModeFor(ThemePreference.LIGHT),
        )
        assertEquals(
            UiModeManager.MODE_NIGHT_YES,
            systemNightModeFor(ThemePreference.DARK),
        )
        assertEquals(
            UiModeManager.MODE_NIGHT_AUTO,
            systemNightModeFor(ThemePreference.SYSTEM),
        )
    }
}
