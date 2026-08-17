package com.dougie.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppForegroundTracker : DefaultLifecycleObserver {
    @Volatile
    var foreground: Boolean = false
        private set

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
    }
}
