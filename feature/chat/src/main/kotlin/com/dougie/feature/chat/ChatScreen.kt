package com.dougie.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dougie.core.model.ToolTraceStatus
import com.dougie.feature.chat.R as ChatR

@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    allowCloud: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        uiState = uiState,
        onSend = viewModel::send,
        onConfirm = viewModel::confirm,
        onReject = viewModel::reject,
        onRetry = viewModel::retry,
        allowCloud = allowCloud,
        onOpenSettings = onOpenSettings,
        onOpenMemory = onOpenMemory,
        onOpenPermissions = onOpenPermissions,
        onOpenHistory = onOpenHistory,
    )
}

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSend: (String) -> Unit,
    onConfirm: () -> Unit = {},
    onReject: () -> Unit = {},
    onRetry: () -> Unit = {},
    allowCloud: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DougieColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        DougieTopBar(
            allowCloud = allowCloud,
            onOpenSettings = onOpenSettings,
            onOpenPermissions = onOpenPermissions,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isEmpty) {
                EmptyState(onExampleClick = onSend)
            } else {
                ChatFeed(
                    items = uiState.items,
                    canRetry = uiState.canRetry,
                    onConfirm = onConfirm,
                    onReject = onReject,
                    onRetry = onRetry,
                )
            }
        }
        ChatInputBar(
            enabled = uiState.inputEnabled,
            onSend = onSend,
        )
        DougieBottomBar(
            onOpenSettings = onOpenSettings,
            onOpenMemory = onOpenMemory,
            onOpenHistory = onOpenHistory,
        )
    }
}

@Composable
private fun DougieTopBar(
    allowCloud: Boolean,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DougieColors.SurfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(ChatR.drawable.dougie_logo),
            contentDescription = "Dougie",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, DougieColors.OutlineVariant, CircleShape)
                .background(DougieColors.SurfaceContainerLowest),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dougie",
                color = DougieColors.Primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 32.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = DougieColors.OnSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " 灵魂: 默认  |  ",
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                )
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = DougieColors.OnSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (allowCloud) " 出境策略: 已授权云端" else " 出境策略: 仅本地",
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "权限中心",
            tint = DougieColors.Primary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onOpenPermissions),
        )
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = "隐私",
            tint = DougieColors.Primary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onOpenPermissions),
        )
    }
}

@Composable
private fun EmptyState(onExampleClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(ChatR.drawable.dougie_logo),
            contentDescription = "Dougie",
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "你好，我是 Dougie",
            color = DougieColors.Primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "本地理解上下文，保护你的隐私。尝试问我：",
            color = DougieColors.OnSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        ExampleChip(BATTERY_EXAMPLE, onExampleClick)
        Spacer(Modifier.height(12.dp))
        ExampleChip(TIME_EXAMPLE, onExampleClick)
    }
}

@Composable
private fun ExampleChip(text: String, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLowest)
            .clickable { onClick(text) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Memory,
            contentDescription = null,
            tint = DougieColors.Primary,
        )
        Text(text = text, color = DougieColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChatFeed(
    items: List<ChatItem>,
    canRetry: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val lastAgent = (items.lastOrNull() as? ChatItem.AgentMessage)?.text
    LaunchedEffect(items.size, lastAgent) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(items.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = items,
            key = { item ->
                when (item) {
                    is ChatItem.UserMessage -> "user"
                    is ChatItem.Thinking -> "thinking-${item.loopNumber}"
                    is ChatItem.ToolCard -> "tool-${item.entry.toolCallId}"
                    is ChatItem.ConfirmCard -> "confirm-${item.toolCallId}"
                    is ChatItem.AgentMessage -> "agent"
                }
            },
        ) { item ->
            when (item) {
                is ChatItem.UserMessage -> UserBubble(item.text)
                is ChatItem.Thinking -> ThinkingChip(item.loopNumber)
                is ChatItem.ToolCard -> ToolCallCard(item)
                is ChatItem.ConfirmCard -> ConfirmToolCard(item, onConfirm, onReject)
                is ChatItem.AgentMessage -> AgentBubble(
                    text = item.text,
                    showRetry = canRetry && item === items.lastOrNull(),
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            color = DougieColors.OnSurface,
            fontSize = 16.sp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp).copy(topEnd = androidx.compose.foundation.shape.CornerSize(4.dp)))
                .border(1.dp, DougieColors.Primary, RoundedCornerShape(16.dp).copy(topEnd = androidx.compose.foundation.shape.CornerSize(4.dp)))
                .background(DougieColors.SurfaceContainerLowest)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AgentBubble(
    text: String,
    showRetry: Boolean = false,
    onRetry: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(
                text = text,
                color = DougieColors.OnSurface,
                fontSize = 16.sp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(16.dp).copy(topStart = androidx.compose.foundation.shape.CornerSize(4.dp)))
                    .background(DougieColors.SurfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (showRetry) {
            Text(
                text = "重试",
                color = DougieColors.Primary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, DougieColors.Outline, RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ThinkingChip(loopNumber: Int) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(
        modifier = Modifier
            .scale(scale)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = null,
            tint = DougieColors.StatusThinking.copy(alpha = alpha),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "思考中... [KISS 循环 $loopNumber]",
            color = DougieColors.StatusThinking.copy(alpha = alpha),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ToolCallCard(item: ChatItem.ToolCard) {
    val entry = item.entry
    val barColor = when (entry.status) {
        ToolTraceStatus.SUCCESS -> DougieColors.StatusCompleted
        ToolTraceStatus.EXECUTING, ToolTraceStatus.PENDING -> DougieColors.StatusExecuting
        ToolTraceStatus.FAILED -> Color(0xFFD32F2F)
    }
    val toolLabel = toolDisplayName(entry.toolName)
    val risk = entry.riskLevel.name
    val label = when (entry.status) {
        ToolTraceStatus.SUCCESS -> "已调用 $toolLabel ($risk)"
        ToolTraceStatus.EXECUTING -> "正在调用 $toolLabel ($risk)"
        ToolTraceStatus.PENDING -> "准备调用 $toolLabel ($risk)"
        ToolTraceStatus.FAILED -> "$toolLabel 失败 ($risk)"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(start = 16.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLow),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(barColor),
        )
        Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = barColor, modifier = Modifier.size(18.dp))
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = DougieColors.OnSurface,
                )
            }
            val resultJson = entry.resultJson
            Text(
                text = "> 正在执行 ${entry.toolName}...\nresult: ${resultJson ?: "..."}",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = DougieColors.SecondaryFixed,
                modifier = Modifier
                    .padding(start = 8.dp, top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DougieColors.TerminalBg)
                    .padding(8.dp),
            )
            if (entry.status == ToolTraceStatus.SUCCESS && resultJson != null) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = DougieColors.StatusCompleted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = toolResultSummary(entry.toolName, resultJson),
                        color = DougieColors.StatusCompleted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmToolCard(
    item: ChatItem.ConfirmCard,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val toolLabel = toolDisplayName(item.toolName)
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(start = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DougieColors.Error, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLowest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Build, contentDescription = null, tint = DougieColors.Error)
            Text(
                text = "确认 $toolLabel",
                fontWeight = FontWeight.Bold,
                color = DougieColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.riskLevel.name,
                color = DougieColors.Error,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "该操作会写入设备数据。确认后才会执行；拒绝则跳过。",
            color = DougieColors.OnSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(
            text = item.argsJson.ifBlank { "{}" },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = DougieColors.SecondaryFixed,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DougieColors.TerminalBg)
                .padding(8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConfirmActionButton(
                label = "拒绝",
                modifier = Modifier.weight(1f),
                onClick = onReject,
            )
            ConfirmActionButton(
                label = "确认",
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun ConfirmActionButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        color = DougieColors.OnSurface,
        fontWeight = FontWeight.Medium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .border(1.dp, DougieColors.Outline, shape)
            .background(DougieColors.SurfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun ChatInputBar(
    enabled: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DougieColors.Surface.copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                .background(DougieColors.SurfaceContainerLowest),
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = enabled,
                placeholder = { Text("给 Dougie 发消息...") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.Mic, contentDescription = "麦克风", tint = DougieColors.OnSurfaceVariant)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DougieColors.SurfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Filled.Memory,
                        contentDescription = null,
                        tint = DougieColors.TertiaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "记忆门控: 开启",
                        color = DougieColors.TertiaryContainer,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            draft = ""
                        }
                    },
                    enabled = enabled && draft.isNotBlank(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DougieColors.Primary),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = DougieColors.OnPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DougieBottomBar(
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DougieColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem("对话", Icons.AutoMirrored.Filled.Chat, selected = true)
        BottomItem("任务", Icons.Filled.History, selected = false, onClick = onOpenHistory)
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

internal fun toolDisplayName(toolName: String): String = when (toolName) {
    "battery" -> "电池工具"
    "time" -> "时间工具"
    "calendar_query" -> "日历查询"
    "calendar_create" -> "创建日程"
    "clipboard_read" -> "读取剪贴板"
    "clipboard_write" -> "写入剪贴板"
    else -> toolName
}

internal fun toolResultSummary(toolName: String, resultJson: String): String {
    if (toolName != "battery") return resultJson
    val percent = Regex(""""battery_percent"\s*:\s*(\d+)""").find(resultJson)?.groupValues?.get(1)
    val charging = Regex(""""charging"\s*:\s*(true|false)""").find(resultJson)?.groupValues?.get(1)
    return if (percent != null && charging != null) {
        "$percent%, charging: $charging"
    } else {
        resultJson
    }
}

internal fun batterySummary(resultJson: String): String = toolResultSummary("battery", resultJson)
