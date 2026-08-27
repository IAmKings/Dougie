# 侧载悬浮球冻帧进对话

## Goal

侧载悬浮球在用户看着其他 App 时截一整屏，冻成已有「附上屏幕」芯片并打开 Chat；不自动提交任务。Play 行为不变。

## User value

不必先切回 Dougie 再截（否则只能截到 Chat）。人在目标 App 上点球，回到对话时芯片已绑定该帧。

## Background

- Chat 附上已落地：`pinCurrentScreen()`、`ScreenFrameStore.pin()`、芯片宽高、不 `submit`。
- 截屏硬门：`isAppForeground()`；后台文案 `应用不在前台，无法截取屏幕。`。MediaProjection 截整屏；FGS 线程约定见 backend spec。Consent 一次性。
- 悬浮球仅 sideload；单击目前只 `chatLaunchIntent`，不截、不 submit。Play 无 overlay。

## Decisions

- 单击（非拖动）= 截屏（允许后台）→ pin → 打开 Chat 显示芯片；**不** `TaskManager.submit`。
- 截屏前隐藏悬浮球，避免球进画面。
- LLM 的 `screen_capture` **仍要求前台**。仅 overlay / `pinCurrentScreen(requireForeground=false)` 可后台。
- Intent 只有布尔 extra（无像素、无 prompt）。失败仍开 Chat，展示既有中文错误。
- Play / Tile / 气泡 / 定时：不截屏。

## Requirements

- R1 像素不进 Intent / Prompt / 通知 / Logcat。
- R2 复用 `pin` + 芯片；不新开截屏栈。
- R3 不放松 Tool 前台门。
- R4 `checkChannelLeak`：Play 仍无 overlay。

## Out of scope

- Play 气泡截屏、倒计时、自动 submit、云视觉、相册。

## Acceptance Criteria

- [x] AC1 侧载、目标 App 在前台、点球（非拖）：Chat 出现「已附上 · 宽×高」，无缩略图，任务未自动跑。
- [x] AC2 无投屏授权：Chat 错误为 `未授权，已为你跳过该操作`，无假 capture_id。
- [x] AC3 Play 包无 overlay 行为变化；`checkChannelLeak` 通过。
- [x] AC4 LLM `screen_capture` 后台仍失败。
- [x] AC5 `ChatLaunch` extra 测试：无 prompt/key/像素。
