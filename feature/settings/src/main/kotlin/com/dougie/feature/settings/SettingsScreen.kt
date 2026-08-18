package com.dougie.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dougie.core.model.LlmVendors

const val EGRESS_CONSENT_COPY = "本次请求可能将输入、必要上下文和 Tool Result 发送至第三方 LLM 服务。"

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    SettingsScreen(
        form = form,
        models = models,
        onBack = onBack,
        onAllowCloudChange = viewModel::setAllowCloud,
        onVendorChange = viewModel::setVendor,
        onBaseUrlChange = viewModel::setBaseUrl,
        onApiKeyChange = viewModel::setApiKey,
        onModelChange = viewModel::setModel,
        onMaxTokensChange = viewModel::setMaxTokensText,
        onSave = viewModel::save,
        onRequestModel = viewModel::requestModel,
        onConfirmModel = viewModel::confirmModel,
        onDismissModelConfirm = viewModel::dismissModelConfirm,
        onCancelModel = viewModel::cancelModel,
        onOpenDebug = onOpenDebug,
    )
}

@Composable
fun SettingsScreen(
    form: SettingsFormState,
    models: OfflineModelsUi,
    onBack: () -> Unit,
    onAllowCloudChange: (Boolean) -> Unit,
    onVendorChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestModel: (String) -> Unit,
    onConfirmModel: () -> Unit,
    onDismissModelConfirm: () -> Unit,
    onCancelModel: (String) -> Unit,
    onOpenDebug: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DougieColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
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
            Text(
                text = "Dougie",
                color = DougieColors.Primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "提供商设置",
                color = DougieColors.OnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "配置云端模型与数据出境授权。默认拦截一切云端调用。",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                    .background(DougieColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "安全策略",
                    color = DougieColors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "允许显式数据出站",
                            color = DougieColors.OnSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = EGRESS_CONSENT_COPY,
                            color = DougieColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                    Switch(
                        checked = form.allowCloud,
                        onCheckedChange = onAllowCloudChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DougieColors.OnPrimary,
                            checkedTrackColor = DougieColors.Primary,
                        ),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                    .background(DougieColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "连接配置",
                    color = DougieColors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                FieldLabel("厂商")
                VendorDropdown(vendorId = form.vendorId, onVendorChange = onVendorChange)
                FieldLabel("基础 URL")
                SettingsField(value = form.baseUrl, onValueChange = onBaseUrlChange)
                FieldLabel("API 密钥")
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = form.apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "隐藏密钥" else "显示密钥",
                                tint = DougieColors.OnSurfaceVariant,
                            )
                        }
                    },
                    colors = fieldColors(),
                )
                FieldLabel("模型")
                SettingsField(value = form.model, onValueChange = onModelChange)
                FieldLabel("max_tokens")
                SettingsField(
                    value = form.maxTokensText,
                    onValueChange = onMaxTokensChange,
                    keyboardType = KeyboardType.Number,
                )
            }
            OfflineModelsSection(
                models = models,
                onRequestModel = onRequestModel,
                onCancelModel = onCancelModel,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                    .background(DougieColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenDebug)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "开发者",
                    color = DougieColors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "查看当前任务状态与最近工具审计。",
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            if (form.saved) {
                Text(
                    text = "已保存。下次对话将使用当前策略。",
                    color = DougieColors.TertiaryContainer,
                    fontSize = 14.sp,
                )
            }
            Button(
                onClick = onSave,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DougieColors.Primary,
                    contentColor = DougieColors.OnPrimary,
                ),
            ) {
                Text("保存配置")
            }
        }
    }
    val pending = models.pending
    if (pending != null) {
        AlertDialog(
            onDismissRequest = onDismissModelConfirm,
            title = { Text("下载 ${pending.title}？") },
            text = {
                val overwrite = if (pending.willReplace) "将覆盖当前已安装的意图模型。" else ""
                Text(
                    "将下载 ${pending.sizeLabel} 到 Dougie 应用私有目录，会占用存储并消耗流量。$overwrite 确认后才开始下载。",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmModel) { Text("确认下载") }
            },
            dismissButton = {
                TextButton(onClick = onDismissModelConfirm) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OfflineModelsSection(
    models: OfflineModelsUi,
    onRequestModel: (String) -> Unit,
    onCancelModel: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .background(DougieColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "离线模型",
            color = DougieColors.OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "语音识别、语音合成按需下载。意图理解请选 Q4 或 Q8（共用一份本地文件，换量化会覆盖）。Dougie 不会把模型当作 Agent 工具由云端触发。",
            color = DougieColors.OnSurfaceVariant,
            fontSize = 14.sp,
        )
        models.rows.forEach { row ->
            OfflineModelRow(
                row = row,
                onRequest = { onRequestModel(row.id) },
                onCancel = { onCancelModel(row.id) },
            )
        }
    }
}

@Composable
private fun OfflineModelRow(
    row: OfflineModelRowUi,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    color = DougieColors.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = row.sizeLabel,
                    color = DougieColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            when {
                row.installed -> Text("已安装", color = DougieColors.TertiaryContainer, fontSize = 14.sp)
                row.downloading -> TextButton(onClick = onCancel) { Text("取消") }
                else -> TextButton(onClick = onRequest, enabled = row.configured) { Text("下载") }
            }
        }
        if (!row.configured && !row.installed) {
            Text(
                text = OfflineModelDownloads.UNCONFIGURED,
                color = DougieColors.OnSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (row.downloading) {
            val progress = if (row.total > 0L) {
                (row.downloaded.toFloat() / row.total.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (row.total > 0L) "下载中 ${row.downloaded} / ${row.total}" else "下载中…",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (row.error != null && row.configured) {
            Text(text = row.error, color = DougieColors.Error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = DougieColors.OnSurfaceVariant,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp,
    )
}

@Composable
private fun VendorDropdown(vendorId: String, onVendorChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = LlmVendors.byId(vendorId)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "选择厂商",
                    tint = DougieColors.OnSurfaceVariant,
                )
            },
            colors = fieldColors(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LlmVendors.ALL.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onVendorChange(preset.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DougieColors.Primary,
    unfocusedBorderColor = DougieColors.OutlineVariant,
    focusedContainerColor = DougieColors.Surface,
    unfocusedContainerColor = DougieColors.Surface,
    focusedTextColor = DougieColors.OnSurface,
    unfocusedTextColor = DougieColors.OnSurface,
)
