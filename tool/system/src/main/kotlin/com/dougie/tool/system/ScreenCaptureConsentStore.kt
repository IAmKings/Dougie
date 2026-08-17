package com.dougie.tool.system

import android.app.Activity
import android.content.Intent

object ScreenCaptureConsentStore {
    @Volatile
    var resultCode: Int = Activity.RESULT_CANCELED
        private set

    @Volatile
    var data: Intent? = null
        private set

    fun hasToken(): Boolean = data != null && resultCode == Activity.RESULT_OK

    fun save(resultCode: Int, data: Intent?) {
        this.resultCode = resultCode
        this.data = data
    }

    fun clear() {
        resultCode = Activity.RESULT_CANCELED
        data = null
    }
}
