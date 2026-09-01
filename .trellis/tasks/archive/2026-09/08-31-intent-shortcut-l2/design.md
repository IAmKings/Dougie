# Design: L2 写操作短路径

## Boundaries

| Piece | Where |
|-------|--------|
| 意图 → 工具名 + 抽槽 | `:core:runtime` `IntentRouteAnswers` |
| 确认 / 权限 / 执行 | 现有 `executeToolPass` + `PolicyEngine` |
| 确认卡 UI | 现有 Chat `ConfirmCard`，不改交互 |

`:cli` 默认无 `intentPort`，行为不变。

## Data flow

```text
classify → toolNameFor
  → parseShortcutArgs(tool, text)  // null → 整段短路径放弃，LLM
  → executeToolPass(argsJson)      // L2 确认
  → Halt | Success + formatFinalAnswer
```

`completionPath=LOCAL_INTENT` 在已抽出参数、即将 `executeToolPass` 时写入（含等待确认期间）。

## Slot rules (conservative)

**clipboard_write `text`**

- 取成对 `「」` `『』` `" "` `' '` 中的第一段非空内容。
- 否则不抽（「复制这句话」「写到剪贴板」无载荷 → LLM）。

**calendar_create**

- `startIso`：必须出现可解析钟点（`N点`/`N点N分`/`HH:mm`），可带 今天/明天/后天、上午/下午/晚上（晚上 8 点 → 20:00）。缺钟点 → 不抽。
- `title`：去掉日期时间套话后的剩余；若空且原句含 开会/会议 → `开会`；仍空 → 不抽。
- 时区：`ZoneId.systemDefault()`。

不解析「下下周三」「大概下午」。

## Templates

- `calendar_create` 成功 JSON 有 `ok`/`title`：`已创建日程：{title}。`
- `clipboard_write`：`已写入剪贴板。`

解析失败 → `TOOL_FAILED`（已执行成功但模板坏了，不回落 LLM）。

## Compatibility

- 无槽位的 `create_calendar` 分类命中仍走 LLM（改旧单测）。
- 确认拒绝 / 无写日历权限不回落 LLM（用户已看到确认卡或权限错误）。
