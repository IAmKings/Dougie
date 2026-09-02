# 侧载悬浮球截屏可发现性

## Goal

侧载用户能发现并完成「切到第三方 App → 悬浮球展开 → 截取屏幕」；缺上层显示或投屏时被带到授权，不假装已截。Chat 短路径成功后再给一行侧载引导。Play 不变。

## Background

- 短路径只截 Dougie 前台、不钉芯片。第三方入口是悬浮球。
- Q1：单击展开「截取屏幕 / 打开对话」，不再单击即截。
- Q2：侧载在「已截取屏幕。」之后再显示引导；不写进 `:core` 模板；不进 TTS `finalAnswer`。
- 设置 `overlay_body` 现为「点按打开对话」，与行为不符。球上收起时为 Dougie。
- 授权：上层显示（设置开关）+ MediaProjection（权限中心）。Play 禁止 overlay /「上层显示」。

## Requirements

- R1 设置 `overlay_body`：切到其他应用 → 点悬浮球展开 → 截取屏幕；需「显示在其他应用上层」和投屏；默认关闭；不含对话原文。
- R2 非拖动单击展开固定两项：**截取屏幕**、**打开对话**。再点空白或球收起。拖动只移动。收起标签仍为 Dougie。
- R3 「截取屏幕」：先收起面板并藏球 → `pinCurrentScreen(false)` → `chatLaunchIntent(applyPinnedScreen=true)`，不 `submit`。无投屏：开 Chat，`overlayAttachError` 为既有 `PERMISSION_DENIED`，状态行可去权限中心。无上层显示：打开系统上层显示页，不开假芯片。
- R4 「打开对话」：不截，`chatLaunchIntent` 且 `applyPinnedScreen=false`。
- R5 侧载 Chat：`COMPLETED` + `LOCAL_INTENT` + 成功工具 `screen_capture` 时，在终答气泡下显示固定引导（见文案）。Play 与 LLM 截屏路径不加。`播报` 仍只读 `已截取屏幕。`。
- R6 权限中心侧载增加「上层显示」项（开系统设置），与投屏项并列说明截第三方需要两道授权。Play 列表不变。
- R7 Play：无 overlay 类型、无「上层显示」字符串；`checkChannelLeak` / `PlayShortcutCopyTest`。

## Acceptance Criteria

- [x] AC1 侧载开球后，单击展开两项；拖动不展开。点「截取屏幕」后 Chat 有屏幕芯片、无自动任务。
- [x] AC2 点「打开对话」进入 Chat，无新芯片（除非原先已有）。
- [x] AC3 无投屏点「截取屏幕」：无假 capture_id，Chat 错误为 `未授权，已为你跳过该操作`，可去权限中心。
- [x] AC4 设置文案含截屏与两道授权，不含「点按打开对话」。
- [x] AC5 短路径「已截取屏幕。」后侧载出现引导句；Play 与 `播报` 不含该句。
- [x] AC6 Play 包无 overlay /「上层显示」；`checkChannelLeak` 通过。

## Copy

引导句：`截其他应用请打开设置里的悬浮球，切到目标应用后点「截取屏幕」。需要「显示在其他应用上层」和投屏授权。`

## Out of scope

Play 气泡截屏；放开 Tool 前台门；短路径钉附件；自动 `submit`；无障碍代替悬浮球；改 MiniRBT。
