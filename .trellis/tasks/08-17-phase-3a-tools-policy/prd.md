# Phase 3a Calendar Clipboard Policy

## Goal

在现有 Loop 上接上 **Policy 链（权限 + 风险确认）**、**日历查询/创建**、**前台剪贴板**，以及 **Permission Center**。L2 创建/写剪贴板必须 Confirm Card；拒绝则不执行。

## Background

- 依赖：已归档 Phase 2。
- 产品：`PRD.md` §6.3–§6.6、§11.2 Permission Center、§11.5 Confirm Card、Case 03–05。
- 视觉：`design/权限与系统/权限中心_-_隐私管控_中文版__*.html`、`高风险操作确认_-_权限授予_中文版__*.html`。文案 Dougie。
- 本切片 **不含** Location、ScreenCapture、OpenCV、Accessibility（归 3b / Beta）。

## Requirements

- **R1** `ToolDescriptor` 增加 `riskLevel`（L0–L2）与可选 `androidPermission`。`PolicyEngine`（JVM `:core:runtime`）在 Sanitize 之后、execute 之前：未知权限未授予 → 不执行，用户文案「未授权，已为你跳过该操作」；L2 → 等待用户确认。
- **R2** `TaskStatus.AWAITING_CONFIRMATION`。Chat 展示 Confirm Card（工具名、风险徽标、参数、确认/拒绝同级）。拒绝或超时（建议 60s）不执行，任务 `FAILED` 自然语言说明。`TaskManager.confirm()` / `reject()`。L0 电量/时间路径不变。
- **R3** `calendar_query`（L1，READ_CALENDAR）：返回今后事件摘要 JSON。`calendar_create`（L2，WRITE_CALENDAR）：title/startIso 必填；`idempotencyKey` 进程内去重，重复调用返回首次结果。Android 实现走 CalendarContract；JVM 用 Fake 端口测 Policy/幂等。
- **R4** `clipboard_read`（L1）仅当前台；后台返回明确错误。`clipboard_write`（L2）需确认。`:feature:*` 不直连 ClipboardManager / CalendarContract。
- **R5** OpenAI `tools` 数组由已注册 Tool 的 descriptor 生成，不要写死 battery/time。
- **R6** `:feature:permissions` Permission Center：列出日历读/写、（可选）剪贴板说明；已授权/未授权；引导系统设置或请求运行时权限；最近使用时间尽力而为。Chat 顶栏锁/盾可进入。空态引导授权。
- **R7** 拒绝权限后 Tool 不执行（UF-04）。

## Acceptance Criteria

- [ ] JVM：L0 Fake 三连 battery 测试仍绿。
- [ ] JVM：L2 Fake create 在 confirm=true 时执行一次；reject 时不调用 execute。
- [ ] JVM：缺权限时不 execute，lastError 含未授权文案。
- [ ] JVM：同一 `taskId+toolCallId` 两次 create，底层只 insert 一次。
- [ ] ChatUiState：AWAITING_CONFIRMATION 出现 ConfirmCard；input 禁用。
- [ ] OpenAI 请求 tools 含 `calendar_query` / `calendar_create` / `clipboard_*`（Mock 或单元组装）。
- [ ] `./gradlew :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:assembleDebug` 通过。

## Out of scope

- Location、MediaProjection、OpenCV、Accessibility
- 持久化幂等表（Phase 4）；本阶段进程内 Map 即可
- Play/Sideload 权限白名单差异
- 完整 Audit Log 落库

## Constraints

- `:core:*` JVM-only。
- 不静默 Fake LLM。
- 不 log 日历事件全文 / 剪贴板内容。
