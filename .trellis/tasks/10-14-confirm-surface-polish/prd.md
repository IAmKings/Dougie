# 确认面去重与透明度

## Goal

真机验证通过后的三条体验反馈：①悬浮框确认菜单 UI 简陋且无标题；②确认面只显示工具名（tap_by_text），不显示点击标准（要点的文字）；③Dougie 自己前台时聊天确认卡与悬浮框菜单重复弹出。另登记「完全操作模式」（后台连续执行不逐卡询问）为规划任务，本轮不动安全模型。

## Requirements

- R1 纯 JVM 助手 `TaskNotice.confirmSummary(task): String?`：AWAITING_CONFIRMATION 时从最后一条 trace 的 argsSummary 提取 `text` 字段（JSON 解析，容错）→「点击“记录”」；无 text 字段返回 null。formatTaskNotice 的待确认行带 detail：「待确认 · tap_by_text · 点击“记录”」。
- R2 悬浮框确认菜单（DougieOverlayService）：圆角卡片 + 标题行「Dougie 请求确认」+ 明细行（confirmSummary 结果或工具名）+ 「✓ 确认」/「✕ 拒绝」行（确认绿、拒绝红）。
- R3 去重：悬浮框菜单弹出前检查前台包名==自身 → 不弹（聊天确认卡已覆盖）；已在显示中不重复。
- R4 「完全操作模式」另立规划任务（本轮不实现）：设计约束为任务级一次性授权、仅 L2/L3 非高危、验证照跑、可随时打断。
- R5 不动 14 条冻结契约、不动 LoopEngine 确认门与 60s 超时。

## Acceptance Criteria

- [ ] AC1 JVM 测试：confirmSummary 对含 text 的 argsSummary 返回「点击“记录”」；无 text/坏 JSON 返回 null；formatTaskNotice 待确认行含 detail。
- [ ] AC2 悬浮框 UI 改造编译通过（Android UI 真机验证）。
- [ ] AC3 全量 `:app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak` 绿。
- [ ] AC4 真机验证：第三方前台时悬浮框确认带「点击“记录”」明细；Dougie 前台时不重复弹。

## Out of scope

- 完全操作模式实现（另立规划任务）；悬浮框 Compose 化；确认卡（聊天内）样式改动。
