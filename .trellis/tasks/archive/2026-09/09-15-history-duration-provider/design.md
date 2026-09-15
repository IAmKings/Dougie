# Design: 任务卡耗时与 Provider

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:model` | `AgentTask.startedAt` / `endedAt: Long? = null`（epoch ms）。`CompletionPath.toUserLabel()`：本地意图 / 本地 LLM / 远程 LLM |
| `:core:runtime` | Codec 编解码可选 long；`submit` 写 `startedAt`；终端态写 `endedAt`（见下）。`recoverInterrupted` / `markCancelled` 同样打点 |
| `:feature:history` | `formatTaskDuration`；`HistoryItem.durationLabel` / `providerLabel`；`HistoryCard` 元信息行 |
| `:feature:debug` | `completionPath?.toUserLabel() ?: "无"`（行为不变） |

不加表、不加列、不 bump `dougie_tasks.db`。`:core:*` 保持 JVM 纯净。Chat 不显示耗时。

## Data flow

```
submit → AgentTask(startedAt=now, endedAt=null)
      → LoopEngine.copy() 保留 startedAt
      → 终端 persist：endedAt = endedAt ?: now
      → TaskStore.upsert(snapshot_json)
      → listRecent → toHistoryItem → HistoryCard
```

- `startedAt`：仅 `TaskManager.submit` 创建任务时写入。`seed` / `openConversation` 不改时间戳。
- `endedAt`：当 `status` 为 `COMPLETED` 或 `FAILED` 且字段仍为 null 时写入。幂等，不覆盖。
- 打点点：`TaskManager` 的 emit/`persist` 路径、`markCancelled`、`recoverInterrupted`。不要在 LoopEngine 十几处 `copy(COMPLETED/FAILED)` 里散落时钟。
- 耗时：`formatTaskDuration(startedAt, endedAt)` 在 `:feature:history`。负差按 0。缺一边 → `null`。
- Provider：`completionPath?.toUserLabel()`；null → `null`（History 省略；Debug 仍「无」）。

## Codec

与 `attachedWidth` 相同：非 null 才 `put` long；decode `longOrNull`。缺键 / 旧 JSON → null。`ignoreUnknownKeys` 已开。`priorTurns` 继续省略。

## History UI

`HistoryItem` 增加 `durationLabel: String? = null`、`providerLabel: String? = null`。

`HistoryCard` 在循环行之上或之下加一行 13sp `OnSurfaceVariant`：

`[不足1秒|N秒|M分|M分S秒] · [本地意图|本地 LLM|远程 LLM]`

仅有一侧时不带 ` · `。两侧都空则不组 Text。

## Compatibility

- 旧快照：无时间戳 → 无耗时；无 `completionPath` → 无 Provider。
- 进行中：有 `startedAt`、无 `endedAt` → 无耗时；若已路由则可能已有 Provider。
- 重试：新 `taskId`、新 `startedAt`。旧行不变。
- Rollback：字段 default null；去掉 UI 行即可。磁盘多两个 JSON 键无害。

## Tests

- `formatTaskDuration`：null、不足1秒、3秒、1分、1分12秒、负差。
- `toHistoryItem`：两端时间戳 → 耗时；缺一端 → null；三路径标签；null path → providerLabel null。
- Codec roundtrip + 旧 JSON 无键。
- `submit` 后 `startedAt != null`；假 Loop 完成后 `endedAt >= startedAt`。
- `recoverInterrupted` / 取消：FAILED 带 `endedAt`。
- `DebugUiStateTest` 仍断言三标签 + 「无」。

## Don't

- 用 `updated_at` 当耗时。
- 把墙钟改成扣掉确认停留。
- Chat / Debug 加耗时。
- log `startedAt`/`endedAt` 以外的 snapshot。
- 小时文案、DB bump、`conversations` 表。
