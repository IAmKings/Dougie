# Phase 2 Local Memory FTS

## Goal

让 Dougie **记住用户说过的长期事实**，并在之后的对话里用 **FTS5 / 关键词** 找回至少一条，用户能在 Memory UI 查看来源、编辑、删除、清空、关闭记忆。

## Background

- 依赖：已归档 Phase 1b（流式 + 双 Tool）。
- 产品：`PRD.md` §7、§15 Phase 2、§20.1 `MemoryStore`。
- 视觉：`design/其他功能/Semantic_Memory_-_Facts__History__*.html`；文案用 Dougie。

## Requirements

- **R1** `MemoryStore` 接口在 JVM `:core:memory`：`search` / `store` / `delete` / `clear` / `list`。写入必经 `MemoryGate`。`embedding` 字段可空，本阶段不实现向量。
- **R2** Android `:data:memory` 用 Room + **FTS4 或 FTS5** 持久化事实。`:core:*` 仍无 Android plugin。JVM 测试用 `InMemoryMemoryStore`。
- **R3** Memory Gate 廉价过滤（非每轮 LLM）：禁用记忆则不写；密码/Token/API Key/卡号等敏感不写；用户说「不要记住」不写；重复内容去重；通过则持久化，带来源（`sourceTaskId` + 用户原句摘要）。
- **R4** Loop 在调用 LLM 前 `search(userInput)`，把命中事实注入上下文（system 或独立 memory 消息）。Token 预算可先截断为最多 N 条 / 总字符上限。
- **R5** 任务 `COMPLETED` 后对「用户输入 + 最终回答」跑 Gate（至少能从「我叫小明」类自我介绍抽出一条事实）。
- **R6** `:feature:memory`：列表、来源、编辑、删除、清空、关闭记忆开关。Chat 底栏「记忆」进入该页。产品名 Dougie。
- **R7** 关闭记忆后：不再写入、不再注入上下文；已存事实仍可在 UI 查看直到用户删除。

## Acceptance Criteria

- [ ] JVM：写入「我叫小明，住在上海」后，search「小明」或「上海」能命中，且带 source。
- [ ] JVM：含 `sk-` / `密码是` 的候选被 Gate 拒绝。
- [ ] JVM：memoryEnabled=false 时 store 为 no-op，search 仍可被上层跳过注入。
- [ ] Chat 路径：LoopContext / AgentTask 带上 retrieved memories；OpenAI 请求 body 含这些内容（MockWebServer 可断言，或单元测试 Context 组装）。
- [ ] Memory UI 能列出/删除/清空；开关写进 PreferenceStore。
- [ ] `./gradlew :core:memory:test :core:runtime:test :app:assembleDebug` 通过。

## Out of scope

- 向量 / embedding 模型
- 完整 conversations/messages 多表（本阶段一张 facts 表足够证明找回）
- LLM 冲突仲裁 UI
- 日历等新 Tool
- Task History / Room 任务恢复（Phase 4）

## Constraints

- 记忆默认仅本地，不随 Egress 上传全文库；注入上下文时仍受 `EgressGateway` 约束（用户已授权才出云）。
- 不 log 记忆全文到 Logcat。
