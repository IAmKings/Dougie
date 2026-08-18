# 修复 Case11 截屏后崩溃

## Goal

`screen_capture` 成功返回 `capture_id` / 宽高之后，Dougie 进程不得退出。真机（至少 PJZ110 / Android 16）按协议重跑 Case 11 的捕获半段，任务完成后应用仍在前台可操作。

## Background

- 父任务 `08-18-device-e2e-signoff` Case 11：**失败**。JSON 合格（无像素）；任务结束后崩溃；重启后历史可见工具结果。
- 同期 log：`MainActivity` `DESTROYED`、回到 Launcher，随后 `DeadObjectException`。投屏授权容易把 Activity 打到后台。
- 旧实现：`ScreenCaptureService` 在工作线程 `finally` 里调用 `stopForeground` / `stopSelf`，并在同一线程释放 `VirtualDisplay` / `projection.stop()`。ColorOS 上这是高嫌疑崩溃点。
- 工作区已有未提交草稿：主线程停前台服务、HandlerThread 上释放 display、捕获宽限制 720。本任务收口并在真机验证，不把记忆页/Gate 改动算进本任务验收。

## Requirements

- **R1** 捕获成功后进程保持活着；Chat 仍显示含 `capture_id`、`width`、`height` 且无像素/base64 的工具 JSON。
- **R2** `stopForeground` / `stopSelf` 只在主线程调用。VirtualDisplay / ImageReader / `MediaProjection.stop` 在创建它们的 HandlerThread 上释放。
- **R3** 须先 `startForeground`（`FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`）再 `getMediaProjection`。不把截图像素写入工具 JSON、日志或 Prompt。
- **R4** 在 PJZ110（或同等 Android 13–16）上重跑 Case 11 捕获半段：完成后手动再发一条聊天，证明未崩。拒绝授权半段：无 token 时 `PERMISSION_DENIED`，不得仍返回 `capture_id`。
- **R5** 不为签字加「强制崩溃」开关。不改 Loop 超时、不改记忆 Gate。

## Acceptance Criteria

- [x] 真机捕获半段：工具 JSON 合格，**任务结束后 10s 内进程仍在**（`adb` 可见 `com.dougie.app` 或 UI 仍可点）。
- [x] 拒绝/无投屏授权：失败文案为 `未授权，已为你跳过该操作`，无 `capture_id`。
- [x] 前台约束仍在：后台调用仍是 `应用不在前台，无法截取屏幕。`

## Out of Scope

- Case 07/09 真机诱导、OpenCV、记忆页 refresh、问句 Gate（可同 commit 但非本任务 AC）。
- 多 OEM 矩阵。归档签字父任务。

## Key Decisions

- 验收机：签字同款 PJZ110 / API 36。
- 崩溃修复优先于匹配成功率；`solid`/`logo` 匹配失败仍可受阻。
