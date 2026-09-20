# Chat 工具卡可展开结构化结果

## Goal

对话里的工具卡默认收起，只留标题、进度和短摘要；有 `resultJson` 时可点「展开」看缩进后的结构化结果，不再把整段 JSON 常驻铺在卡片上。

## User value

看对话时先看到调了什么、成没成；要对账再展开，而不是每次都被终端 dump 挡住。

## Background

- 根 `PRD.md` §11.5 Tool：Tool 名 + 参数摘要 + 结果；验收点「可展开查看结构化 Tool Result」。
- 现网 `ToolCallCard` 始终 dump `> 正在执行 {raw toolName}...\nresult: {resultJson ?: "..."}`。SUCCESS 另有 `toolResultSummary`：仅 `battery` 译成 `63%, charging: true`，其它工具再铺一遍 JSON。
- 确认卡是 overlay，已展示参数。过去轮 `toPastChatItems` 不重放工具卡。任务页展开只有 raw `toolName` + 成败，不 dump JSON（`09-16-history-expand-loop`）。

## Requirements

- R1 默认收起。PENDING / EXECUTING：标题 + 不确定进度条；无 `resultJson` 则没有「展开」。SUCCESS / FAILED：标题；`battery` SUCCESS 保留现有短摘要；其它工具收起态不展示 result dump。
- R2 `resultJson` 非空时，卡片上有「展开」/「收起」（与任务页同一套用词）。展开区为缩进后的 `resultJson`（合法 JSON pretty-print；非法则原样）。不展示 `argsSummary`。
- R3 展开态 `remember(toolCallId)`，不落盘。点「展开」不改 `listKey`、不重放工具卡进入动效、不打开任务页。
- R4 不改确认卡、任务页展开、过去轮映射、Chat 路由、catalog。

## Out of scope

- 确认卡参数、任务页展开深度、把过去轮工具卡加回对话。
- 为每个工具写中文 schema、可编辑 JSON、展开参数摘要。
- Debug 页、DB bump、抽取 `toolDisplayName`。
- 打字机动画、语音来源标注。

## Technical notes

- 映射函数放 `:feature:chat`（`prettyToolResult` / 收起摘要），`ChatUiStateTest` JVM 测；无 Compose UI 测试。
- 进度条仍在标题与结果区之间；收起且无摘要时进度条下可以没有 dump 块。

## Acceptance Criteria

- [x] AC1 无 `resultJson` 的进行中卡片：无「展开」，无 `result:` dump，进度条仍在。
- [x] AC2 有 `resultJson`：默认看不到整段 JSON；点「展开」出现缩进结果；再点「收起」消失。`battery` 收起仍有短摘要。
- [x] AC3 展开区不含 `argsSummary`；非法 JSON 原样显示，不崩溃。确认卡与任务页展开行为不变。
- [x] AC4 `ChatUiStateTest`（或同模块 JVM）覆盖 pretty / 收起摘要 / 非法 JSON；`./gradlew :feature:chat:testDebugUnitTest` 过。
