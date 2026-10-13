# 后台确认通知（复合指令结构性断点）

## Goal

真机链路断点：「打开Anchor，点击tab」第一步 app_intent 打开第三方应用后 Dougie 退后台，L2/L3 确认卡在聊天界面用户看不见 → 60s 超时失败；且用户切回 Dougie 点确认会让前台变成 Dougie，tap_by_text 会在 Dougie 自己的控件树里找目标（目标应用已不在前台）——**确认动作与目标应用前台互斥**。修复：AWAITING_CONFIRMATION 状态的系统通知带「确认/拒绝」动作按钮，任何前台应用下都能完成确认。

## Confirmed facts

- `TaskProgressNotifier` 已观察 task StateFlow 并对 AWAITING_CONFIRMATION 发常驻通知（TaskNotice 已有「待确认 · 工具名」文案）——状态变化时自动更新/撤销，通路完整，缺的只是动作按钮。
- `DougieApplication.taskManager` 为 public lateinit；confirm()/reject() 即引擎确认门；manifest 已有静态 receiver 先例（DougieBootReceiver）。
- 通知动作点击不切前台（receiver 内直接调 TaskManager，无 activity trampoline，符合 Android 12+ 规范）。
- 悬浮框（DougieOverlayService）是另一可用面，本轮不做（通知不依赖悬浮权限、覆盖面更广），列为候选后续。

## Requirements

- R1 新增 `TaskConfirmReceiver`（app main，静态注册，exported=false）：ACTION_CONFIRM/ACTION_REJECT → 调 `(app as? DougieApplication)?.taskManager` 的 confirm()/reject()，未就绪静默忽略。
- R2 `TaskProgressNotifier.apply`：status==AWAITING_CONFIRMATION 时通知追加「确认」「拒绝」两个动作按钮（broadcast PendingIntent，独立 request code，FLAG_IMMUTABLE|UPDATE_CURRENT），并设 PRIORITY_HIGH 提升可见性；其余状态维持现状。
- R3 状态离开 AWAITING_CONFIRMATION 后通知自动恢复为无按钮形态（既有 StateFlow 收集保证）；超时语义不变（60s 门未动）。
- R4 双 flavor 通用（确认门与 flavor 无关，无 sideload 依赖）；不动 14 条冻结契约、不动 LoopEngine 确认门逻辑。

## Acceptance Criteria

- [x] AC1 1 编译双 flavor + `:app:testSideloadDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak` 绿；既有通知行为不回归。
- [x] AC1 2 真机验证：复合指令第二步确认卡期间，下拉通知出现「确认/拒绝」，点确认后流程继续（tap_by_text 在目标应用前台执行并验证）；点拒绝任务 CONFIRM_REJECTED。结果记回 device-checklist（AC2 真机待验证）

## Out of scope

- 悬浮框确认按钮（候选后续）；批量确认（一次放行整个任务计划——削弱逐步同意的安全模型，需产品决策）；确认超时时长调整。
