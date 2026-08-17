# Phase 5j：离线模型按需下载

## Goal

提供 **HTTPS + SHA-256** 把 ASR/TTS/意图模型写到 `filesDir` 的安装器。必须先有用户确认；校验失败删除半成品。本切片 **不是** Agent Tool（模型不得让 LLM 填任意 URL）。**不** 入库模型、**不** 做 CER/意图评测集、**不** 做完整下载 UI。

## Background

- 根 `PRD.md` §6.8–6.10：play 按需下载至私有目录；sideload 内置是后续打包切片。
- 布局已有：`models/asr`、`models/tts`、`models/intent`。

## Requirements

- 仅 `https://` URL；哈希不符则失败且不留下坏文件。
- `userConfirmed=false` 时不发起网络请求。
- 成功后目标文件与 `*ModelLayout` 文件名一致。
- 不把 URL 查询串、哈希以外的密钥写入日志。
- 不经过 `EgressGateway`（那是 LLM 出境，不是模型包）。

## Acceptance Criteria

- [x] JVM：未确认不下载；http 拒绝；哈希失败不保留文件；成功可被 layout 识别。
- [x] `./gradlew :core:tool:test :app:checkChannelLeak` 通过。
