package com.dougie.feature.permissions

import android.app.Application
import android.content.pm.PackageManager
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

data class PermissionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val runtimePermission: String?,
    val granted: Boolean,
    val riskLabel: String,
    val lastUsedLabel: String,
    val highRisk: Boolean = false,
)

class PermissionsViewModel(
    application: Application,
    private val lastUsedMs: (String) -> Long?,
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
        return listOf(
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
            ),
        )
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PermissionsViewModel(application, lastUsedMs) as T
        }
    }

    companion object {
        const val CLIPBOARD_USAGE_KEY = "clipboard"
    }
}
