package com.dougie.app

import android.content.Context
import android.content.Intent
import android.provider.Settings

object OverlayController {
    fun sync(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, DougieOverlayService::class.java)
        if (OverlayPrefs.isEnabled(app) && Settings.canDrawOverlays(app)) {
            app.startService(intent)
        } else {
            app.stopService(intent)
        }
    }
}
