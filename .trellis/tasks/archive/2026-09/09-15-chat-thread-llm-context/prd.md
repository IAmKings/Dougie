# LLM 注入当前会话近期轮次

## Goal

同一会话里，后一轮 LLM 请求能看到本会话**已经结束**的用户句与助手终答，从而能处理指代与「接着说」。旧轮完整 `toolTrace` 不重放进 `messages`。

## User value

用户说「改成四点」「他姓什么」时，云端（及预算内的端侧）能接到上一轮，而不必只靠 MemoryGate 里那几类短事实。

## Depends on

- **必须先完成** `09-15-chat-thread-default`：需要稳定的 `conversationId`、当前会话指针、以及「当前会话有哪些已完成任务」。
- 父任务：`09-15-chat-conversation-window`。

未完成上一刀之前不要 `task.py start` 本任务。

## Background

- 根 `PRD.md` §8.1 优先级：System → Current User Input → Relevant Memory → **Recent Conversation** → Tool Result。§8.2–8.3：8K 量级预算、本地估算、先截 Tool Result。
- 现状：`buildRequestJson`（`OpenAICompatibleProvider.kt`）只有本轮 user + 本轮 tools；`localPrompt` 只有本轮 `input`（外加本轮工具结果）。`Known facts:` 仍来自本轮 `retrievedMemories`。
- 端侧小模型 + `localToolProtocolActive` 对夹带旧 JSON 敏感（`.trellis/spec/backend/directory-structure.md` Chat prompt assembler）。
- `LoopEngine` 每轮仍 `search(task.input)`；本刀不改记忆检索。

## Requirements

- R1 拼装输入 = 当前会话内、早于本轮、已 `COMPLETED`（失败轮是否纳入：默认 **不纳入**，避免把「任务失败：…」当成助手内容）的 `input` + `finalAnswer`（终答空白则跳过该轮）。
- R2 云端 `messages` 形状：`system`（现有 `systemPrefix`）→ 交错的历史 `user`/`assistant` → 本轮 `user` → **仅本轮** `tool_calls` / `tool`。历史轮不得附带 `tool_calls`。
- R3 端侧：同样语义写入 `localPrompt` 的「近期对话」块，但窗口更短（建议最多 2–4 轮、更严字符上限）。`localToolProtocolActive` 为 true 时历史必须短、且不得把旧轮 JSON 示例再教一遍。
- R4 预算：统一最坏情况估算（PRD §8.3：约 1 token ≈ 0.75 汉字）。**不**引入 tiktoken，也**不**新建 `ContextBuilder` 类型。超限时先丢更早的历史轮，再截任何历史工具摘要；**不**为了塞历史而丢掉本轮 user 或 `Known facts:`。云端建议先按 8–16 轮或等价字符上限封顶。不做根文档 8K 硬封顶的精确达标证明。
- R5 只注入**当前** `conversationId`。新对话后的第一轮 messages 不含上一会话。
- R6 不把事实原文、完整 prompt 打进 Logcat（现有 logging 红线）。
- R7 `:core:*` 保持 JVM 纯净；拼装单测在 `:core:llm`（及必要的 runtime），不在 androidTest 里调真模型。

## Out of scope

- 改对话 UI transcript、新对话、会话列表（上一刀）。
- 重放旧轮工具结果、把 MemoryGate 放宽成「记住每句聊天」。
- 真 tiktoken、按供应商精确 tokenizer、8K 硬预算验收、`ContextBuilder` 接口、US-001 对历史 Conversation 做 FTS。
- 改 `maxLoops`、确认卡、intent 捷径。

## Key Decisions

- 历史 = 文本轮次，不是工具轨迹。
- 失败轮默认不进 prompt。
- 云端与端侧两套上限，端侧更狠。

## Acceptance Criteria

- [ ] AC1 同一会话：第一轮用户给出一个专有名字并得到终答；第二轮只说「他/她叫什么」类指代时，云端请求 JSON 的 `messages` 含第一轮 user 与 assistant 文本。
- [ ] AC2 「新对话」后第一轮 `messages` 不含上一会话的 user/assistant。
- [ ] AC3 本轮若有 `toolTrace`，历史轮仍无 `tool_calls`；本轮 tool 消息仍紧跟本轮 user。
- [ ] AC4 端侧：历史轮数/字符有上限；协议激活时的单测断言 prompt 不含旧轮工具 JSON 对象。
- [ ] AC5 超预算时更早轮被丢掉，本轮 `task.input` 与 `Known facts:` 仍在。
- [ ] AC6 `:core:llm:test`（及改动到的 runtime 测试）通过（JDK 17）。

## Open questions

无。任务页重返会改当前会话指针；本刀只跟指针，不必另做「打开上一条」。
