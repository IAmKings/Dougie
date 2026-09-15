# Design: 会话可重命名

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:runtime` | `ConversationTitles`：读 map、写/清一条。纯函数 `conversationDisplayName` |
| `:data:preferences` | EncryptedSharedPreferences 键（JSON object id→name），`StateFlow`；**不**进 `ProviderSettings.save()` |
| `:feature:history` | `toHistorySections(..., titles)`；节标题右侧「改名」对话框；`setTitle` |
| `:feature:chat` | 顶栏 Dougie 下多一行 `conversationTitle: String`（由 `:app` 传入） |
| `:app` | 实现 `ConversationTitles`；Chat 用当前 id + titles + 是否空窗口算出显示名 |

不加表、不加列。`:core:*` 保持 JVM 纯净。标题不是 `AgentTask` 字段。

## Display name

```kotlin
fun conversationDisplayName(
    conversationId: String,
    customTitle: String?,
    numberedFallback: String, // 「对话 2」；default 忽略此参数
    isUnlistedNew: Boolean = false,
): String
```

- 自定义非空白 → trim 后的名（截到 20）。
- `id == default` → 「默认会话」。
- `isUnlistedNew`（当前 id 不在 `listRecent` 且 transcript+task 皆空）→ 「新对话」。
- 否则 → `numberedFallback`。

编号仍由 `toHistorySections` 按 50 条最旧优先生成。Chat 对已有窗口：用同一套编号（MainActivity 可把 `listRecent` 的 sections 里该 id 的 title 当 fallback，或 History/Chat 共用 mapper）。空新窗口不进 50 条，走「新对话」，不要临时占一个「对话 n」。

## Prefs

键与 `current_conversation_id` 并列，例如 `conversation_titles_json`：`{"uuid":"工作","default":"家里"}`。缺键 / 坏 JSON → 空 map。`save(ProviderSettings)` 不得 `remove` 该键。上限：只保留仍被写入的条目；改名时 trim；空则 delete key。不必按 50 条 GC（残留标题无害，体积极小）。

## History UI

`stickyHeader`：左标题，右 TextButton「改名」。对话框：预填当前显示名，保存/取消。保存走 `ConversationTitles.setTitle`。不 `openConversation`。

## Chat UI

`DougieTopBar(..., conversationTitle: String)`：Dougie 与灵魂之间插入一行 12sp / OnSurfaceVariant / 单行 ellipsis。不 clickable。

## Tests

- `conversationDisplayName`：自定义、空、default、新对话。
- `toHistorySections` 传入 titles 后「工作」覆盖「对话 2」。
- PreferenceStore：save() 后 titles 键仍在（若已有 prefs 单测风格）。
