package com.dougie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dougie.feature.chat.ChatRoute
import com.dougie.feature.chat.ChatViewModel
import com.dougie.feature.chat.DougieColors
import com.dougie.feature.history.HistoryRoute
import com.dougie.feature.history.HistoryViewModel
import com.dougie.feature.memory.MemoryRoute
import com.dougie.feature.memory.MemoryViewModel
import com.dougie.feature.settings.SettingsRoute
import com.dougie.feature.settings.SettingsViewModel

private enum class AppRoute { Chat, Settings, Memory, Permissions, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DougieApplication
        setContent {
            ChannelHooks.Root {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DougieColors.Surface,
                ) {
                var route by remember { mutableStateOf(AppRoute.Chat) }
                val prefs by app.preferenceStore.settings.collectAsStateWithLifecycle()
                when (route) {
                    AppRoute.Chat -> {
                        val viewModel: ChatViewModel = viewModel(
                            factory = ChatViewModel.Factory(app.taskManager),
                        )
                        ChatRoute(
                            viewModel = viewModel,
                            allowCloud = prefs.allowCloud,
                            onOpenSettings = { route = AppRoute.Settings },
                            onOpenMemory = { route = AppRoute.Memory },
                            onOpenPermissions = { route = AppRoute.Permissions },
                            onOpenHistory = { route = AppRoute.History },
                        )
                    }
                    AppRoute.Settings -> {
                        val viewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(app.preferenceStore),
                        )
                        SettingsRoute(
                            viewModel = viewModel,
                            onBack = { route = AppRoute.Chat },
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
}
