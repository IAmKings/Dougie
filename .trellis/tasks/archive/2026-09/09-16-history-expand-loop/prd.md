# 任务卡可展开 Loop 与 Tool Result

## Goal

底栏「任务」每张卡能 **展开** 看这一轮逐步用了哪些工具、各步成功还是失败，而不必先点进对话。卡片主体仍打开该窗口并滚到那一句用户话。

## User value

回看时核对「调了什么、成没成」；要看对话内容仍点卡进 Chat。

## Background

- 刚归档 `09-15-history-duration-provider`。根 `PRD.md` §11.3 任务卡字段已有 Loop 次数与 Tool 链；「可展开结构化 Tool Result」写在 §11.5 Chat 气泡。本刀把逐步工具列表做到任务卡上，不把 History 做成第二份 Chat 工具卡。
- 现网：整卡 `clickable` → `openConversation` + 定位。循环行 `循环 n · toolName → …`。`HistoryItem` 只有 `toolChain` 字符串。
- Chat 当前轮 `ToolCard` 含 `resultJson`；过去轮不重放工具卡。spec：History 不 dump 工具参数。`ToolTraceEntry` 已在 snapshot。不 bump DB。

## Requirements

- R1 有 `toolTrace` 的卡，循环行右侧有「展开」/「收起」。无工具迹则没有该按钮。
- R2 点卡片主体（摘要、状态、耗时、Provider、循环行文字、错误）仍打开对话并定位用户句。点「展开」只切换逐步列表，不 `openConversation`。忙时打开对话仍 no-op。
- R3 展开列表：每步一行，`toolName`（与工具链同一套英文名）+ 成功 / 失败 / 进行中。不展示 `argsSummary`、`resultJson`、风险等级、思考芯片。默认收起；展开态不落盘。
- R4 不 bump `dougie_tasks.db`。不 log 输入 / snapshot。不改 Chat 工具卡。不抽取 `toolDisplayName`。

## Acceptance Criteria

- [x] AC1 有工具迹的卡出现「展开」；点开后逐步列出 name + 状态；再点「收起」列表消失。无工具迹的卡没有「展开」。
- [x] AC2 点摘要仍打开对话并滚到该用户句（与现网一致）。点「展开」不切会话、不滚 Chat。
- [x] AC3 展开区域不含参数 JSON、`resultJson`、日历标题/剪贴板/短信正文。
- [x] AC4 `HistoryItemTest` 覆盖步骤映射与空 toolTrace；JDK 17 `./gradlew :feature:history:testDebugUnitTest` 通过。

## Out of scope

会话删除、侧栏、规则 E、Chat 气泡改版、开发者页 resultJson、中文化工具链、把 `toolDisplayName` 抽到 `:core:model`、展开态持久化、重建 Chat「思考中 [循环 n]」芯片。

## Key Decisions

- 主体仍打开对话；单独「展开」只开关步骤。
- 展开深度：工具名 + 成功/失败/进行中，不跟 Chat 工具卡对齐结果 JSON。
- 工具名与现网 `toolChain` 一致（原始 `toolName`）。
- 默认收起；`remember(taskId)` 本地状态。
