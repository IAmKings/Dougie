package com.dougie.app

import android.content.Context
import android.content.res.Configuration
import com.dougie.core.model.ThemePreference

fun wrapThemeContext(base: Context, mode: ThemePreference): Context {
    if (mode == ThemePreference.SYSTEM) return base
    val override = Configuration(base.resources.configuration)
    val night = if (mode == ThemePreference.DARK) {
        Configuration.UI_MODE_NIGHT_YES
    } else {
        Configuration.UI_MODE_NIGHT_NO
    }
    override.uiMode = (override.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    return base.createConfigurationContext(override)
}
