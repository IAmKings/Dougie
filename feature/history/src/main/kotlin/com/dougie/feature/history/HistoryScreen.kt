package com.dougie.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dougie.core.model.CONVERSATION_TITLE_MAX_CHARS
import com.dougie.core.model.TaskStatus

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onOpenChat: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    HistoryScreen(
        uiState = uiState,
        onOpenChat = onOpenChat,
        onOpenMemory = onOpenMemory,
        onOpenSettings = onOpenSettings,
        onOpenConversation = onOpenConversation,
        onRename = viewModel::setTitle,
        onDelete = onDelete,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onOpenChat: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {},
) {
    var renaming by remember { mutableStateOf<HistorySection?>(null) }
    var deleting by remember { mutableStateOf<HistorySection?>(null) }
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
                text = "任务历史",
                color = DougieColors.Primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (uiState.sections.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有任务记录", color = DougieColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "对话完成后会显示在这里。中断的任务会标记为失败。",
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
                uiState.sections.forEach { section ->
                    stickyHeader(key = "section:${section.conversationId}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DougieColors.Surface)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = section.title,
                                color = DougieColors.Primary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { renaming = section }) {
                                Text("改名", color = DougieColors.Primary, fontSize = 13.sp)
                            }
                            if (section.canDelete()) {
                                TextButton(
                                    onClick = { deleting = section },
                                    modifier = Modifier.semantics { contentDescription = "删除" },
                                ) {
                                    Text("删除", color = DougieColors.Error, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    items(section.items, key = { it.taskId }) { item ->
                        HistoryCard(
                            item,
                            onOpen = { onOpenConversation(item.conversationId, item.taskId) },
                        )
                    }
                }
            }
        }
        HistoryBottomBar(
            onOpenChat = onOpenChat,
            onOpenMemory = onOpenMemory,
            onOpenSettings = onOpenSettings,
        )
    }
    val renamingSection = renaming
    if (renamingSection != null) {
        var draft by remember(renamingSection.conversationId) {
            mutableStateOf(renamingSection.title)
        }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("改名") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { next ->
                        draft = next.replace('\n', ' ').replace('\r', ' ')
                            .take(CONVERSATION_TITLE_MAX_CHARS)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renamingSection.conversationId, draft)
                        renaming = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消") }
            },
        )
    }
    val deletingSection = deleting
    if (deletingSection != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除会话") },
            text = {
                Text("将删除「${deletingSection.title}」中的全部任务，且无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deletingSection.canDelete()) {
                            onDelete(deletingSection.conversationId)
                        }
                        deleting = null
                    },
                ) { Text("删除", color = DougieColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, onOpen: () -> Unit) {
    val badgeColor = when (item.status) {
        TaskStatus.COMPLETED -> DougieColors.StatusCompleted
        TaskStatus.FAILED -> DougieColors.Error
        else -> DougieColors.Primary
    }
    var expanded by remember(item.taskId) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLowest)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.inputSummary,
                color = DougieColors.OnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.statusLabel,
                color = badgeColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        val meta = listOfNotNull(item.durationLabel, item.providerLabel, item.completedAtLabel)
            .joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                color = DougieColors.OnSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "循环 ${item.loopCount}" +
                    if (item.toolChain.isNotBlank()) "  ·  ${item.toolChain}" else "",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            if (item.steps.isNotEmpty()) {
                val expandLabel = if (expanded) "收起" else "展开"
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { contentDescription = expandLabel },
                ) {
                    Text(expandLabel, color = DougieColors.Primary, fontSize = 13.sp)
                }
            }
        }
        if (expanded && item.steps.isNotEmpty()) {
            item.steps.forEach { step ->
                Text(
                    text = "${step.toolName}  ·  ${step.statusLabel}",
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        val error = item.error
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                color = DougieColors.Error,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun HistoryBottomBar(
    onOpenChat: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DougieColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem("对话", Icons.AutoMirrored.Filled.Chat, selected = false, onClick = onOpenChat)
        BottomItem("任务", Icons.Filled.History, selected = true)
        BottomItem("记忆", Icons.Filled.Storage, selected = false, onClick = onOpenMemory)
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
