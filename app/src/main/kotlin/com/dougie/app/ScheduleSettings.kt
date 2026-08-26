package com.dougie.app

import android.provider.Settings as AndroidSettings
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dougie.feature.chat.DougieColors
import java.time.ZoneId

@Composable
fun ScheduleSettings() {
    val context = LocalContext.current
    val store = remember { ScheduleStore(context.applicationContext.filesDir) }
    var items by remember { mutableStateOf(store.list()) }
    var hourText by remember { mutableStateOf("8") }
    var minuteText by remember { mutableStateOf("0") }
    var draft by remember { mutableStateOf("") }
    var daily by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val exact = ScheduleAlarms.exactLikely(context)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DougieColors.OutlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "定时提醒",
            color = DougieColors.OnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "到点发通知并打开对话，不会自动发送。最多 ${ScheduleStore.MAX} 条。",
            color = DougieColors.OnSurfaceVariant,
            fontSize = 14.sp,
        )
        if (!exact) {
            Text(
                text = "系统未允许精确闹钟，提醒可能被推迟。",
                color = DougieColors.OnSurfaceVariant,
                fontSize = 13.sp,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                TextButton(
                    onClick = {
                        val uri = Uri.parse("package:${context.packageName}")
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) {
                    Text("打开精确闹钟设置")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = hourText,
                onValueChange = { hourText = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("时") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = { minuteText = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("分") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("到点后填入输入框（可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "每日重复",
                color = DougieColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = daily, onCheckedChange = { daily = it })
        }
        error?.let {
            Text(text = it, color = DougieColors.Error, fontSize = 13.sp)
        }
        Button(
            onClick = {
                val hour = hourText.toIntOrNull()
                val minute = minuteText.toIntOrNull()
                if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59) {
                    error = "请填写 0–23 时、0–59 分"
                    return@Button
                }
                val now = System.currentTimeMillis()
                val epoch = nextDailyEpochMs(hour, minute, now, ZoneId.systemDefault())
                val item = ScheduleItem(
                    id = ScheduleStore.newId(),
                    hour = hour,
                    minute = minute,
                    daily = daily,
                    draft = draft.trim(),
                    oneShotEpochMillis = if (daily) null else epoch,
                )
                if (!store.add(item)) {
                    error = "最多 ${ScheduleStore.MAX} 条"
                    return@Button
                }
                error = null
                ScheduleAlarms.sync(context)
                items = store.list()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("添加")
        }
        items.forEach { item ->
            val time = formatScheduleNotice(item.hour, item.minute).removePrefix("定时提醒 · ")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(time)
                        if (item.daily) append(" · 每天") else append(" · 一次")
                    },
                    color = DougieColors.OnSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        ScheduleAlarms.cancel(context, item.id)
                        store.remove(item.id)
                        ScheduleAlarms.sync(context)
                        items = store.list()
                    },
                ) {
                    Text("删除")
                }
            }
        }
    }
}
