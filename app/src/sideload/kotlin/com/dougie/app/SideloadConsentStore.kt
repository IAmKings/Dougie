package com.dougie.app

import android.content.Context

object SideloadConsentStore {
    private const val PREFS = "sideload_consent"
    private const val KEY = "sideload_a11y_consent"

    fun isGranted(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun grant(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, true)
            .apply()
    }
}
