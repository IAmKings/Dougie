# 默认会话 + 新对话 + 窗口多轮

## Goal

打开对话页就是**当前会话的滚动记录**（多轮用户句 + 回复），而不是只显示正在执行的那一个 `AgentTask`。安装后一条默认会话；用户可「新对话」清空窗口并换新的 `conversationId`。本刀不把历史轮次送进 LLM。

## User value

连续聊天时能看见刚才说了什么；想切断上下文时点「新对话」；想回去时到底栏「任务」点那一轮。模型是否记得，留给 `09-15-chat-thread-llm-context`。

## Depends on

无代码依赖。父任务：`09-15-chat-conversation-window`。必须在 `09-15-chat-thread-llm-context` 之前完成。

## Background

- 单一数据源今日是 `TaskManager.task: StateFlow<AgentTask?>`（`.trellis/spec/frontend/state-management.md`）。`toChatUiState()` 只根据这一条拼 `ChatItem`。
- `submit` 在终端态之后新建任务并覆盖 `_task`（`TaskManager.kt`）。
- `recoverInterrupted`（`TaskStore.kt`）只处理 `listRecent(1)` 非终态；已完成任务不进对话页。
- `AgentTask` 无 `conversationId`。`TaskSnapshotCodec` 已 `ignoreUnknownKeys`，新字段可缺省。
- 底栏「任务」`HistoryRoute` 已有 `onOpenChat`，但 `HistoryCard` 不可点，不能带 `conversationId` 回去。

## Requirements

- R1 `AgentTask` 增加 `conversationId: String`（默认常量 `"default"`）。`submit` 写入**当前会话** id。`TaskSnapshotCodec` 编解码；旧 JSON 缺字段或空白 → `"default"`，升级用户的旧任务出现在默认窗口。
- R2 当前会话 id 持久化（`PreferenceStore` 独立 key，**不**进 `ProviderSettings.save`，避免「保存配置」误清）。缺省为 `"default"`。新对话写成新 UUID。
- R3 对话页 transcript = 当前会话内任务按时间升序：已完成/失败轮展示用户句 + 终答或失败文案；进行中的那一轮仍用现有 Thinking / Tool / Confirm / 流式规则。旧轮工具卡默认折叠或隐藏，避免列表过长（可只保留终答）。
- R4 冷启动：读当前会话 id，加载该会话 transcript；仅当 `recoverInterrupted` 得到的任务属于**当前**会话时才 `seed` 到 `TaskManager`。其它会话里被标失败的任务照样落库，不把窗口切走。
- R5 「新对话」：当前会话已有气泡时需确认；确认后换新 UUID、写入 prefs、窗口变空（`isEmpty`）。进行中禁用（与 `inputEnabled` 相同）。空窗口再点：no-op。
- R6 `TaskStore` 提供按 `conversationId` 读取（或等价：能列出该会话全部任务）。禁止用全局 `listRecent(50)` 冒充当前会话。
- R7 一轮一任务不变：忙时丢弃第二发；`retry` 仍新 `taskId`，**同一** `conversationId`。
- R8 本刀不改 `OpenAICompatibleProvider.buildRequestJson` / `ChatPromptAssembler.localPrompt` 的 messages 历史。
- R9 「任务」页仍是 `listRecent` 扁平列表（文案、状态、循环、工具链不变），**不**改成按会话分组。卡片可点：把 prefs 中当前会话 id 写成该任务的 `conversationId`，再 `onOpenChat`，对话页加载该会话**全部**轮次（升序），不是只显示被点的那一条。v1 不强制滚到该气泡。记忆 / 设置 / 开发者不是入口。对话顶栏不做「最近会话」。
- R10 当前 `TaskManager` 任务非终态时，任务卡片不可切会话（与 R5 新对话禁用一致）。
- R11 不新增 `conversations` 表（除非实现时发现 prefs+快照不够，须先回到 parent 改范围）。
- R12 若给 `dougie_tasks.db` 加列：必须 additive `onUpgrade`，禁止再 drop。仅改 `snapshot_json` 则可不升版本。

## Out of scope

- LLM 近期对话、token 滑动窗口（下一子任务）。
- 对话页内最近会话、会话侧栏、重命名、按会话分组的任务页。
- MemoryGate / 语义记忆 / sqlite-vec。
- 合并 `AgentTask`、改 intent 捷径、改确认卡策略。

## Key Decisions

- 启动刀包含窗口 + 会话身份 + 新对话；LLM 失忆可接受。
- `conversationId` 在模型与 codec；当前指针在 prefs。
- 点任务卡片打开**整段**会话，不回放单条。v1 不滚到被点气泡。
- 旧快照无 id = 默认会话，避免升级后窗口空。

## Acceptance Criteria

- [ ] AC1 默认会话连续发送两轮（等第一轮 COMPLETED 再发第二轮）：对话页同时显示两轮 UserMessage 与对应 AgentMessage；第二轮进行中第一轮气泡仍在。
- [ ] AC2 冷启动（无未完成任务、当前会话已有已完成任务）：打开对话页能看到该会话历史，不是空空态。
- [ ] AC3 「新对话」确认后窗口空；随后发送的任务 `conversationId` 与上一会话不同；在「任务」点上一会话任一条任务后，对话页展示该会话**全部**已有轮次（至少两轮时能看到未被点中的那一轮），当前会话指针已切换。
- [ ] AC3b 任务进行中点其它会话的卡片：不切换、不取消当前 Loop。
- [ ] AC4 任务进行中：「新对话」与发送均不可用。
- [ ] AC5 缺 `conversationId` 的旧快照出现在默认会话 transcript（JVM codec / store 测试）。
- [ ] AC6 `recoverInterrupted` 仍把最新非终态标失败且不调 LLM；该任务若不属于当前会话，对话页不切换会话。
- [ ] AC7 云端/端侧单轮 prompt 与本刀之前相同（无历史 messages）；`:core:llm` 现有拼装测试不因「多轮 UI」而改期望。
- [ ] AC8 `:core:model` / `:core:runtime` / `:feature:chat` 相关单测通过（JDK 17）。

## Open questions

无。与根 `PRD.md` 的表结构差异见父任务 Alignment。
