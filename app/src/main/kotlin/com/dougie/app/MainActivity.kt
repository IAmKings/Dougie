package com.dougie.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dougie.core.model.AndroidPermissions
import androidx.lifecycle.lifecycleScope
import com.dougie.core.model.AgentException
import com.dougie.core.model.TaskStatus
import com.dougie.core.model.UserFacingErrors
import com.dougie.feature.chat.ChatRoute
import com.dougie.feature.debug.DebugRoute
import com.dougie.feature.debug.DebugViewModel
import com.dougie.feature.chat.ChatViewModel
import com.dougie.feature.chat.DougieColors
import com.dougie.feature.chat.ScreenAttachUi
import com.dougie.feature.chat.intelligenceMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dougie.feature.history.HistoryRoute
import com.dougie.feature.history.HistoryViewModel
import com.dougie.feature.memory.MemoryRoute
import com.dougie.feature.memory.MemoryViewModel
import com.dougie.feature.settings.SettingsRoute
import com.dougie.feature.settings.SettingsViewModel

private enum class AppRoute { Chat, Settings, Memory, Permissions, History, Debug }

class MainActivity : ComponentActivity() {
    private val routeState = mutableStateOf(AppRoute.Chat)
    private val chatDraftState = mutableStateOf("")
    private val screenAttachState = mutableStateOf<ScreenAttachUi?>(null)
    private val screenAttachErrorState = mutableStateOf<String?>(null)
    private val screenAttachingState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_CHAT_DRAFT)?.let { chatDraftState.value = it }
        if (savedInstanceState == null) {
            applyChatIntent(intent)
        }
        enableEdgeToEdge()
        val app = application as DougieApplication
        setContent {
            ChannelHooks.Root {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DougieColors.Surface,
                ) {
                var route by routeState
                var chatDraft by chatDraftState
                var screenAttach by screenAttachState
                var screenAttachError by screenAttachErrorState
                var screenAttaching by screenAttachingState
                val prefs by app.preferenceStore.settings.collectAsStateWithLifecycle()
                val task by app.taskManager.task.collectAsStateWithLifecycle()
                val notifyLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) app.republishTaskNotice()
                }
                LaunchedEffect(task?.status) {
                    if (Build.VERSION.SDK_INT < 33) return@LaunchedEffect
                    if (!isTaskBusy(task)) return@LaunchedEffect
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        AndroidPermissions.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) return@LaunchedEffect
                    if (NotificationPermissionGate.tryMarkRequested()) {
                        notifyLauncher.launch(AndroidPermissions.POST_NOTIFICATIONS)
                    }
                }
                when (route) {
                    AppRoute.Chat -> {
                        val viewModel: ChatViewModel = viewModel(
                            factory = ChatViewModel.Factory(app.taskManager),
                        )
                        ChatRoute(
                            viewModel = viewModel,
                            allowCloud = prefs.allowCloud,
                            intelligenceMark = intelligenceMark(
                                allowCloud = prefs.allowCloud,
                                apiKeyConfigured = prefs.apiKey.isNotBlank(),
                                // Intent GGUF is not a chat LLM; keep false until a local chat model exists.
                                localLlmReady = false,
                                failedLastError = task
                                    ?.takeIf { it.status == TaskStatus.FAILED }
                                    ?.lastError,
                            ),
                            composerText = chatDraft,
                            onComposerChange = { chatDraft = it },
                            attachedScreen = screenAttach,
                            attachedError = screenAttachError,
                            attachingScreen = screenAttaching,
                            onAttachScreen = { attachCurrentScreen() },
                            onClearAttachedScreen = {
                                screenAttach = null
                                screenAttachError = null
                            },
                            onDismissAttachedScreen = {
                                screenAttach = null
                                screenAttachError = null
                                app.screenFrameStore.clearPin()
                            },
                            onOpenSettings = { route = AppRoute.Settings },
                            onOpenMemory = { route = AppRoute.Memory },
                            onOpenPermissions = { route = AppRoute.Permissions },
                            onOpenHistory = { route = AppRoute.History },
                        )
                    }
                    AppRoute.Settings -> {
                        val viewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                app.preferenceStore,
                                app.modelInstaller,
                                app.filesDir,
                                app.cacheDir,
                                AppOfflineModels.offers,
                                probe = AppOfflineModelProbe.create(app),
                                tree = ExternalModelTreeImpl(
                                    app,
                                    uriProvider = { app.preferenceStore.settings.value.modelTreeUri },
                                ),
                            ),
                        )
                        val treePicker = rememberLauncherForActivityResult(
                            PersistableOpenDocumentTree(),
                        ) { uri ->
                            if (uri == null) return@rememberLauncherForActivityResult
                            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            try {
                                val previous = app.preferenceStore.settings.value.modelTreeUri
                                contentResolver.takePersistableUriPermission(uri, flags)
                                if (previous.isNotBlank() && previous != uri.toString()) {
                                    runCatching {
                                        contentResolver.releasePersistableUriPermission(
                                            Uri.parse(previous),
                                            flags,
                                        )
                                    }
                                }
                                viewModel.setModelTreeUri(uri.toString())
                            } catch (_: SecurityException) {
                                // Persistable grant missing; scan will ask the user to pick again.
                            }
                        }
                        SettingsRoute(
                            viewModel = viewModel,
                            onBack = { route = AppRoute.Chat },
                            onOpenDebug = { route = AppRoute.Debug },
                            onPickModelTree = { treePicker.launch(null) },
                            shortcutLayer = { ChannelHooks.ShortcutLayerSettings() },
                            scheduleLayer = { ScheduleSettings() },
                        )
                    }
                    AppRoute.Debug -> {
                        val viewModel: DebugViewModel = viewModel(
                            factory = DebugViewModel.Factory(
                                app.taskManager,
                                app.taskStores.auditLog,
                            ),
                        )
                        DebugRoute(
                            viewModel = viewModel,
                            onBack = { route = AppRoute.Settings },
                        )
                    }
                    AppRoute.Memory -> {
                        val viewModel: MemoryViewModel = viewModel(
                            factory = MemoryViewModel.Factory(app.memoryStore, app.preferenceStore),
                        )
                        MemoryRoute(
                            viewModel = viewModel,
                            onOpenChat = { route = AppRoute.Chat },
                            onOpenSettings = { route = AppRoute.Settings },
                            onOpenHistory = { route = AppRoute.History },
                        )
                    }
                    AppRoute.History -> {
                        val viewModel: HistoryViewModel = viewModel(
                            factory = HistoryViewModel.Factory(app.taskStores.taskStore),
                        )
                        HistoryRoute(
                            viewModel = viewModel,
                            onOpenChat = { route = AppRoute.Chat },
                            onOpenMemory = { route = AppRoute.Memory },
                            onOpenSettings = { route = AppRoute.Settings },
                        )
                    }
                    AppRoute.Permissions -> {
                        val viewModel: com.dougie.feature.permissions.PermissionsViewModel = viewModel(
                            factory = com.dougie.feature.permissions.PermissionsViewModel.Factory(
                                app,
                                app.permissionUsage::lastUsedMs,
                                projectionGranted = {
                                    com.dougie.tool.system.ScreenCaptureConsentStore.hasToken()
                                },
                            ),
                        )
                        com.dougie.feature.permissions.PermissionsRoute(
                            viewModel = viewModel,
                            onBack = { route = AppRoute.Chat },
                            onProjectionConsent = { resultCode, data ->
                                com.dougie.tool.system.ScreenCaptureConsentStore.save(resultCode, data)
                            },
                        )
                    }
                }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CHAT_DRAFT, chatDraftState.value)
    }

    override fun onResume() {
        super.onResume()
        val app = application as DougieApplication
        ChannelHooks.syncOverlay(this)
        if (isTaskBusy(app.taskManager.task.value)) {
            app.republishTaskNotice()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyChatIntent(intent)
    }

    private fun attachCurrentScreen() {
        if (screenAttachingState.value) return
        val app = application as DougieApplication
        screenAttachingState.value = true
        screenAttachErrorState.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { app.pinCurrentScreen() }
            screenAttachingState.value = false
            result.fold(
                onSuccess = { frame ->
                    screenAttachState.value = ScreenAttachUi(
                        captureId = frame.id,
                        width = frame.width,
                        height = frame.height,
                    )
                    screenAttachErrorState.value = null
                },
                onFailure = { error ->
                    screenAttachState.value = null
                    screenAttachErrorState.value = (error as? AgentException)?.userMessage
                        ?: UserFacingErrors.TOOL_FAILED
                },
            )
        }
    }

    private fun applyChatIntent(intent: Intent?) {
        if (ChatLaunch.requestsChat(intent)) {
            routeState.value = AppRoute.Chat
        }
        ChatLaunch.scheduleId(intent)?.let { applyScheduleDraft(it) }
    }

    private fun applyScheduleDraft(id: String) {
        ScheduleStore(filesDir).draftForNotificationTap(id)?.let { chatDraftState.value = it }
    }

    companion object {
        private const val KEY_CHAT_DRAFT = "dougie.chat.draft"
    }
}

private class PersistableOpenDocumentTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
    }
}
