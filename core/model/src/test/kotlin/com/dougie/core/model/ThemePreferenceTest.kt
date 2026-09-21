package com.dougie.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {
    @Test
    fun fromStoredUnknownOrBlankIsSystem() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored(""))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored(" "))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored("night"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored("SYSTEM"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored("true"))
    }

    @Test
    fun fromStoredKnownValues() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored("system"))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromStored("light"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromStored("dark"))
    }

    @Test
    fun storedRoundTrips() {
        ThemePreference.entries.forEach { mode ->
            assertEquals(mode, ThemePreference.fromStored(mode.stored))
        }
        assertEquals("system", ThemePreference.SYSTEM.stored)
        assertEquals("light", ThemePreference.LIGHT.stored)
        assertEquals("dark", ThemePreference.DARK.stored)
    }

    @Test
    fun isDarkFollowsSystemOrOverride() {
        assertFalse(ThemePreference.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemePreference.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemePreference.LIGHT.isDark(systemDark = true))
        assertFalse(ThemePreference.LIGHT.isDark(systemDark = false))
        assertTrue(ThemePreference.DARK.isDark(systemDark = false))
        assertTrue(ThemePreference.DARK.isDark(systemDark = true))
    }
}
