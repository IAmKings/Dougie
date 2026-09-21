package com.dougie.core.model

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    val stored: String
        get() = when (this) {
            SYSTEM -> "system"
            LIGHT -> "light"
            DARK -> "dark"
        }

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(raw: String?): ThemePreference = when (raw) {
            SYSTEM.stored -> SYSTEM
            LIGHT.stored -> LIGHT
            DARK.stored -> DARK
            else -> SYSTEM
        }
    }
}
