package com.dougie.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dougie.core.tool.OpenAppEntries
import com.dougie.core.tool.OpenAppEntry

@Composable
fun OpenAppsRoute(
    viewModel: OpenAppsViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    OpenAppsScreen(
        entries = entries,
        launchers = { viewModel.launchers() },
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onRemove = viewModel::remove,
    )
}

@Composable
fun OpenAppsScreen(
    entries: List<OpenAppEntry>,
    launchers: () -> List<LauncherApp>,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OpenAppEntry?>(null) }
    var listQuery by remember { mutableStateOf("") }
    val shown = remember(entries, listQuery) {
        val q = listQuery.trim()
        if (q.isEmpty()) entries
        else entries.filter {
            it.alias.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }
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
            Text(
                text = "可打开的应用",
                color = DougieColors.Primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { adding = true }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加",
                    tint = DougieColors.Primary,
                )
            }
        }
        Text(
            text = "只有名单里的应用能被本地短路径或模型用 package 打开。别名需整句等于「打开」之后的文字。",
            color = DougieColors.OnSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (entries.isNotEmpty()) {
            OutlinedTextField(
                value = listQuery,
                onValueChange = { listQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("筛选已添加") },
            )
        }
        if (entries.isEmpty()) {
            Text(
                text = "还没有应用。点右上角从已安装应用里添加。",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
            )
        } else if (shown.isEmpty()) {
            Text(
                text = "没有匹配的已添加应用。",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(shown, key = { it.packageName }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
                            .background(DougieColors.SurfaceContainerLow, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PackageIcon(packageName = row.packageName)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                text = row.alias,
                                color = DougieColors.OnSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = row.packageName,
                                color = DougieColors.OnSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        IconButton(onClick = { editing = row }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = DougieColors.Primary)
                        }
                        IconButton(onClick = { onRemove(row.packageName) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = DougieColors.Error)
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        val available = remember(entries) {
            val taken = entries.map { it.packageName }.toSet()
            launchers().filter { it.packageName !in taken }
        }
        var addQuery by remember { mutableStateOf("") }
        val filtered = remember(available, addQuery) {
            val q = addQuery.trim()
            if (q.isEmpty()) available
            else available.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("添加应用") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addQuery,
                        onValueChange = { addQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索名称或包名") },
                    )
                    if (available.isEmpty()) {
                        Text(
                            "没有可添加的已安装应用。",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else if (filtered.isEmpty()) {
                        Text(
                            "没有匹配的应用。",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .padding(top = 8.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAdd(app.label.take(OpenAppEntries.ALIAS_MAX), app.packageName)
                                            adding = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PackageIcon(packageName = app.packageName)
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(app.label, color = DougieColors.OnSurface, fontSize = 16.sp)
                                        Text(
                                            app.packageName,
                                            color = DougieColors.OnSurfaceVariant,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { adding = false }) { Text("关闭") }
            },
        )
    }
    val editRow = editing
    if (editRow != null) {
        var draft by remember(editRow.packageName) { mutableStateOf(editRow.alias) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("别名") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(OpenAppEntries.ALIAS_MAX) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.trim().isNotEmpty(),
                    onClick = {
                        onRename(editRow.packageName, draft)
                        editing = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PackageIcon(packageName: String) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 40.dp.roundToPx().coerceAtLeast(1) }
    val bitmap = remember(packageName, px) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(width = px, height = px)
                .asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}
