# 短路径截屏钉附件

## Goal

聊天里高置信「截个屏」走本地短路径成功后，该帧进入作曲家芯片（彩色预览），留给下一条消息。不自动再 `submit`。SCREEN 像素不上云、不进气泡。

## Background

- 队列：本切片 → 意图包 int8 → 向量记忆 → 端侧对话 LLM。开 App 短路径已完成。
- 悬浮球：`addScreen` 且不 `submit`，芯片一直留着。短路径会 `submit`，「截个屏」发出时 `onAttachmentsConsumed` 已清空作曲家；`ScreenCaptureTool` 只 `store.put(gray)`，丢掉 JPEG；`finally` 里 `clearPin` + `releaseAfterTask` 再清 store。
- 已有 SCREEN 芯片 / `attachedCaptureId` 时短路径仍跳过（既有）。
- Q1 已拍板：只钉 **LOCAL_INTENT** 短路径；LLM 循环里的 `screen_capture` 不钉。

## Requirements

- R1 短路径 `screen_capture` 成功：终答仍「已截取屏幕。」，LLM 0 次，该帧进入作曲家（JPEG 预览与悬浮球相同）。
- R2 芯片在本次 `COMPLETED` 之后仍在，直到用户删除或下一次发送（下一次 `releaseAfterTask` / `onAttachmentsConsumed`）。
- R3 提交时已有 4 张附件：不截屏，`FAILED` + `ATTACHMENTS_FULL`，不回落 LLM。
- R4 Play/侧载同一套。`checkChannelLeak`；SCREEN 不进 `image_url`。
- R5 非前台 / 无投屏：现网 Halt。LLM `screen_capture` 行为不变（不钉芯片）。
- R6 `:core:*` 不引用 Compose / `ChatAttachmentSession`。JPEG 仍只在进程内存。

## Acceptance Criteria

- [x] AC1 「截个屏」短路径成功后作曲家出现该帧彩色芯片；终答「已截取屏幕。」；开发者页本地意图。真机通过。
- [x] AC2 芯片留下；下一条带该芯片发出时，云端只有 `capture_id` 系统注记，无 SCREEN `image_url`。真机通过。
- [x] AC3 已有 4 张时「截个屏」为 `ATTACHMENTS_FULL`，无第五张，LLM 0 次。
- [ ] AC4 无授权 / 非前台与现网短路径一致。
- [x] AC5 LLM 调 `screen_capture` 成功后作曲家不因该次捕获新增芯片。
- [x] AC6 `:app:checkChannelLeak` 过。

## Out of scope

- `speech_input` 短路径、MiniRBT int8、向量记忆、端侧对话 LLM。
- 自动 `submit`、Play 气泡截屏、后台 LLM 截屏、token 落盘。
- 改 MediaProjection 会话。
- 短路径发出时已附相册是否「未上云却被清」——保持现网 `onAttachmentsConsumed`。
