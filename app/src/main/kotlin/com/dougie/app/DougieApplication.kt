package com.dougie.app

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import android.app.Application
import com.dougie.core.llm.OpenAICompatibleProvider
import com.dougie.core.memory.MemoryStore
import com.dougie.core.model.CloudLlmConfig
import com.dougie.core.model.EgressPolicy
import com.dougie.core.model.AndroidPermissions
import com.dougie.core.runtime.EgressGateway
import com.dougie.core.runtime.LoopEngine
import com.dougie.core.runtime.PolicyEngine
import com.dougie.core.runtime.TaskManager
import com.dougie.core.runtime.recoverInterrupted
import com.dougie.core.tool.AppIntentTool
import com.dougie.core.tool.AgentTool
import com.dougie.core.tool.CalendarCreateTool
import com.dougie.core.tool.CalendarQueryTool
import com.dougie.core.tool.ClipboardReadTool
import com.dougie.core.tool.ClipboardWriteTool
import com.dougie.core.tool.InMemoryScreenFrameStore
import com.dougie.core.tool.LocationTool
import com.dougie.core.tool.ScreenCaptureTool
import com.dougie.core.tool.ScreenMatchTool
import com.dougie.core.tool.SpeechInputTool
import com.dougie.core.tool.SpeechOutputTool
import com.dougie.core.tool.IntentClassifierTool
import com.dougie.core.tool.PreferOfflineTtsPort
import com.dougie.core.tool.SherpaTtsEngine
import com.dougie.core.tool.TtsModelLayout
import com.dougie.core.tool.SystemTimeTool
import com.dougie.data.memory.RoomMemoryStore
import com.dougie.data.preferences.PreferenceStore
import com.dougie.data.tasks.DougieTaskStores
import com.dougie.tool.system.AndroidAppIntentPort
import com.dougie.tool.system.AndroidCalendarPort
import com.dougie.tool.system.AndroidClipboardPort
import com.dougie.tool.system.AndroidIntentPort
import com.dougie.tool.system.AndroidLocationPort
import com.dougie.tool.system.AndroidScreenCapturePort
import com.dougie.tool.system.AndroidSpeechPort
import com.dougie.tool.system.AndroidSystemTtsEngine
import com.dougie.tool.system.DeviceBatteryTool
import com.dougie.tool.system.SherpaJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class DougieApplication : Application() {
    lateinit var taskManager: TaskManager
        private set
    lateinit var preferenceStore: PreferenceStore
        private set
    lateinit var memoryStore: MemoryStore
        private set
    lateinit var taskStores: DougieTaskStores
        private set
    lateinit var permissionUsage: PermissionUsageTracker
        private set

    private val foregroundTracker = AppForegroundTracker()
    private lateinit var tools: LinkedHashMap<String, AgentTool>

    override fun onCreate() {
        super.onCreate()
        preferenceStore = PreferenceStore(this)
        memoryStore = RoomMemoryStore(this)
        taskStores = DougieTaskStores(this)
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
        val locationPort = AndroidLocationPort(this) {
            permissionUsage.mark(AndroidPermissions.ACCESS_COARSE_LOCATION)
        }
        val speechPort = AndroidSpeechPort(
            context = this,
            isForeground = { foregroundTracker.foreground },
            onUsed = { permissionUsage.mark(AndroidPermissions.RECORD_AUDIO) },
        )
        val ttsDir = File(filesDir, TtsModelLayout.DIR)
        val ttsPort = PreferOfflineTtsPort(
            offline = SherpaTtsEngine(
                modelDir = ttsDir,
                nativeAvailable = SherpaJni::isAvailable,
                speakNative = SherpaJni::speak,
            ),
            fallback = AndroidSystemTtsEngine(this),
        )
        val intentPort = AndroidIntentPort(this)
        val screenStore = InMemoryScreenFrameStore()
        val screenPort = AndroidScreenCapturePort(
            context = this,
            isForeground = { foregroundTracker.foreground },
            onUsed = { permissionUsage.mark(SCREEN_CAPTURE_USAGE_KEY) },
        )
        val appIntentPort = AndroidAppIntentPort(
            context = this,
            isForeground = { foregroundTracker.foreground },
        )
        tools = linkedMapOf(
            "battery" to DeviceBatteryTool(this),
            "time" to SystemTimeTool(),
            CalendarQueryTool.NAME to CalendarQueryTool(calendarPort),
            CalendarCreateTool.NAME to CalendarCreateTool(calendarPort, taskStores.idempotencyStore),
            ClipboardReadTool.NAME to ClipboardReadTool(clipboardPort),
            ClipboardWriteTool.NAME to ClipboardWriteTool(clipboardPort),
            LocationTool.NAME to LocationTool(locationPort),
            ScreenCaptureTool.NAME to ScreenCaptureTool(screenPort, screenStore),
            ScreenMatchTool.NAME to ScreenMatchTool(screenStore),
            AppIntentTool.NAME to AppIntentTool(appIntentPort, taskStores.idempotencyStore),
            SpeechInputTool.NAME to SpeechInputTool(speechPort),
            SpeechOutputTool.NAME to SpeechOutputTool(ttsPort),
            IntentClassifierTool.NAME to IntentClassifierTool(intentPort),
        )
        ChannelTools.register(tools, { ChannelHooks.hasChannelConsent(this) }, taskStores.idempotencyStore)
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
                auditLog = taskStores.auditLog,
            ),
            dispatcher = dispatcher,
            taskStore = taskStores.taskStore,
        )
        runBlocking {
            recoverInterrupted(taskStores.taskStore)?.let { taskManager.seed(it) }
        }
    }

    fun refreshChannelTools() {
        ChannelTools.register(tools, { ChannelHooks.hasChannelConsent(this) }, taskStores.idempotencyStore)
    }

    companion object {
        const val CLIPBOARD_USAGE_KEY = "clipboard"
        const val SCREEN_CAPTURE_USAGE_KEY = "screen_capture"
    }
}
