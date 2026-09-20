# Design: Chat 工具卡展开

## Boundaries

| Module | Owns |
|--------|------|
| `:feature:chat` | `ToolCallCard` 收起/展开；`prettyToolResult`；收起摘要仍走 `toolResultSummary`（仅 battery） |
| Confirm overlay | 不改 |
| `:feature:history` | 不改 |
| `:core:*` | 不改 `ToolTraceEntry` |

## Data flow

```
ToolTraceEntry.resultJson
  → collapsed: battery → toolResultSummary; else omit dump
  → expand button iff resultJson != null
  → expanded: prettyToolResult(resultJson) in existing terminal block style
```

展开态：`remember(entry.toolCallId)` `mutableStateOf(false)`，与 History `remember(taskId)` 同级本地状态。

## Contracts

```
fun prettyToolResult(resultJson: String): String
  // parse JSON element; prettyPrint indent two spaces; on failure return original
```

Copy：「展开」/「收起」。`contentDescription` 同文案。

不把 `argsSummary` 拷进展开区。不改 `listKey` / `usesBubbleEnter` / `showsToolProgress`。

## Compatibility

确认卡仍展示参数。任务页展开仍是 name + 成败。过去轮仍无工具卡。`toolResultSummary("battery")` 语义不变。

## Risks

- 日历/剪贴板/短信的 `resultJson` 展开后仍可见——现网 dump 已经可见，只是改到展开后。不在本刀做脱敏。
- pretty-print 增加卡片高度，follow-to-end 仍由现网 `shouldFollowChatFeed` 处理。
