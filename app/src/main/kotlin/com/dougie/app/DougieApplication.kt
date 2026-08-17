package com.dougie.app

import android.app.Application
import com.dougie.core.llm.OpenAICompatibleProvider
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.EgressPolicy
import com.dougie.core.runtime.EgressGateway
import com.dougie.core.runtime.LoopEngine
import com.dougie.core.runtime.TaskManager
import com.dougie.core.tool.SystemTimeTool
import com.dougie.data.preferences.PreferenceStore
import com.dougie.tool.system.DeviceBatteryTool
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DougieApplication : Application() {
    lateinit var taskManager: TaskManager
        private set
    lateinit var preferenceStore: PreferenceStore
        private set

    override fun onCreate() {
        super.onCreate()
        preferenceStore = PreferenceStore(this)
        val dispatcher = Dispatchers.Default
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        val provider = OpenAICompatibleProvider(http) {
            val prefs = preferenceStore.settings.value
            CloudLlmConfig(
                baseUrl = prefs.baseUrl,
                apiKey = prefs.apiKey,
                model = prefs.model,
            )
        }
        val gateway = EgressGateway(
            policy = { EgressPolicy(allowCloud = preferenceStore.settings.value.allowCloud) },
            apiKey = { preferenceStore.settings.value.apiKey.ifBlank { null } },
        )
        taskManager = TaskManager(
            loopEngine = LoopEngine(
                llm = provider,
                tools = mapOf(
                    "battery" to DeviceBatteryTool(this),
                    "time" to SystemTimeTool(),
                ),
                dispatcher = dispatcher,
                gateway = gateway,
            ),
            dispatcher = dispatcher,
        )
    }
}
