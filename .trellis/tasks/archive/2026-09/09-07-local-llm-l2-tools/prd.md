# 本地 LLM 对齐 L2 工具

## Goal

侧载 0.6B 能发出带参数的 L2 JSON，走现有确认卡后执行写剪贴板、建日历、打开 https 链接。

## Requirements

- R1 `localTeachable` 增加 `clipboard_write`、`calendar_create`、`app_intent`。协议示例含必填 args，不是 `{}`。已有七件无槽位仍教。
- R2 不教 `screen_match`、`speech_output`、`intent_classifier`、`tap_swipe`。远程仍全表。
- R3 真机关出境：确认卡点确认后 SUCCESS。
  - 「把你好写到剪贴板」→ `clipboard_write`
  - 「明天下午三点建一个开会」→ `calendar_create`（写日历已授；模型须带 title+startIso）
  - 「打开 https://example.com」→ `app_intent`（https 无需包名白名单）
- R4 不 Logcat；`checkChannelLeak` 过。

## Out of scope

模板匹配、TTS 工具、意图分类器、无障碍点击。

## Acceptance Criteria

- [ ] AC1 真机三句 + 确认卡。
- [ ] AC2 JVM：本地清单含这三名及带参示例；不含 `tap_swipe`。
- [ ] AC3 `:core:llm:test`。
