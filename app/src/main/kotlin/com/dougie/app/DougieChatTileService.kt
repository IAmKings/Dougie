package com.dougie.app

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService

class DougieChatTileService : TileService() {
    override fun onClick() {
        unlockAndRun {
            val launch = chatLaunchIntent(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launch)
            }
        }
    }
}
