# 思考→工具卡状态切换

## Goal

思考芯片切到调用工具时，按 PRD §11.7 做 150ms `LinearOutSlowIn` 状态切换；同一张工具卡从「正在调用」到「已调用」也走这套。现网是思考瞬间变成「循环 n」，新工具卡再走 200ms 气泡进入，卡上文案瞬间换字。

## User value

执行链从「思考中」接到「正在调用」、再到「已调用」时有一段明确的状态过渡，而不是控件各闪一下。

## Background

- 根 `PRD.md` §11.7：状态切换 150ms `LinearOutSlowIn`，说明「Thinking→Calling 等」；动效期间可交互；系统「移除动画」时降级为淡入。同表 Bubble 进入、Confirm 弹出、Tool 不确定进度条、Chat↔任务页共享元素另有行。
- §11.1 链：Thinking → Calling → Waiting for permission → Tool Result → Final。§11.6：状态切换伴随进度指示；进度条本身是另一行，本刀不做。
- 现网列表结构已锁：每轮先 `Thinking`（live「思考中… [循环 n]」→ 死后「循环 n」，同一 `listKey`），再 `ToolCard`（`准备调用` / `正在调用` / `已调用` / 失败，同一 `tool-{id}`）。Confirm 走覆盖层，`confirm-{id}` 不在列表里。
- 新 `ToolCard` 目前走气泡进入（200ms FastOutSlowIn 淡入上移 8dp）。同一 key 的 live→死、卡上文案变化都不动画。确认后覆盖层关掉，列表里第一次出现 `tool-{id}`，现网也会走气泡进入。
- 无 Compose UI 测试。不 bump DB。不改终止、Confirm 上滑语义。

## Requirements

- R1 本轮 live 思考变成死后「循环 n」：150ms `LinearOutSlowIn`。新出现的 `ToolCard`（思考之后，或确认覆盖关掉之后）：150ms `LinearOutSlowIn` 淡入，不位移、不走气泡进入的 8dp 上移。
- R2 同一 `tool-{id}` 文案从准备/正在调用切到已调用或失败：150ms `LinearOutSlowIn`。
- R3 动效期间可交互。系统动画时长为 0：只淡入、无位移（本刀本就无位移）。`ChatScreen` 第一次组合不重播；同一 `listKey` 的后续状态变化只播 R2，不重播进入。
- R4 不改文案、风险色、`listKey`、Confirm 上滑/压暗、终止语义、`shouldFollowChatFeed`。列表仍是「循环 n」芯片 + 工具卡两行。下一轮新的 live 思考芯片仍走气泡进入。无 Compose UI 测试。不 bump DB。

## Acceptance Criteria

- [x] AC1 思考中出现工具调用：芯片收成「循环 n」，工具卡 150ms 淡入；该卡没有 8dp 上移的气泡进入。确认之后工具卡进列表同样 150ms 淡入、无 8dp 上移。
- [x] AC2 卡上「正在调用」变为「已调用」或失败：150ms 切字，不重播进入。
- [x] AC3 打开已有窗口或底栏回对话：已完成的链不重播切换。系统关闭动画时只淡入。
- [x] AC4 JDK 17：`./gradlew :feature:chat:testDebugUnitTest` 通过。无 Compose UI 测试。

## Out of scope

不确定进度条、Chat↔任务页共享元素、思考芯片与工具卡并成一行、Confirm 离场动效、无工具时思考直接变终答、规则 E、DB bump。

## Key Decisions

- 覆盖：思考芯片 live→「循环 n」；所有新 `ToolCard`（含确认后）用 150ms LinearOutSlowIn 淡入，不走 200ms 气泡进入。同一张卡准备/正在调用 → 已调用/失败也 150ms 切字。
- 列表结构不变。下一轮 live 思考仍走气泡进入。
- 打开窗口 / 底栏返回不重播。
