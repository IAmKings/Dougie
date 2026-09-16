# Design: Chat 气泡显示耗时

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:model` | `formatTaskDuration(startedAt, endedAt)` 唯一实现（与 `conversationDisplayName` 一样是 JVM 纯文案） |
| `:feature:history` | `toHistoryItem` 继续调用该函数；删掉本模块副本 |
| `:feature:chat` | `ChatItem.AgentMessage.durationLabel`；终态映射写入；`AgentBubble` 画在正文下、来源上 |

不改 `TaskSnapshotCodec`、`TaskManager` 打点、`dougie_tasks.db`、Debug、任务卡布局。

## Data flow

```
AgentTask(startedAt, endedAt, status)
  → formatTaskDuration          // 缺一端 → null
  → 仅 COMPLETED/FAILED 的 AgentMessage.durationLabel
  → AgentBubble 12sp OnSurfaceVariant
```

流式 `AgentMessage`（`streamingText`、非终态）不传 `durationLabel`。`toPastChatItems` 与 `toChatUiState` 同一规则。

## Chat UI

```kotlin
data class AgentMessage(
    val text: String,
    val memorySources: List<String> = emptyList(),
    val durationLabel: String? = null,
    override val listKey: String,
)
```

`AgentBubble`：非空 `durationLabel` 时，在气泡 `Row` 之后、`memorySources` 之前画一行。来源仍是 12sp monospace「来源：$it」；耗时不是 monospace，也不是「来源：」前缀。`listKey` 不变。

## Formatter move

现网 `feature/history/.../HistoryItem.kt` 的 `formatTaskDuration` 原样迁到 `core/model/.../AgentTask.kt`（或紧邻的同包文件）。签名与桶不变。History / Chat 都 `import com.dougie.core.model.formatTaskDuration`。禁止在 Chat 复制一份。

桶测迁到 `AgentTaskTest`。`HistoryItemTest` 只断言 `toHistoryItem().durationLabel` 仍接到该函数（至少一端有、一端无、FAILED「2秒」）。`ChatUiStateTest` 断言：终态有两端 → 标签；流式有 `startedAt` 无 `endedAt` → null；过去轮同样。

## Compatibility

- 旧快照无时间戳 → Chat 与 History 都不显示耗时。
- 重试：新 `taskId`、新时间戳；旧气泡保持自己的标签。
- Rollback：去掉 `durationLabel` 绘制即可；磁盘 JSON 无新键。

## Don't

- Compose 里 `System.currentTimeMillis() - startedAt`。
- Chat 依赖 `:feature:history`。
- 新建 `:core:ui`。
- 把耗时写进气泡 `text`。
- 小时文案、Provider、完成时刻。
