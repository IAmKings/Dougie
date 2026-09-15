# 任务页按窗口分组并定位语句

## Goal

底栏「任务」按对话窗口分节（全部展开、节标题吸顶）。点某一轮卡片后打开该窗口，并滚到那一句用户话，而不是会话底部。

## User value

先按窗口切开长列表；再点具体一轮时，对话页停在那句，不必在长记录里另找。

## Background

已归档 `09-15-chat-thread-default`、`09-15-chat-thread-llm-context` 留下：`HistoryItem.conversationId`、`listRecent(50)`（全局新在前）、点击目前只传会话 id、忙时 `MainActivity` no-op。对话页升序，`listKey` 为 `{taskId}:user`；`openConversation` 把该会话最新一条 seed 到 `_task`，换窗口时 `shouldFollowChatFeed` 会滚到最后一条。无 `conversations` 表、无 SQL `conversation_id` 列。本刀改写父任务「任务页不分组 / v1 不滚到被点气泡」。

## Requirements

- R1 任务页按 `conversationId` 分节，默认全部展开，节标题吸顶。`"default"` → 「默认会话」；其它窗口 → 「对话 2」「对话 3」…（不展示 UUID）。非 default 编号按这 50 条里该窗口最旧一条的先后；默认会话不占数字，因此即使只有一个额外窗口也叫「对话 2」。
- R2 节顺序：`listRecent` 里该 id 第一次出现的顺序（最近活动的窗口在上）。节内仍新在前，一轮一张卡（摘要、状态、循环、工具链）。
- R3 点卡：`openConversation(conversationId)` + 打开对话 + 滚到 `{taskId}:user`，不要跟到窗口底部。忙时 no-op。`_task` 仍 seed 该会话最新一条（播报/重试仍作用在最后一轮）；focus 只改滚动。
- R4 底栏「对话」（不点任务卡）不写入、不消耗定位目标，仍恢复上次滚动 / 跟新消息。
- R5 仍用 `listRecent(50)` 分组，不扫全库，不加列/表，不改 LLM，不把 focus 写入 prefs / `snapshot_json`。

## Out of scope

- 折叠/手风琴、会话重命名、侧栏、全文搜索。
- `PRD.md` §11.3 耗时 / Provider / 可展开 Loop。
- `09-15-intent-rule-e-followup`。

## Key Decisions

- 任务页分组，且全部展开 + sticky 节标题（用户采纳）。
- 点卡定位到对应用户句，不滚到会话底。
- 节内保持执行日志新在前；对话页仍旧在上；两页顺序可以不一致。

## Acceptance Criteria

- [ ] AC1 两个窗口各至少两轮：任务页两节，同窗口的卡在同一节，最近活动的窗口在上。
- [ ] AC2 `"default"` 标题为「默认会话」；另一窗口为「对话 n」，不含 UUID。
- [ ] AC3 点非最后一轮的卡片 → 对话页停在该用户句附近（不在列表最底）。忙时点击不切走。
- [ ] AC4 底栏回「对话」不强制滚到刚才点过的那句。
- [ ] AC5 `:feature:history` 分节映射单测；Chat 在指定 `listKey` 时不 `shouldFollow` 到底（JDK 17）。
