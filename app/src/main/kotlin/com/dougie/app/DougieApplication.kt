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
import com.dougie.core.tool.OpenAppEntries
import com.dougie.core.tool.PreferOfflineTtsPort
import com.dougie.core.tool.SherpaTtsEngine
import com.dougie.core.tool.ModelInstaller
import com.dougie.core.tool.TtsModelLayout
import com.dougie.core.tool.TtsVoices
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
import com.dougie.tool.system.OkHttpModelGet
import com.dougie.tool.system.SherpaJni
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dougie.core.model.AgentException
import com.dougie.core.model.UserFacingErrors
import com.dougie.core.tool.ScreenFrame
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
    lateinit var modelInstaller: ModelInstaller
        private set
    lateinit var screenFrameStore: InMemoryScreenFrameStore
        private set
    lateinit var screenCapturePort: AndroidScreenCapturePort
        private set
    lateinit var speechPort: AndroidSpeechPort
        private set
    lateinit var attachmentSession: ChatAttachmentSession
        private set
    @Volatile
    var overlayAttachError: String? = null
    private val composerEpochState = MutableStateFlow(0)
    val composerEpoch: StateFlow<Int> = composerEpochState.asStateFlow()
    private lateinit var taskProgressNotifier: TaskProgressNotifier

    private val foregroundTracker = AppForegroundTracker()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tools: LinkedHashMap<String, AgentTool>

    override fun onCreate() {
        super.onCreate()
        ChannelHooks.seedBundledModels(this)
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
        modelInstaller = ModelInstaller(OkHttpModelGet(OkHttpModelGet.client(http)))
        val calendarPort = AndroidCalendarPort(this) { permissionUsage.mark(it) }
        val clipboardPort = AndroidClipboardPort(
            context = this,
            isForeground = { foregroundTracker.foreground },
            onUsed = { permissionUsage.mark(CLIPBOARD_USAGE_KEY) },
        )
        val locationPort = AndroidLocationPort(this) {
            permissionUsage.mark(AndroidPermissions.ACCESS_COARSE_LOCATION)
        }
        val ttsDir = File(filesDir, TtsModelLayout.DIR)
        val ttsPort = PreferOfflineTtsPort(
            offline = SherpaTtsEngine(
                modelDir = ttsDir,
                nativeAvailable = SherpaJni::isAvailable,
                speakNative = { dir, text ->
                    SherpaJni.speak(
                        dir,
                        text,
                        TtsVoices.clamp(preferenceStore.settings.value.ttsSpeakerId),
                    )
                },
                stopNative = SherpaJni::stopSpeak,
            ),
            fallback = AndroidSystemTtsEngine(this),
        )
        speechPort = AndroidSpeechPort(
            context = this,
            isForeground = { foregroundTracker.foreground },
            onUsed = { permissionUsage.mark(AndroidPermissions.RECORD_AUDIO) },
            replyTts = ttsPort,
        )
        val speechPort = this.speechPort
        val intentPort = AndroidIntentPort(this)
        screenFrameStore = InMemoryScreenFrameStore()
        screenCapturePort = AndroidScreenCapturePort(
            context = this,
            isForeground = { foregroundTracker.foreground },
            onUsed = { permissionUsage.mark(SCREEN_CAPTURE_USAGE_KEY) },
        )
        val screenStore = screenFrameStore
        val screenPort = screenCapturePort
        attachmentSession = ChatAttachmentSession(screenStore)
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
            AppIntentTool.NAME to AppIntentTool(
                appIntentPort,
                taskStores.idempotencyStore,
                allowedPackages = {
                    OpenAppEntries.packages(OpenAppEntries.parse(preferenceStore.openAppsJson.value))
                },
            ),
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
                    maxTokens = prefs.maxTokens,
                )
            },
            toolDescriptors = { tools.values.map { it.descriptor } },
            allowCloud = { preferenceStore.settings.value.allowCloud },
            attachmentJpeg = { attachmentSession.jpeg(it) },
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
                intentPort = intentPort,
                openAppEntries = { OpenAppEntries.parse(preferenceStore.openAppsJson.value) },
            ),
            dispatcher = dispatcher,
            taskStore = taskStores.taskStore,
            screenFrames = screenStore,
            onTaskFinished = { finishComposerAfterTask() },
        )
        runBlocking {
            recoverInterrupted(taskStores.taskStore)?.let { taskManager.seed(it) }
        }
        TaskProgressNotifier(this).also { taskProgressNotifier = it }
            .start(appScope, taskManager.task)
        ChannelHooks.syncOverlay(this)
        ScheduleAlarms.sync(this)
    }

    fun republishTaskNotice() {
        taskProgressNotifier.apply(taskManager.task.value)
    }

    suspend fun pinCurrentScreen(requireForeground: Boolean = true): Result<ScreenFrame> {
        val port = screenCapturePort
        if (requireForeground && !port.isAppForeground()) {
            return Result.failure(AgentException(UserFacingErrors.SCREEN_NOT_FOREGROUND))
        }
        if (!port.hasProjectionConsent()) {
            return Result.failure(AgentException(UserFacingErrors.PERMISSION_DENIED))
        }
        return try {
            val captured = port.capture()
            attachmentSession.addScreen(captured.frame, captured.previewJpeg).map { captured.frame }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AgentException) {
            Result.failure(e)
        } catch (_: Exception) {
            Result.failure(AgentException(UserFacingErrors.TOOL_FAILED))
        }
    }

    private fun finishComposerAfterTask() {
        val kept = ShortcutScreenPin.adoptIntoComposer(
            taskManager.task.value,
            attachmentSession,
            screenFrameStore,
        )
        if (!kept) {
            attachmentSession.releaseAfterTask()
        }
        composerEpochState.value += 1
    }

    fun refreshChannelTools() {
        ChannelTools.register(tools, { ChannelHooks.hasChannelConsent(this) }, taskStores.idempotencyStore)
    }

    companion object {
        const val CLIPBOARD_USAGE_KEY = "clipboard"
        const val SCREEN_CAPTURE_USAGE_KEY = "screen_capture"
    }
}
