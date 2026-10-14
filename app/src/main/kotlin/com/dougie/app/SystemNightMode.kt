package com.dougie.app

import android.app.UiModeManager
import android.util.Log
import android.os.SystemClock
import android.content.Context
import android.os.Build
import com.dougie.core.model.ThemePreference

/** System per-app night mode (UiModeManager.setApplicationNightMode, API 33+). */
fun systemNightModeFor(mode: ThemePreference): Int = when (mode) {
    ThemePreference.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemePreference.DARK -> UiModeManager.MODE_NIGHT_YES
    ThemePreference.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
}

/**
 * Push the in-app theme preference into the system's per-app night mode so the SYSTEM
 * renders this app's startup window in the preferred shade (the starting window is drawn
 * by the system before the process exists and cannot read app-private preferences).
 */
fun syncSystemNightMode(context: Context, mode: ThemePreference) {
    if (BuildConfig.DEBUG) {
        Log.d("ThemeSwitch", "syncSystemNightMode mode=$mode t=${SystemClock.elapsedRealtime()}")
    }
    if (Build.VERSION.SDK_INT < 33) return
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager ?: return
    runCatching { uiModeManager.setApplicationNightMode(systemNightModeFor(mode)) }
}
