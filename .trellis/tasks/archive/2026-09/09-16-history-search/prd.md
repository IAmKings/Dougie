# Chat 检索历史对话并作答

## Goal

用户在 Chat 里用自然语言问过去聊过的内容时，系统检索**全部窗口**里的历史轮次（加上现有记忆事实），把命中交给 LLM 作答，并在 Final 显示引用来源。

## User value

记忆只存 Gate 抽出的短事实。很多讨论从未进记忆，换窗口后 `priorTurns` 也看不见。用户要能问「UNO 项目有哪些关键点？」并得到基于旧聊天的回答。

## Background

- 根 `PRD.md` US-001：搜历史 Conversation →（文档写 FTS）→ Semantic Memory → LLM → UI 来源；无记忆则「未找到」；不云端则不出境。
- 现网：每轮 `retrieveMemories`（最多 5 条 / 800 字，受 `memory_enabled` 开关）；Chat Final 引用 `retrievedMemories.source`。当前窗口已完成轮走 `attachPriorTurns` → `priorTurns`（云端 ≤16，端侧更短）。其它窗口不可见。
- `agent_tasks` 无 FTS、无 messages 表。`dougie_tasks.db` v1 `onUpgrade` DROP 重建。`listByConversation` / `deleteByConversation` 已全表扫 snapshot。禁止 SQL `LIKE` 搜 json。
- 任务页仍是 `listRecent(50)`，无搜索框。本刀是 Chat 问答，不是任务页筛卡。
- 父任务 `09-15-chat-conversation-window` 曾把 US-001 标成有意不做；本刀补上。

## Key Decisions

- Chat 问答检索，不做任务页搜索框。
- 不 bump `dougie_tasks.db`，不加会话四表。历史轮次用全表扫 snapshot，Kotlin 里用与记忆相同的 `searchNeedles` 匹配 **用户句 + 终答**（不匹配 `toolTrace`）。
- 每轮会走 LLM 的发送都检索；全部窗口；当前窗口已进入 `priorTurns` 的轮跳过。
- 无命中不强制「未找到」，Chat 照常作答（与现网记忆空一致）。US-001 该条不做成系统替换句。
- 命中写入 `AgentTask` 并进 snapshot（供 Final 引用在重开后仍在）。`priorTurns` 仍不落库。
- 来源展示复用 Chat 现有引用条：窗口显示名 · 询问摘要。不可点、不跳任务页。不把 UUID 当来源。
- `memory_enabled == false` 仍检索历史对话（任务档案 ≠ 记忆开关）。本地意图短路径不扫全表（那些不调 LLM）。

## Requirements

- R1 `TaskStore` 能按 query 返回已完成且终答非空的任务（全表，newest-first），跳过 `excludeTaskIds`。blank query / 无 needles → 空列表。
- R2 `LoopEngine` 在 `attachPriorTurns` 之后、LLM 循环之前检索；排除本轮 `taskId` 与已挂上的 prior 轮；最多 3 条、总字符 ≤800；截断用户句/终答。注入 prompt 独立块「相关历史对话」，与「Known facts」和「近期对话」分开。
- R3 `ChatPromptAssembler.systemPrefix`（云端 + 端侧）都含该块。不把工具参数/结果写入该块。
- R4 Final 引用 = 记忆 `source` ∪ 历史命中 `sourceLabel`，去重保序。空白来源省略。
- R5 不 log query、snapshot、命中正文、自定义窗口名。
- R6 旧快照缺新字段视为无命中。Codec `ignoreUnknownKeys`。

## Acceptance Criteria

- [x] AC1 另一窗口有已完成轮「UNO 项目关键点…」；当前窗口问相关问题时，云端/端侧 prompt 含该轮用户句或终答摘录，且不含其 `toolTrace`。
- [x] AC2 当前窗口已在 `priorTurns` 里的轮不会作为历史命中再注入一遍。
- [x] AC3 无命中时任务能完成，回复不是固定「未找到」；有命中时 Final 显示来源标签（窗口名 · 摘要）。
- [x] AC4 记忆关闭时仍能命中历史对话。MiniRBT / 说话短路径不因本刀去扫全表。
- [x] AC5 JDK 17：`:core:runtime:test`、`:core:llm:test`、`:core:model:test`、`:feature:chat:testDebugUnitTest` 通过。

## Out of scope

任务页搜索框、ChatGPT 侧栏、规则 E、单条任务删除、tiktoken、§11.7、§7.1 四张表、DB bump、来源点击跳转、强制「未找到」、把历史命中当 `MemoryEntry` 写入记忆库。
