# 本地 LLM 教 screen_match 与 speech_output

## Goal

侧载 0.6B 在合适时机发出 `screen_match` / `speech_output` 一行 JSON，走现有 Tool。远程仍全表。不教 `tap_swipe`、`intent_classifier`。

## Background

`LOCAL_TEACH_NAMES` 现为十件。`localPrompt` 仅在尚无成功 tool 结果时教一行 JSON；有结果后禁止再出工具 JSON。`screen_match` 必填 `template_id`（内置 `solid` / `logo`），对最近一帧灰度 NCC；无帧或未过阈值 → fatal `SCREEN_MATCH_FAILED`。`speech_output` 必填非空 `text`。语音发送的宿主 `speakReply` 可能与 Tool 叠音；本刀不改宿主。

## Requirements

- R1 `localTeachable` 增加 `screen_match`、`speech_output`。示例带参：`template_id` 只示范 `solid`；`speech_output` 的 `text` 用短中文且与剪贴板示例「示例文字」区分。
- R2 远程 `systemPrefix` 仍全表。不教 `tap_swipe`、`intent_classifier`。
- R3 不改「工具结果后禁止再出 JSON」。同一任务不能先截再匹配；匹配依赖已有截屏/附件或 `ScreenFrameStore.last()`。不改 Tool 门闩、不把 SCREEN 像素写入 prompt、不 Logcat 提示或 TTS 原文。
- R4 JVM：清单含这两名及带参示例；本地不含 `tap_swipe`。`:core:llm:test` + `checkChannelLeak`。

## Out of scope

- 改 Loop 协议做截屏→匹配连招。
- `tap_swipe`、模板目录扩充、OpenCV、宿主 `speakReply` 去重。
- Play 本地聊天。

## Acceptance Criteria

- [ ] AC1 真机关出境：已有截屏时说匹配 → `screen_match`（`solid`）；打字「把你好念出来」走短语短路径 `speech_output`（不经 0.6B），应出声并以「已念出来。」收尾。无帧 / 错误 template_id 走现有中文失败，不猜测。
- [ ] AC2 JVM 本地清单含两名及 `template_id`/`text` 示例；远程仍含 `tap_swipe`；本地不含。
- [ ] AC3 `:core:llm:test`；`:app:checkChannelLeak`。
