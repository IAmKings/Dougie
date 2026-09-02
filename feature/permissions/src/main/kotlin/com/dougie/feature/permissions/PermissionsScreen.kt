package com.dougie.feature.permissions

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PermissionsRoute(
    viewModel: PermissionsViewModel,
    onBack: () -> Unit,
    onProjectionConsent: (resultCode: Int, data: Intent?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    PermissionsScreen(
        uiState = uiState,
        onBack = onBack,
        onGranted = { viewModel.refresh() },
        onProjectionConsent = { resultCode, data ->
            onProjectionConsent(resultCode, data)
            viewModel.refresh()
        },
    )
}

@Composable
fun PermissionsScreen(
    uiState: PermissionUiState,
    onBack: () -> Unit,
    onGranted: () -> Unit,
    onProjectionConsent: (resultCode: Int, data: Intent?) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onGranted() }
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        onProjectionConsent(result.resultCode, result.data)
    }
    val calendarDenied = uiState.items
        .filter { it.id.startsWith("calendar") }
        .none { it.granted }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DougieColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DougieColors.SurfaceContainer)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = DougieColors.Primary,
                )
            }
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = DougieColors.Primary,
            )
            Text(
                text = " 权限中心",
                color = DougieColors.Primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Dougie 只申请完成任务所需的权限。拒绝后相关操作会被跳过。",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
            )
            if (calendarDenied) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                        .background(DougieColors.SurfaceContainerLowest)
                        .padding(16.dp),
                ) {
                    Text("尚未授予日历权限", fontWeight = FontWeight.Bold, color = DougieColors.OnSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "授权后 Dougie 才能查询或创建日程。",
                        color = DougieColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    GrantButton("去授权日历") {
                        launcher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    }
                }
            }
            uiState.items.forEach { item ->
                PermissionRow(
                    item = item,
                    onGrant = {
                        when (item.kind) {
                            PermissionKind.SCREEN_CAPTURE -> {
                                val manager = context.getSystemService(MediaProjectionManager::class.java)
                                projectionLauncher.launch(manager.createScreenCaptureIntent())
                            }
                            PermissionKind.OVERLAY -> {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                            PermissionKind.RUNTIME -> {
                                val permission = item.runtimePermission
                                if (permission != null) {
                                    launcher.launch(arrayOf(permission))
                                }
                            }
                            PermissionKind.CLIPBOARD -> Unit
                        }
                    },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLowest)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.title,
                color = DougieColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.riskLabel,
                color = if (item.highRisk) DougieColors.Error else DougieColors.Primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(item.subtitle, color = DougieColors.OnSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (item.granted) "已授权" else "未授权",
            color = if (item.granted) DougieColors.Primary else DougieColors.Error,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(
            text = "最近使用：${item.lastUsedLabel}",
            color = DougieColors.OnSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        when (item.kind) {
            PermissionKind.RUNTIME -> {
                if (!item.granted) {
                    GrantButton("去授权", onGrant)
                } else {
                    GrantButton("在系统设置中撤销", onOpenSettings)
                }
            }
            PermissionKind.SCREEN_CAPTURE -> {
                GrantButton(if (item.granted) "重新授权屏幕截取" else "去授权屏幕截取", onGrant)
            }
            PermissionKind.OVERLAY -> {
                GrantButton(if (item.granted) "在系统设置中管理" else "去系统设置授权", onGrant)
            }
            PermissionKind.CLIPBOARD -> Unit
        }
    }
}

@Composable
private fun GrantButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = DougieColors.OnPrimary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DougieColors.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
