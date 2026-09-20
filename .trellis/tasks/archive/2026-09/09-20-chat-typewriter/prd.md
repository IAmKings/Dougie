# Chat 终答打字机动画

## Goal

Agent 终答在一次性整段出现时按字打出；SSE 追加、历史轮、动画缩放为 0、失败气泡直接全文。不重做终端风配色，不做暗色主题。

## User value

本地意图 / 端侧模型一次性吐出终答时，不再整段砸进气泡。

## Background

- `PRD.md` §3.1 / §11.4：打字机现网未做。气泡进入已有；`ANIMATOR_DURATION_SCALE == 0` 只淡入、不位移。
- 同一 `listKey` `{taskId}:agent`：流式 `streamingText` → 终态 `finalAnswer`。SSE 每次写入更长前缀。streaming→final 已规定不重放进入动效。
- 过去轮直接终答。失败为「任务失败：…」。无 Compose UI 测试。

## Requirements

- R1 仅当目标文本不是「已显示文本的前缀延长」（一次性跳到整段）时打字。SSE 前缀变长：界面跟 `streamingText`，不再节流。
- R2 `ChatScreen` 第一次组合、历史轮、`ANIMATOR_DURATION_SCALE == 0`、失败气泡：直接全文。不从第一个字重打已流式看过的内容。
- R3 节奏：约每 24ms 一个 Unicode 标量；若剩余字数会使动画超过约 800ms，加大步长，总时长不超过 800ms。缩放为 0 则跳过。
- R4 目标与已显示不是前缀关系（整段替换）：直接跳到目标，避免乱码。
- R5 不改 LoopEngine / SSE / TTS / `listKey` / 进入动效 / 工具卡 / 用户气泡。

## Out of scope

- M3 双主题、等宽/ANSI 换肤、mosaic。
- SSE 节流、失败文案打字、用户气泡打字。
- Compose UI 测试。

## Technical notes

- 纯函数（shown / target / reduceMotion / firstFrame / elapsed）放 `:feature:chat`，`ChatUiStateTest` 覆盖。`AgentBubble` 或 ChatScreen 用 `remember(listKey)` 记下已显示前缀。
- 播报按钮仍对完整 `item.text`（`finalAnswer`），不要等打字结束才允许播报。

## Acceptance Criteria

- [x] AC1 空 → 整段终答：按字（或加速步长）显现，800ms 内到全文。
- [x] AC2 流式前缀变长：立即显示最新前缀，无额外延迟。
- [x] AC3 历史轮、缩放=0、失败、首次进入已完成窗口：直接全文，不重打。
- [x] AC4 `ChatUiStateTest` 覆盖上述谓词；`./gradlew :feature:chat:testDebugUnitTest` 过。
