package com.dougie.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dougie.core.model.MemoryEntry

@Composable
fun MemoryRoute(
    viewModel: MemoryViewModel,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MemoryScreen(
        uiState = uiState,
        onToggle = viewModel::setMemoryEnabled,
        onDelete = viewModel::delete,
        onClear = viewModel::clearAll,
        onSaveEdit = viewModel::updateContent,
        onOpenChat = onOpenChat,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun MemoryScreen(
    uiState: MemoryUiState,
    onToggle: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onSaveEdit: (MemoryEntry, String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Dougie",
                color = DougieColors.Primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmClear = true }, enabled = uiState.entries.isNotEmpty()) {
                Text("清空", color = DougieColors.Error)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("启用记忆", color = DougieColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (uiState.memoryEnabled) "新对话会检索并写入本地事实" else "已关闭写入与注入，已存事实仍可查看",
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = uiState.memoryEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DougieColors.OnPrimary,
                    checkedTrackColor = DougieColors.Primary,
                ),
            )
        }
        if (uiState.entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有记住的事实", color = DougieColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "对 Dougie 说「我叫小明」这类自我介绍后，会显示在这里。",
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    MemoryCard(
                        entry = entry,
                        onEdit = { editing = entry },
                        onDelete = { onDelete(entry.id) },
                    )
                }
            }
        }
        MemoryBottomBar(onOpenChat = onOpenChat, onOpenSettings = onOpenSettings)
    }
    val currentEdit = editing
    if (currentEdit != null) {
        var draft by remember(currentEdit.id) { mutableStateOf(currentEdit.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑事实") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveEdit(currentEdit, draft)
                        editing = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部记忆？") },
            text = { Text("这会删除 Dougie 本地保存的全部事实，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        confirmClear = false
                    },
                ) { Text("清空", color = DougieColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MemoryCard(
    entry: MemoryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(entry.content, color = DougieColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            text = "来源：${entry.source}",
            color = DougieColors.OnSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = relativeTime(entry.updatedAt),
            color = DougieColors.TertiaryContainer,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = DougieColors.Primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = DougieColors.Error)
            }
        }
    }
}

@Composable
private fun MemoryBottomBar(onOpenChat: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DougieColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem("对话", Icons.AutoMirrored.Filled.Chat, selected = false, onClick = onOpenChat)
        BottomItem("任务", Icons.Filled.History, selected = false)
        BottomItem("记忆", Icons.Filled.Storage, selected = true)
        BottomItem("设置", Icons.Filled.Settings, selected = false, onClick = onOpenSettings)
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) DougieColors.PrimaryContainer else Color.Transparent
    val tint = if (selected) DougieColors.OnPrimaryContainer else DougieColors.OnSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, color = tint, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

internal fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - epochMs).coerceAtLeast(0L)
    val minutes = delta / 60_000L
    return when {
        minutes < 1L -> "刚刚"
        minutes < 60L -> "${minutes} 分钟前"
        minutes < 24L * 60L -> "${minutes / 60L} 小时前"
        else -> "${minutes / (24L * 60L)} 天前"
    }
}
