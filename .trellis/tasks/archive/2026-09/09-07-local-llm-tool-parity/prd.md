# 对齐本地 LLM 工具调用

## Goal

侧载 0.6B 关出境时，在已教会四件之外，再稳定调用 `calendar_query`、`screen_capture`、`speech_input`。

## Background

Loop 全表可执行；本地清单目前只有 time / battery / clipboard_read / location。`calendar_query` 的 `{}` 等价 limit=10。截屏、语音输入无槽位。L2/L3 与带必填槽位的工具本刀不教。

## Requirements

- R1 `localTeachable` 增加 `calendar_query`、`screen_capture`、`speech_input`；协议示例均为 `{"name":"...","args":{}}`。原四件仍在。
- R2 远程 `systemPrefix` 仍全表。不把 `calendar_create`、`clipboard_write`、`screen_match`、`app_intent`、`speech_output`、`intent_classifier`、`tap_swipe` 写入本地清单。
- R3 真机（侧载、关出境、对话包）在对应权限/包就绪时：
  - 「最近有什么日程」→ `calendar_query` SUCCESS（已授读日历）
  - 「截一下当前屏幕」→ `screen_capture` SUCCESS（前台且投屏已授）
  - 「听我说一句」→ `speech_input` SUCCESS（前台、录音权限、ASR 包就绪）
- R4 不 Logcat 提示；Play `checkChannelLeak` 过。

## Out of scope

- L2 写剪贴板 / 建日历、打开应用、模板匹配、TTS 工具、意图分类器、无障碍点击
- 把全表塞回本地清单

## Acceptance Criteria

- [ ] AC1 真机三句如上，各 `LOCAL_LLM` + 对应工具 SUCCESS。
- [ ] AC2 JVM：全表入、`localPrompt` 含这七名及 `{}` 示例，不含 `calendar_create` / `tap_swipe`；远程仍含未教名。
- [ ] AC3 `:core:llm:test`；`:app:checkChannelLeak`。
