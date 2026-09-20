# Implement: Chat 工具卡展开

## Order

1. `prettyToolResult` + 收起规则（battery 摘要 / 其它不 dump / 非法原样）JVM 测试。
2. `ToolCallCard`：去掉常驻 `> 正在执行… result:` dump；进度条保留；有 `resultJson` 时「展开」/「收起」；展开画 pretty 终端块；battery SUCCESS 收起仍画短摘要。
3. `remember(entry.toolCallId)` 默认 false。
4. `./gradlew :feature:chat:testDebugUnitTest`

## Do not

- 改 Confirm / History / `toPastChatItems` / `listKey` / 进入动效。
- 展开 `argsSummary`。
- Compose UI 测试、DB bump。
- `task.py start` 前未获规划批准。
