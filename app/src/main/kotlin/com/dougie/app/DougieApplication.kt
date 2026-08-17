package com.dougie.app

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import android.app.Application
import com.dougie.core.llm.OpenAICompatibleProvider
import com.dougie.core.memory.MemoryStore
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.EgressPolicy
import com.dougie.core.runtime.EgressGateway
import com.dougie.core.runtime.LoopEngine
import com.dougie.core.runtime.PolicyEngine
import com.dougie.core.runtime.TaskManager
import com.dougie.core.tool.CalendarCreateTool
import com.dougie.core.tool.CalendarQueryTool
import com.dougie.core.tool.ClipboardReadTool
import com.dougie.core.tool.ClipboardWriteTool
import com.dougie.core.tool.SystemTimeTool
import com.dougie.data.memory.RoomMemoryStore
import com.dougie.data.preferences.PreferenceStore
import com.dougie.tool.system.AndroidCalendarPort
import com.dougie.tool.system.AndroidClipboardPort
import com.dougie.tool.system.DeviceBatteryTool
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DougieApplication : Application() {
    lateinit var taskManager: TaskManager
        private set
    lateinit var preferenceStore: PreferenceStore
        private set
    lateinit var memoryStore: MemoryStore
        private set
    lateinit var permissionUsage: PermissionUsageTracker
        private set

    private val foregroundTracker = AppForegroundTracker()

    override fun onCreate() {
        super.onCreate()
        preferenceStore = PreferenceStore(this)
        memoryStore = RoomMemoryStore(this)
        permissionUsage = PermissionUsageTracker()
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundTracker)
        val dispatcher = Dispatchers.Default
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        val calendarPort = AndroidCalendarPort(this) { permissionUsage.mark(it) }
        val clipboardPort = AndroidClipboardPort(
            context = this,
            isForeground = { foregroundTracker.foreground },
            onUsed = { permissionUsage.mark(CLIPBOARD_USAGE_KEY) },
        )
        val tools = mapOf(
            "battery" to DeviceBatteryTool(this),
            "time" to SystemTimeTool(),
            CalendarQueryTool.NAME to CalendarQueryTool(calendarPort),
            CalendarCreateTool.NAME to CalendarCreateTool(calendarPort),
            ClipboardReadTool.NAME to ClipboardReadTool(clipboardPort),
            ClipboardWriteTool.NAME to ClipboardWriteTool(clipboardPort),
        )
        val provider = OpenAICompatibleProvider(
            client = http,
            config = {
                val prefs = preferenceStore.settings.value
                CloudLlmConfig(
                    baseUrl = prefs.baseUrl,
                    apiKey = prefs.apiKey,
                    model = prefs.model,
                )
            },
            toolDescriptors = { tools.values.map { it.descriptor } },
        )
        val gateway = EgressGateway(
            policy = { EgressPolicy(allowCloud = preferenceStore.settings.value.allowCloud) },
            apiKey = { preferenceStore.settings.value.apiKey.ifBlank { null } },
        )
        taskManager = TaskManager(
            loopEngine = LoopEngine(
                llm = provider,
                tools = tools,
                dispatcher = dispatcher,
                gateway = gateway,
                memoryStore = memoryStore,
                memoryEnabled = { preferenceStore.settings.value.memoryEnabled },
                policyEngine = PolicyEngine { permission ->
                    ContextCompat.checkSelfPermission(this, permission) ==
                        PackageManager.PERMISSION_GRANTED
                },
            ),
            dispatcher = dispatcher,
        )
    }

    companion object {
        const val CLIPBOARD_USAGE_KEY = "clipboard"
    }
}
