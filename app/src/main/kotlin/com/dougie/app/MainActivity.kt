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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.dougie.core.model.AttachmentKind
import com.dougie.core.model.AttachmentLimits
import com.dougie.feature.chat.ChatAttachmentUi
import com.dougie.feature.chat.toUi
import java.io.File
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
import com.dougie.feature.chat.intelligenceMark
import com.dougie.core.tool.SpeechHold
import com.dougie.feature.chat.appendVoiceTranscript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dougie.core.tool.TtsSpeakResult
import com.dougie.feature.history.HistoryRoute
import com.dougie.feature.history.HistoryViewModel
import com.dougie.feature.memory.MemoryRoute
import com.dougie.feature.memory.MemoryViewModel
import com.dougie.feature.settings.OpenAppsRoute
import com.dougie.feature.settings.OpenAppsViewModel
import com.dougie.feature.settings.SettingsRoute
import com.dougie.feature.settings.SettingsViewModel

private enum class AppRoute { Chat, Settings, Memory, Permissions, History, Debug, OpenApps }

class MainActivity : ComponentActivity() {
    private val routeState = mutableStateOf(AppRoute.Chat)
    private val chatDraftState = mutableStateOf("")
    private val attachmentsState = mutableStateOf<List<ChatAttachmentUi>>(emptyList())
    private val attachErrorState = mutableStateOf<String?>(null)
    private val attachingState = mutableStateOf(false)
    private val previewState = mutableStateOf<ImageBitmap?>(null)
    private val holdingMicState = mutableStateOf(false)
    private val voiceTranscribingState = mutableStateOf(false)
    private val voiceUsedThisDraftState = mutableStateOf(false)
    private val speakingReplyState = mutableStateOf(false)
    private val asrReadyState = mutableStateOf(false)
    private val ttsReadyState = mutableStateOf(false)
    private var cameraOutput: File? = null
    private var holdActive = false
    private var ignoreUntilUp = false
    private var holdLimitJob: Job? = null
    private var speakJob: Job? = null
    private var previousReplyStatus: TaskStatus? = null
    private var spokenReplyTaskId: String? = null

    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentLimits.MAX),
    ) { uris -> ingestGallery(uris) }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> ingestCamera(ok) }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCamera() else attachErrorState.value = UserFacingErrors.PERMISSION_DENIED
    }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) attachErrorState.value = UserFacingErrors.PERMISSION_DENIED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_CHAT_DRAFT)?.let { chatDraftState.value = it }
        voiceUsedThisDraftState.value =
            savedInstanceState?.getBoolean(KEY_VOICE_USED_THIS_DRAFT, false) == true
        if (savedInstanceState == null) {
            applyChatIntent(intent)
        }
        enableEdgeToEdge()
        val app = application as DougieApplication
        refreshVoicePacks()
        setContent {
            ChannelHooks.Root {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DougieColors.Surface,
                ) {
                var route by routeState
                var chatDraft by chatDraftState
                var attachments by attachmentsState
                var attachError by attachErrorState
                var attaching by attachingState
                var previewImage by previewState
                var holdingMic by holdingMicState
                var voiceTranscribing by voiceTranscribingState
                var voiceUsedThisDraft by voiceUsedThisDraftState
                var speakingReply by speakingReplyState
                var asrReady by asrReadyState
                var ttsReady by ttsReadyState
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
                LaunchedEffect(task?.taskId, task?.status, task?.speakReply, task?.finalAnswer) {
                    val current = task
                    if (ReplyPlayback.shouldSpeak(current, previousReplyStatus, spokenReplyTaskId)) {
                        spokenReplyTaskId = current!!.taskId
                        startReplySpeak(current.finalAnswer.orEmpty().trim())
                    }
                    previousReplyStatus = current?.status
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
                            attachments = attachments,
                            attachedError = attachError,
                            attaching = attaching,
                            onCaptureScreen = { attachCurrentScreen() },
                            onPickGallery = { pickGallery() },
                            onTakePhoto = { requestCamera() },
                            onRemoveAttachment = { id ->
                                app.attachmentSession.remove(id)
                                previewImage = null
                                syncChips()
                            },
                            onPreviewAttachment = { id -> previewAttachment(id) },
                            previewImage = previewImage,
                            onClosePreview = { previewImage = null },
                            onAttachmentsConsumed = {
                                app.attachmentSession.clearComposer()
                                previewImage = null
                                syncChips()
                            },
                            onMicDown = { onMicDown() },
                            onMicUp = { onMicUp() },
                            holdingMic = holdingMic,
                            transcribingVoice = voiceTranscribing,
                            speakReplyOnSend = voiceUsedThisDraft,
                            speakingReply = speakingReply,
                            asrReady = asrReady,
                            ttsReady = ttsReady,
                            onStopReply = { stopReplySpeak() },
                            onSpeakReply = { startReplySpeak(it) },
                            onSpeakReplyConsumed = {
                                voiceUsedThisDraft = false
                                if (attachError == UserFacingErrors.TTS_REPLY_UNAVAILABLE) {
                                    attachError = null
                                }
                            },
                            overlayShortcutHint = ChannelHooks.screenShortcutHint(this@MainActivity, task),
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
                            onOpenOpenApps = { route = AppRoute.OpenApps },
                            onPickModelTree = { treePicker.launch(null) },
                            shortcutLayer = { ChannelHooks.ShortcutLayerSettings() },
                            scheduleLayer = { ScheduleSettings() },
                        )
                    }
                    AppRoute.OpenApps -> {
                        val viewModel: OpenAppsViewModel = viewModel(
                            factory = OpenAppsViewModel.Factory(
                                app.preferenceStore,
                                listLaunchers = { LauncherApps.list(app) },
                            ),
                        )
                        OpenAppsRoute(
                            viewModel = viewModel,
                            onBack = { route = AppRoute.Settings },
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
                                extraItems = {
                                    listOfNotNull(ChannelHooks.overlayPermissionItem(this@MainActivity))
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
        outState.putBoolean(KEY_VOICE_USED_THIS_DRAFT, voiceUsedThisDraftState.value)
    }

    override fun onStop() {
        super.onStop()
        stopReplySpeak()
    }

    override fun onResume() {
        super.onResume()
        val app = application as DougieApplication
        ChannelHooks.syncOverlay(this)
        refreshVoicePacks()
        if (isTaskBusy(app.taskManager.task.value)) {
            app.republishTaskNotice()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyChatIntent(intent)
    }

    private fun refreshVoicePacks() {
        val app = application as DougieApplication
        asrReadyState.value = app.speechPort.isModelPresent() && app.speechPort.isEngineReady()
        ttsReadyState.value = app.speechPort.isReplyTtsReady()
    }

    private fun stopReplySpeak() {
        speakJob?.cancel()
        speakJob = null
        (application as DougieApplication).speechPort.stopPlayback()
        speakingReplyState.value = false
    }

    private fun startReplySpeak(text: String) {
        val app = application as DougieApplication
        if (!app.speechPort.isReplyTtsReady()) {
            attachErrorState.value = UserFacingErrors.TTS_REPLY_UNAVAILABLE
            return
        }
        speakJob?.cancel()
        app.speechPort.stopPlayback()
        if (attachErrorState.value == UserFacingErrors.TTS_REPLY_UNAVAILABLE) {
            attachErrorState.value = null
        }
        speakingReplyState.value = true
        speakJob = lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { app.speechPort.speakReply(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                TtsSpeakResult(
                    ok = false,
                    backend = "offline",
                    error = UserFacingErrors.TTS_REPLY_UNAVAILABLE,
                )
            }
            if (!isActive) return@launch
            speakingReplyState.value = false
            if (!result.ok && !result.stopped) {
                attachErrorState.value = UserFacingErrors.TTS_REPLY_UNAVAILABLE
            }
        }
    }

    private fun onMicDown() {
        if (ignoreUntilUp || holdActive || attachingState.value || speakingReplyState.value) return
        val app = application as DougieApplication
        if (!app.speechPort.isAppForeground()) {
            attachErrorState.value = UserFacingErrors.SPEECH_NOT_FOREGROUND
            return
        }
        if (!app.speechPort.isModelPresent()) {
            attachErrorState.value = UserFacingErrors.SPEECH_MODEL_MISSING
            return
        }
        if (!app.speechPort.isEngineReady()) {
            attachErrorState.value = UserFacingErrors.SPEECH_ENGINE_NOT_READY
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            AndroidPermissions.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            micPermission.launch(AndroidPermissions.RECORD_AUDIO)
            return
        }
        if (!app.speechPort.holdRecorder.start()) return
        holdActive = true
        holdingMicState.value = true
        attachErrorState.value = null
        holdLimitJob?.cancel()
        holdLimitJob = lifecycleScope.launch {
            delay(SpeechHold.MAX_MS.toLong())
            finishHold(fromLimit = true)
        }
    }

    private fun onMicUp() {
        if (ignoreUntilUp) {
            ignoreUntilUp = false
            return
        }
        finishHold(fromLimit = false)
    }

    private fun finishHold(fromLimit: Boolean) {
        if (!holdActive) return
        holdActive = false
        holdingMicState.value = false
        holdLimitJob?.cancel()
        holdLimitJob = null
        if (fromLimit) ignoreUntilUp = true
        val app = application as DougieApplication
        attachingState.value = true
        voiceTranscribingState.value = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val utterance = app.speechPort.holdRecorder.stop()
                if (utterance.samples.isEmpty()) {
                    return@withContext Result.failure(AgentException(UserFacingErrors.SPEECH_EMPTY))
                }
                val text = app.speechPort.transcribe(utterance)
                if (text.isBlank()) {
                    Result.failure(AgentException(UserFacingErrors.SPEECH_EMPTY))
                } else {
                    Result.success(text)
                }
            }
            attachingState.value = false
            voiceTranscribingState.value = false
            result.fold(
                onSuccess = { spoken ->
                    chatDraftState.value = appendVoiceTranscript(chatDraftState.value, spoken)
                    voiceUsedThisDraftState.value = true
                    attachErrorState.value = null
                },
                onFailure = { error ->
                    attachErrorState.value = (error as? AgentException)?.userMessage
                        ?: UserFacingErrors.TOOL_FAILED
                },
            )
        }
    }

    private fun attachCurrentScreen() {
        if (attachingState.value) return
        val app = application as DougieApplication
        if (app.attachmentSession.remaining() == 0) {
            attachErrorState.value = UserFacingErrors.ATTACHMENTS_FULL
            return
        }
        attachingState.value = true
        attachErrorState.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { app.pinCurrentScreen() }
            attachingState.value = false
            result.fold(
                onSuccess = {
                    attachErrorState.value = null
                    syncChips()
                },
                onFailure = { error ->
                    attachErrorState.value = (error as? AgentException)?.userMessage
                        ?: UserFacingErrors.TOOL_FAILED
                    syncChips()
                },
            )
        }
    }

    private fun pickGallery() {
        val app = application as DougieApplication
        if (app.attachmentSession.remaining() == 0) {
            attachErrorState.value = UserFacingErrors.ATTACHMENTS_FULL
            return
        }
        galleryPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    private fun requestCamera() {
        val app = application as DougieApplication
        if (app.attachmentSession.remaining() == 0) {
            attachErrorState.value = UserFacingErrors.ATTACHMENTS_FULL
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            AndroidPermissions.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else cameraPermission.launch(AndroidPermissions.CAMERA)
    }

    private fun startCamera() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("shot", ".jpg", dir)
        cameraOutput = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePicture.launch(uri)
    }

    private fun ingestGallery(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = application as DougieApplication
        attachingState.value = true
        attachErrorState.value = null
        lifecycleScope.launch {
            val overflow = withContext(Dispatchers.Default) {
                var full = false
                for (uri in uris) {
                    if (app.attachmentSession.remaining() == 0) {
                        full = true
                        break
                    }
                    val raw = runCatching {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull() ?: continue
                    val jpeg = ChatImageCodec.jpegFromGalleryBytes(raw) ?: continue
                    val added = app.attachmentSession.addPhoto(
                        AttachmentKind.GALLERY,
                        jpeg.first,
                        jpeg.second,
                        jpeg.third,
                    )
                    if (added.isFailure) {
                        full = true
                        break
                    }
                }
                full
            }
            attachingState.value = false
            if (overflow) attachErrorState.value = UserFacingErrors.ATTACHMENTS_FULL
            syncChips()
        }
    }

    private fun ingestCamera(ok: Boolean) {
        val file = cameraOutput
        cameraOutput = null
        if (!ok || file == null) {
            file?.delete()
            return
        }
        val app = application as DougieApplication
        attachingState.value = true
        attachErrorState.value = null
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val raw = runCatching { file.readBytes() }.getOrNull()
                file.delete()
                if (raw == null) {
                    return@withContext Result.failure(AgentException(UserFacingErrors.TOOL_FAILED))
                }
                val jpeg = ChatImageCodec.jpegFromGalleryBytes(raw)
                    ?: return@withContext Result.failure(AgentException(UserFacingErrors.TOOL_FAILED))
                app.attachmentSession.addPhoto(
                    AttachmentKind.CAMERA,
                    jpeg.first,
                    jpeg.second,
                    jpeg.third,
                )
            }
            attachingState.value = false
            result.fold(
                onSuccess = { attachErrorState.value = null },
                onFailure = { error ->
                    attachErrorState.value = (error as? AgentException)?.userMessage
                        ?: UserFacingErrors.TOOL_FAILED
                },
            )
            syncChips()
        }
    }

    private fun previewAttachment(id: String) {
        val app = application as DougieApplication
        val meta = app.attachmentSession.snapshot().find { it.id == id } ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                when (meta.kind) {
                    AttachmentKind.SCREEN ->
                        app.attachmentSession.jpeg(id)?.let { ChatImageCodec.jpegPreview(it) }
                            ?: app.screenFrameStore.get(id)?.let { ChatImageCodec.grayPreview(it) }
                    AttachmentKind.GALLERY, AttachmentKind.CAMERA ->
                        app.attachmentSession.jpeg(id)?.let { ChatImageCodec.jpegPreview(it) }
                }
            }
            previewState.value = bitmap?.asImageBitmap()
        }
    }

    private fun syncChips() {
        val app = application as DougieApplication
        attachmentsState.value = app.attachmentSession.snapshot().map { it.toUi() }
    }

    private fun applyChatIntent(intent: Intent?) {
        if (ChatLaunch.openPermissions(intent)) {
            routeState.value = AppRoute.Permissions
        } else if (ChatLaunch.requestsChat(intent)) {
            routeState.value = AppRoute.Chat
        }
        ChatLaunch.scheduleId(intent)?.let { applyScheduleDraft(it) }
        if (ChatLaunch.applyPinnedScreen(intent)) {
            applyPinnedScreenChip()
        }
    }

    private fun applyPinnedScreenChip() {
        val app = application as DougieApplication
        syncChips()
        val error = app.overlayAttachError
        if (error != null) {
            attachErrorState.value = error
        } else if (attachmentsState.value.isEmpty()) {
            attachErrorState.value = UserFacingErrors.TOOL_FAILED
        } else {
            attachErrorState.value = null
        }
    }

    private fun applyScheduleDraft(id: String) {
        ScheduleStore(filesDir).draftForNotificationTap(id)?.let { chatDraftState.value = it }
    }

    companion object {
        private const val KEY_CHAT_DRAFT = "dougie.chat.draft"
        private const val KEY_VOICE_USED_THIS_DRAFT = "dougie.chat.voiceUsedThisDraft"
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
