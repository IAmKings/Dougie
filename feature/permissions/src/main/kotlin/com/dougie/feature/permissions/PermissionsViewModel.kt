package com.dougie.feature.permissions

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dougie.core.model.AndroidPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionUiState(
    val items: List<PermissionItem> = emptyList(),
)

enum class PermissionKind {
    RUNTIME,
    CLIPBOARD,
    SCREEN_CAPTURE,
}

data class PermissionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val runtimePermission: String?,
    val granted: Boolean,
    val riskLabel: String,
    val lastUsedLabel: String,
    val highRisk: Boolean = false,
    val kind: PermissionKind = PermissionKind.RUNTIME,
)

class PermissionsViewModel(
    application: Application,
    private val lastUsedMs: (String) -> Long?,
    private val projectionGranted: () -> Boolean = { false },
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { PermissionUiState(items = buildItems()) }
        }
    }

    private fun buildItems(): List<PermissionItem> {
        val items = mutableListOf(
            item(
                id = "calendar_read",
                title = "读取日历",
                subtitle = "查询今后的日程摘要",
                permission = AndroidPermissions.READ_CALENDAR,
                riskLabel = "L1",
            ),
            item(
                id = "calendar_write",
                title = "写入日历",
                subtitle = "创建日程前会弹出确认卡片",
                permission = AndroidPermissions.WRITE_CALENDAR,
                riskLabel = "L2",
                highRisk = true,
            ),
            item(
                id = "clipboard",
                title = "剪贴板",
                subtitle = "读取仅在 Dougie 位于前台时可用；写入需确认",
                permission = null,
                usageKey = CLIPBOARD_USAGE_KEY,
                riskLabel = "L1 / L2",
                granted = true,
                kind = PermissionKind.CLIPBOARD,
            ),
            item(
                id = "location",
                title = "粗略位置",
                subtitle = "仅申请粗略定位，用于回答「我在哪」一类问题",
                permission = AndroidPermissions.ACCESS_COARSE_LOCATION,
                riskLabel = "L1",
            ),
            item(
                id = "microphone",
                title = "麦克风",
                subtitle = "仅在前台本地转写，音频不出设备、不入日志",
                permission = AndroidPermissions.RECORD_AUDIO,
                riskLabel = "L1",
            ),
            item(
                id = "screen_capture",
                title = "屏幕截取",
                subtitle = "系统投屏授权后，截图只留在本机内存，不会发给模型",
                permission = null,
                usageKey = SCREEN_CAPTURE_USAGE_KEY,
                riskLabel = "L1",
                granted = projectionGranted(),
                kind = PermissionKind.SCREEN_CAPTURE,
            ),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            items += item(
                id = "notifications",
                title = "通知",
                subtitle = "任务进行时显示状态，不含对话原文",
                permission = AndroidPermissions.POST_NOTIFICATIONS,
                riskLabel = "L0",
            )
        }
        return items
    }

    private fun item(
        id: String,
        title: String,
        subtitle: String,
        permission: String?,
        riskLabel: String,
        highRisk: Boolean = false,
        usageKey: String? = permission,
        granted: Boolean? = null,
        kind: PermissionKind = if (permission != null) PermissionKind.RUNTIME else PermissionKind.CLIPBOARD,
    ): PermissionItem {
        val isGranted = granted ?: permission?.let { checkGranted(it) } ?: false
        return PermissionItem(
            id = id,
            title = title,
            subtitle = subtitle,
            runtimePermission = permission,
            granted = isGranted,
            riskLabel = riskLabel,
            lastUsedLabel = formatLastUsed(usageKey?.let(lastUsedMs)),
            highRisk = highRisk,
            kind = kind,
        )
    }

    private fun checkGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun formatLastUsed(epochMs: Long?): String {
        if (epochMs == null) return "尚未使用"
        val delta = System.currentTimeMillis() - epochMs
        return when {
            delta < 60_000L -> "刚刚使用"
            delta < 3_600_000L -> "${delta / 60_000L} 分钟前"
            else -> "${delta / 3_600_000L} 小时前"
        }
    }

    class Factory(
        private val application: Application,
        private val lastUsedMs: (String) -> Long?,
        private val projectionGranted: () -> Boolean = { false },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PermissionsViewModel(application, lastUsedMs, projectionGranted) as T
        }
    }

    companion object {
        const val CLIPBOARD_USAGE_KEY = "clipboard"
        const val SCREEN_CAPTURE_USAGE_KEY = "screen_capture"
    }
}
