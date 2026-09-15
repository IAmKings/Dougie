# 对话窗口：默认会话与多轮

## Goal

对话页变成**一条默认可滚动的会话**：连续发送不再清空窗口，模型在后续刀能读到本会话近期轮次。执行单位仍是一轮一个 `AgentTask`。产品形态锁定为 **一条默认会话 +「新对话」**，不做 ChatGPT 式多会话列表。

## User value

用户能在同一窗口里接着说「改成四点」「刚才那个名字」，而不必把整段背景再打一遍。需要隔离时点「新对话」。要回到旧会话时走底栏「任务」，不在对话页里翻历史。

## Sequence

对齐此前建议的三步，但「新对话」需要会话身份，故把原第 0 步（窗口）与第 2 步（`conversationId`）合并为**启动子任务**；原第 1 步（LLM 上下文）仍第二刀。没有独立第三实现刀。

1. `09-15-chat-thread-default` — **启动（先做）**。默认会话、`conversationId`、当前会话 transcript、「新对话」。本刀 **不** 改 LLM `messages`。
2. `09-15-chat-thread-llm-context` — 把**当前会话**已完成轮的 user/assistant 滑进云端/端侧 prompt。依赖第 1 子任务的 `conversationId` 与 transcript 语义。
3. **收口（parent 验收，不另开子任务）**：任务页仍是扁平执行日志（可点卡片重返会话，不改成会话分组列表）；不把多轮塞进同一个 `AgentTask`；不改 MemoryGate 来冒充聊天记录。

依赖写在子任务 `prd.md`，不靠目录顺序。不要 `task.py start` 本 parent；批准后 start 启动子任务。

## Background

- `TaskManager.submit`（`TaskManager.kt`）在上一轮 `COMPLETED`/`FAILED` 后新建 UUID `AgentTask`，只带本句 `input`。进行中再发送直接 return。
- `ChatViewModel` 只 map `TaskManager.task`；`AgentTask.toChatUiState()`（`ChatUiState.kt`）只画当前任务。新 `submit` 换掉整页气泡。
- 冷启动 `DougieApplication` 只 `recoverInterrupted`；已完成任务不 `seed`，对话页经常是空的。底栏「任务」`listRecent(50)` 不可点回对话。
- 云端 `OpenAICompatibleProvider.buildRequestJson`：`system` + 一条 `user` + 本轮 `toolTrace`。端侧 `ChatPromptAssembler.localPrompt` 几乎只有本轮 `input`。跨轮仅 `MemoryGate` 短事实 + `retrieveMemories`（最多 5 条 / 800 字）。
- 根 `PRD.md` §5.2 已写 `conversationId`；§7.1 `conversations`/`messages`；§8.1 Recent Conversation。ADR 0002 同样提到 `conversationId`。实现里的 `AgentTask` **没有**该字段；`ContextBuilder` 未落地。
- `dougie_tasks.db` v1：`onUpgrade` drop 重建（`TaskDbHelper.kt`）。v0.1.1 已发出。若加列必须 additive；v1 允许 `conversationId` 只进 `snapshot_json`。
- 前序产品决定：不做 sqlite-vec；不做多会话侧栏。

## Requirements

- R1 会话与任务分层：一轮用户请求仍新建 `AgentTask`；状态机、幂等键 `taskId+toolCallId`、`maxLoops`、确认卡、`recoverInterrupted` 语义不变。
- R2 产品形态：安装后即有一条默认会话；对话顶栏可「新对话」。对话页只显示**当前**会话，没有最近会话、侧栏或返回上一条。v1 **没有**会话重命名、搜索全部消息。
- R3 实施顺序：先窗口与会话身份（启动子任务），再 LLM 上下文（第二子任务）。启动刀验收时模型仍可「失忆」，但窗口必须已是多轮。
- R4 「任务」页保持 `TaskStore.listRecent` **扁平执行日志**，不改成按会话分组的 ChatGPT 列表。它是**唯一**重返旧会话的入口：点一条任务 → 把当前会话指针切到该任务的 `conversationId` → 打开对话页展示该会话**整段**多轮（v1 不强制滚到被点的那一条）。记忆页 / 设置 / 开发者页 / 第五个底栏都不是入口。
- R5 对话正在跑时不能切会话（与不能「新对话」相同）。

## Out of scope

- ChatGPT 式多会话侧栏、对话页内「最近会话」、会话标题编辑。
- 记忆页当聊天档案；第五个底栏「会话」。
- 把多轮消息做进同一个 `AgentTask` / 取消一轮一任务。
- 用 MemoryGate 或 sqlite-vec 代替近期对话。
- 松手语音自动 submit、桌面端、规则 E 评测续作（`09-15-intent-rule-e-followup` 仍独立 planning）。
- 在启动子任务里改云端/端侧 `messages` 拼装。

## Key Decisions

- 默认会话 + 新对话，不做多窗口管理。
- 原建议三步仍是本 parent 的范围；启动切片 = 默认会话 + 新对话 + 窗口多轮。
- `conversationId` 落在 `AgentTask` + `TaskSnapshotCodec`；缺字段 → 常量 `"default"`。当前打开哪条会话用 prefs 独立 key（不进 LLM「保存配置」）。不必先做 `conversations` 表。
- 新对话后 **不能**从对话页回到上一条。重返入口 = 底栏「任务」点卡片（启动刀就要做，否则「新对话」会把窗口变成死胡同）。
- 点任务卡片打开该 `conversationId` 的**整段**会话，不是只回放那一条。v1 不强制滚到被点气泡。
- 任务页不改成会话列表；每条任务仍是一轮执行记录，只是可点。
- 相对根 `PRD.md`：落地的是会话**行为**（`conversationId` + 窗口多轮 + Recent Conversation），不是 §7.1 四张表，也不是 §20.1 的 `ContextBuilder` 接口。

## Acceptance Criteria

- [ ] AC1 启动子任务完成后：同一会话连续两轮发送，对话页同时看得到两轮用户句与回复；「新对话」后窗口空、新任务带新 `conversationId`；在「任务」点上一会话的一条任务，对话页切回该会话**整段** transcript（含该会话其它轮，不是只显示被点的那一条）。
- [ ] AC2 第二子任务完成后：同一会话内后一轮云端请求的 `messages` 含此前已完成轮的 user/assistant（不含旧轮完整 `toolTrace`）；端侧有更短窗口且不把旧轮工具 JSON 当协议。
- [ ] AC3 全程：忙时仍不能并行第二发；杀进程恢复仍只把未完成任务标失败，不自动续跑 LLM。

## Alignment with root `PRD.md`

本 parent 对齐的是「一轮一 `AgentTask` + 会话能续上」的产品意图，**不是**把根文档会话章节一次性做完。三步做完后，下列条款仍未按原文落地（有意缩小，不是漏写启动刀）。

| 根文档 | 三步是否覆盖 |
|--------|----------------|
| §5.2 `conversationId`；每次请求仍新建 `AgentTask` | 是（启动刀）。同节 `createdAt` / `timeoutAt` / `cancellationRequested` 不在本 parent |
| §5.4 `contextBuilder.build` + §20.1 `ContextBuilder` 接口 | **否**。第二刀只改现有 `ChatPromptAssembler` / `buildRequestJson` / `localPrompt` |
| §7.1 表 `conversations` / `messages` / `tool_calls` / `tool_results` | **否**。用 `agent_tasks.snapshot_json` + `conversationId` + prefs 当前指针 |
| §7.4 Conversation Finished → MemoryGate | **否**（已有「一轮结束 ingest」，不改成「整段会话结束才抽取」） |
| US-001 本地搜索**历史 Conversation**（FTS 聊过的项目要点） | **否**。第二刀只把**当前会话近期轮**送进 LLM；不建消息全文检索 |
| §8.1 Recent Conversation | 是（第二刀） |
| §8.2–8.3 8K 预算、滑动窗口、先截 Tool Result、本地 token 估算 | **部分**：第二刀有滑动窗口与汉字估算；不做 tiktoken；不重做 Memory Ranking（仍走现有 `retrieveMemories`）；旧轮不重放 tool 故无「截旧工具结果」 |
| §11.1 / §11.5 当前轮 5 态气泡 | 已有实现；启动刀是**拼接多轮**。旧轮工具卡默认折叠，历史轮不完整重放 §11.1 链 |
| §11.3 任务条目：耗时、Provider、可展开 Loop/Tool Result | **否**。本 parent 只加「可点重返」；现网卡片仍无耗时/Provider |
| §11.7 Chat ↔ Task History 300ms 共享元素 | **否** |
| 「新对话」、一条默认会话、任务页当重返入口 | 根文档**没有**写；本 parent 的产品补丁，与 §5.2 不冲突 |

## Open questions

无。
