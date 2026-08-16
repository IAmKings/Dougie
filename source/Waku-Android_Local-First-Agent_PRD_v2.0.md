# Waku-Android Local-First Agent
## 可执行产品需求文档（PRD）V2.0

| 项目 | 内容 |
|---|---|
| 文档版本 | V2.0.0 |
| 基于版本 | V1.0.0 |
| 日期 | 2026-08-13 |
| 状态 | 可执行开发基线 |
| 平台 | Android 10+，首期重点适配 Android 13–16 |
| 产品形态 | Android 原生 App + Agent Runtime |
| 核心理念 | Local-first / Permission-first / Tool-driven / Recoverable |
| 首期目标 | 完成一个可稳定运行的本地优先移动 Agent MVP |

---

# 1. 文档说明

本文档是在原 PRD V1.0.0 基础上进行的可执行性审查与工程化补全。

原 PRD 已经明确了 Local-first、KISS Loop、三层 Memory、Native Tools、Accessibility、WorkManager、Cloud/Local LLM 等核心方向，但仍缺少能够直接交给 Android 工程团队实施的：

- MVP 范围与明确的非目标
- 用户故事与验收标准
- Agent 生命周期与状态机细节
- Tool Contract / Tool Permission / Tool Result
- LLM Provider 抽象
- Memory 数据模型与生命周期
- 任务恢复与幂等机制
- Android 后台限制下的执行策略
- 安全模型、隐私边界与审计日志
- 错误分类、重试、超时、取消
- 可观测性与性能指标
- 测试策略
- Definition of Done
- 分阶段交付标准

因此 V2.0 将原方案从“架构设计方向”升级为“可以进入 PoC → MVP → Beta 开发”的 PRD。

> 重要原则：原 PRD 中“所有数据绝不离开设备”的目标与“支持 Cloud LLM”存在天然冲突。V2.0 将其修正为“Local-first + Explicit Data Egress”：默认本地保存；任何发送到云端的数据必须经过明确的数据出境策略。

---

# 2. 产品定位

## 2.1 产品一句话

Waku-Android 是一个运行在 Android 手机上的 Local-first Agent Runtime，使 Agent 可以在本地理解用户上下文、检索本地记忆、调用 Android Native Tools，并在获得授权后使用云端或端侧 LLM 完成复杂任务。

## 2.2 核心价值

### V1 MVP 必须证明四件事

1. Agent Loop 可以稳定运行。
2. Agent 可以使用 Android Tool 完成真实任务。
3. Memory 可以被可靠写入、检索和恢复。
4. App 被杀死、网络中断或 Tool 失败后，任务不会出现不可控状态。

## 2.3 产品原则

| 原则 | 要求 |
|---|---|
| Local-first | 控制流、Memory、Tool Registry、权限状态默认在本地 |
| Permission-first | 敏感操作必须经过权限与策略检查 |
| Explicit Egress | 发送到云端前明确知道发送了什么 |
| Tool-driven | Agent 不直接修改系统状态，只能通过 Tool |
| Recoverable | 每个任务必须能够恢复、取消或进入失败态 |
| Observable | 每次 Tool 调用、错误、耗时可追踪 |
| Minimal Core | Core Loop 保持简单，复杂能力放在模块边界 |

---

# 3. MVP 范围

## 3.1 MVP 必做

### Agent Core

- ReAct/Tool Loop
- StateFlow 状态管理
- 最大 Loop 次数
- 单次 Tool Timeout
- 全局 Task Timeout
- Cancel
- Retry
- Error State
- Task Persistence
- Task Recovery

### LLM

- OpenAI-compatible Provider
- 一个默认 Cloud Provider
- Streaming
- Non-streaming
- Tool Calling
- Token/Context Budget
- Provider 超时与错误处理

### Memory

- Conversation Memory
- FTS5
- Semantic Memory 接口
- Memory Gate
- Fact 写入
- Fact 删除
- Memory 引用来源

### Tools

首期只做：

1. 当前时间
2. 电量
3. 日历查询
4. 日历创建
5. 定位获取
6. App Intent
7. Clipboard Read/Write

SMS、Call、Accessibility 自动化进入 Beta，不作为 MVP 阻塞项。

### UI

- Chat
- Task 状态
- Tool 执行状态
- Permission Center
- Memory Viewer
- Provider Settings
- Task History
- Error/Retry
- Debug/Developer 页面

---

# 4. 明确非目标

MVP 不做：

- 完整自主手机操作系统
- 7×24 无限制后台 Agent
- 自动控制所有第三方 App
- 自动发送短信/拨打电话
- 自动读取全部通知
- 自动操作银行、支付、密码管理器
- 多 Agent 协作
- 云端长期记忆
- 自研大模型
- 完整端侧 LLM 产品化
- 无限上下文
- 自动获得 Android 敏感权限

这些能力可以作为后续版本，但不能成为 MVP 交付条件。

---

# 5. 用户场景

## US-001：本地问答

用户：

> “我昨天和 Agent 讨论过的 UNO 项目有哪些关键点？”

系统：

1. 本地搜索历史 Conversation。
2. FTS 找到候选。
3. Semantic Memory 找到相关事实。
4. LLM 生成答案。
5. 不发送云端则不产生数据出境。

验收：

- 正确找到相关历史。
- UI 能显示引用来源。
- Memory 不存在时明确回答“未找到”。

---

## US-002：创建日历事件

用户：

> “明天下午三点提醒我开项目评审。”

流程：

`User → Intent Parse → Tool Plan → Permission → Calendar Tool → Result → LLM → Final`

如果时间存在歧义：

> “明天下午 3 点是指 15:00 吗？”

Agent 不得自行猜测。

---

## US-003：获取当前状态

用户：

> “我现在手机还有多少电？”

调用：

`DeviceBatteryTool`

Tool 返回结构化 JSON：

```json
{
  "battery_percent": 63,
  "charging": true
}
```

LLM 只负责自然语言表达。

---

# 6. 总体架构

```text
┌─────────────────────────────────────────────┐
│                  UI / Gateway               │
│ Chat / Quick Action / Notification / Tile  │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│                 Agent Runtime               │
│ Task Manager                                 │
│ Loop Engine                                  │
│ Policy Engine                                │
│ Context Builder                              │
└───────────┬────────────┬────────────┬───────┘
            │            │            │
     ┌──────▼─────┐ ┌────▼─────┐ ┌───▼────────┐
     │ LLM Layer  │ │  Memory  │ │ Tool Layer │
     │ Provider   │ │ Manager  │ │ Registry   │
     └──────┬─────┘ └────┬─────┘ └───┬────────┘
            │            │            │
      Cloud/Local       Room       Android API
                       FTS5        Intent
                       Vector       Calendar
                                   Location
                                   Accessibility
```

---

# 7. Agent Runtime

## 7.1 Task 是一级执行单位

每次用户请求创建一个 `AgentTask`。

```text
AgentTask
 ├── taskId
 ├── conversationId
 ├── userInput
 ├── status
 ├── createdAt
 ├── updatedAt
 ├── currentLoop
 ├── maxLoops
 ├── timeoutAt
 ├── cancellationRequested
 └── lastError
```

## 7.2 状态机

```text
IDLE
 │
 ▼
PREPARING
 │
 ▼
THINKING
 │
 ├── FINAL ───────────────► COMPLETED
 │
 ▼
TOOL_PENDING
 │
 ▼
POLICY_CHECK
 │
 ├── DENIED ──────────────► WAITING_USER
 │
 ▼
TOOL_EXECUTING
 │
 ├── RETRY
 ├── FAILED ──────────────► FAILED
 │
 ▼
TOOL_RESULT
 │
 ▼
MEMORY_UPDATE
 │
 ▼
THINKING
```

必须支持：

- Cancel
- Timeout
- Process Death Recovery
- Network Loss
- Tool Failure
- User Permission Denied

---

# 8. Loop Engine 详细规则

## 8.1 主循环

伪代码：

```kotlin
suspend fun run(task: AgentTask) {
    while (task.loopCount < task.maxLoops) {

        ensureNotCancelled(task)
        ensureNotTimeout(task)

        val context = contextBuilder.build(task)

        val response = llm.generate(context)

        when (response) {
            is FinalAnswer -> {
                complete(task, response)
                return
            }

            is ToolCall -> {
                policyEngine.check(response)

                val result = toolExecutor.execute(response)

                persistToolResult(result)

                if (result.isFatal) {
                    fail(task, result.error)
                    return
                }
            }
        }

        task.loopCount++
        persist(task)
    }

    fail(task, MaxLoopExceeded)
}
```

## 8.2 默认参数

| 参数 | MVP |
|---|---:|
| MAX_LOOPS | 8 |
| Tool Timeout | 15s |
| LLM Timeout | 60s |
| Task Timeout | 120s |
| Retry | 2 |
| Context Token Budget | 8K |
| Tool Result 最大长度 | 8KB |

这些参数必须配置化，不允许散落在业务代码中。

---

# 9. Tool Architecture

## 9.1 Tool Interface

```kotlin
interface AgentTool {
    val descriptor: ToolDescriptor

    suspend fun execute(
        arguments: JsonObject,
        context: ToolContext
    ): ToolResult
}
```

## 9.2 Tool Descriptor

```kotlin
data class ToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val riskLevel: RiskLevel,
    val requiredPermissions: Set<Permission>,
    val requiresConfirmation: Boolean
)
```

## 9.3 Risk Level

| Level | 示例 | 用户确认 |
|---|---|---|
| L0 | 当前时间、电量 | 否 |
| L1 | 查询日历、读取剪贴板 | 默认否，可配置 |
| L2 | 创建日历事件、写剪贴板 | 是 |
| L3 | SMS、Call、Accessibility 操作 | 每次确认 |
| L4 | 支付、删除重要数据、账号操作 | MVP 禁止 |

---

# 10. Tool Policy Engine

LLM 请求 Tool 后不能直接执行：

```text
LLM Tool Call
      ↓
Schema Validate
      ↓
Permission Check
      ↓
Risk Check
      ↓
User Confirmation?
      ↓
Tool Execute
      ↓
Audit Log
      ↓
Result
```

任何 Tool 都必须经过 Policy Engine。

---

# 11. Tool 幂等与恢复

对于创建型 Tool 必须提供：

```text
idempotencyKey = taskId + toolCallId
```

例如：

Agent 创建日历事件时，如果 App 在 Tool 执行后被杀死：

恢复任务不能再次创建相同事件。

Tool Adapter 必须能够：

- 查询已有执行记录
- 判断是否已经完成
- 返回历史 Result
- 避免重复副作用

---

# 12. Memory Architecture

## 12.1 Conversation Memory

核心表：

```text
conversations
messages
tool_calls
tool_results
agent_tasks
```

## 12.2 Semantic Memory

核心结构：

```text
memory_id
type
content
embedding
source_message_id
confidence
created_at
updated_at
expires_at
```

## 12.3 Memory 必须可追溯

任何长期事实都必须记录：

```text
Fact
 ├── content
 ├── source
 ├── confidence
 ├── createdAt
 └── updatedAt
```

不能出现：

> Agent 自己记住了某件事情，但用户不知道它从哪里来的。

---

# 13. Memory Gate

Memory Gate 不应简单地“每轮都让 LLM 判断”。

建议：

```text
Conversation Finished
       ↓
Cheap Filter
       ↓
Candidate Fact Extraction
       ↓
Deduplication
       ↓
Confidence Check
       ↓
Persist
       ↓
Embedding
```

## 13.1 不应该记忆

- 一次性临时信息
- 密码
- Token
- API Key
- 身份认证信息
- 银行卡信息
- 用户明确要求“不记住”的内容

## 13.2 用户控制

Memory UI 必须支持：

- 查看
- 编辑
- 删除
- 全部清空
- 禁用 Memory
- 查看来源

---

# 14. Context Builder

Context 不允许无限拼接。

优先级：

```text
System / SOUL
   ↓
Current User Input
   ↓
Relevant Memory
   ↓
Recent Conversation
   ↓
Tool Result
```

Context Builder 必须执行：

- Token Budget
- Sliding Window
- Memory Ranking
- Tool Result Truncation
- Sensitive Data Filtering

---

# 15. LLM Adapter

统一接口：

```kotlin
interface LlmProvider {

    suspend fun generate(
        request: LlmRequest
    ): Flow<LlmEvent>
}
```

Provider 不应被 Loop Engine 直接绑定。

```text
Agent Runtime
      │
      ▼
LlmProvider
 ├── OpenAICompatible
 ├── LocalLlama
 └── FutureProvider
```

## 15.1 数据出境策略

每次请求必须生成：

```text
EgressPolicy
 ├── allowCloud
 ├── allowedDataTypes
 ├── excludedMemory
 ├── excludedTools
 └── userConsent
```

默认：

```text
allowCloud = false
```

如果用户主动配置 Cloud Provider，则明确提示：

> 本次请求可能将输入、必要上下文和 Tool Result 发送至第三方 LLM 服务。

---

# 16. Security

## 16.1 数据分类

| 分类 | 示例 | 默认策略 |
|---|---|---|
| Public | 普通用户输入 | 可配置 |
| Personal | 日程、位置 | 本地 |
| Sensitive | 通讯录、聊天 | 本地 |
| Secret | Token、Password | 禁止进入 Prompt |

## 16.2 API Key

使用 Android Keystore。

禁止：

- SharedPreferences 明文
- SQLite 明文
- Logcat 输出
- Crash Log 输出

## 16.3 日志脱敏

日志禁止记录：

- Prompt 全文
- Token
- API Key
- GPS 精确坐标
- SMS 内容
- 联系人内容

---

# 17. Android 权限策略

权限不是“安装时一次申请”。

采用：

```text
Feature Intent
    ↓
Need Permission?
    ↓
Explain Why
    ↓
Android Permission
    ↓
Grant / Deny
    ↓
Persist Decision
```

Accessibility 必须独立作为高风险能力管理。

---

# 18. 后台执行

原 PRD 中“WorkManager + 断点续传”需要进一步修正。

WorkManager 适合：

- 延迟任务
- 周期任务
- 数据同步
- Memory Embedding
- 可恢复后台工作

不应假设：

> WorkManager 可以保证 Agent 长时间持续运行。

实时 Agent Loop 由前台 App / 前台服务场景负责；后台任务必须接受 Android 系统调度限制。

---

# 19. UI

## 19.1 Chat

必须显示：

```text
User
  ↓
Thinking...
  ↓
Calling Calendar
  ↓
Waiting for permission
  ↓
Tool Result
  ↓
Final Answer
```

用户不能只看到：

> Agent 正在思考……

而不知道发生了什么。

## 19.2 Permission Center

显示：

- 已授权
- 未授权
- 高风险
- 最近使用
- 一键撤销

## 19.3 Task History

每个任务显示：

- 输入
- 状态
- Loop 次数
- Tool
- 耗时
- Provider
- 错误

---

# 20. 错误模型

统一错误：

```text
AgentError
 ├── LlmError
 │   ├── Timeout
 │   ├── RateLimit
 │   ├── InvalidResponse
 │   └── Network
 ├── ToolError
 │   ├── PermissionDenied
 │   ├── Timeout
 │   ├── InvalidArgument
 │   └── ExecutionFailed
 ├── MemoryError
 ├── PolicyError
 └── RuntimeError
```

用户可见错误必须是自然语言。

开发者日志保留结构化错误码。

---

# 21. 可观测性

每个 Task 记录：

```text
task_id
total_latency
llm_latency
tool_latency
loop_count
token_input
token_output
tool_count
memory_hit
provider
error_code
```

核心指标：

| 指标 | MVP 目标 |
|---|---:|
| 简单问答 P95 | < 3s（不含云端异常） |
| Tool 调用成功率 | > 95% |
| Task 恢复成功率 | > 99% |
| Crash-free session | > 99.5% |
| 重复副作用 | 0 |
| 未授权 Tool 执行 | 0 |

---

# 22. 测试策略

## Unit Test

覆盖：

- Loop State Machine
- Context Builder
- Policy Engine
- Tool Schema
- Memory Gate
- Retry
- Timeout
- Cancellation

## Integration Test

覆盖：

- LLM → Tool → Result → LLM
- Room Persistence
- Process Death Recovery
- Network Loss
- Permission Denied

## Instrumentation Test

覆盖：

- Calendar
- Location
- Intent
- Clipboard

## Security Test

覆盖：

- API Key 泄露
- Log 泄露
- 越权 Tool
- Prompt Injection
- 恶意 Tool 参数
- Accessibility 越权

---

# 23. Prompt Injection 防护

由于 Agent 能调用真实 Android Tool，Prompt Injection 是核心风险。

必须：

1. Tool Result 不得被默认视为可信指令。
2. 外部文本不得修改 System Policy。
3. Tool 参数必须 Schema Validate。
4. 高风险操作必须重新进行 Policy Check。
5. 来自网页、通知、第三方 App 的内容标记为 `UNTRUSTED_DATA`。

---

# 24. 技术栈

| 模块 | 推荐 |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Async | Coroutines + Flow |
| DI | Hilt |
| DB | Room + SQLite FTS5 |
| Vector | 抽象 VectorStore，首期可选 sqlite-vec / ObjectBox |
| Network | Ktor Client |
| Serialization | Kotlinx Serialization |
| Background | WorkManager |
| Secure Storage | Android Keystore |
| Local LLM | llama.cpp / ONNX Runtime |
| Testing | JUnit + AndroidX Test |

技术库版本不在 PRD 中硬编码，由项目 `libs.versions.toml` 统一管理。

---

# 25. 项目模块

建议采用：

```text
:app
:core:runtime
:core:model
:core:policy
:core:memory
:core:llm
:core:tool
:core:security
:data:database
:data:preferences
:tool:system
:tool:calendar
:tool:location
:tool:intent
:feature:chat
:feature:memory
:feature:settings
:feature:permission
```

原则：

> Feature 不允许直接访问 Android System API；必须经过 Tool/Repository 边界。

---

# 26. MVP Roadmap

## Phase 0：架构验证

周期：3–5 天

交付：

- Kotlin Skeleton
- AgentTask
- Loop Engine
- Fake LLM
- Fake Tool
- State Machine Test

完成标准：

> 可以通过 Fake Provider 完整执行 3 次 Tool Loop。

---

## Phase 1：Cloud MVP

周期：1–2 周

交付：

- Real LLM Provider
- Streaming
- Tool Calling
- Room
- Chat UI
- Task History
- Retry / Timeout / Cancel

完成标准：

> 用户可以完成真实对话，并可靠调用至少 2 个 Tool。

---

## Phase 2：Local Memory

周期：1–2 周

交付：

- FTS5
- Memory Gate
- Semantic Memory Interface
- Memory UI
- Memory Delete
- Source Tracking

完成标准：

> 用户能够让 Agent 从历史对话中找回至少一个相关事实。

---

## Phase 3：Android Tools

周期：1–2 周

交付：

- Battery
- Calendar
- Location
- Intent
- Clipboard
- Permission Center
- Policy Engine

完成标准：

> 所有 Tool 均经过权限、Policy、Schema 验证。

---

## Phase 4：Reliability

周期：1 周

交付：

- Process Death Recovery
- Idempotency
- Crash Recovery
- Network Retry
- Error UI
- Audit Log

完成标准：

> 在 Tool 执行前后杀进程，不产生重复副作用。

---

## Phase 5：Advanced

进入 Beta：

- Accessibility
- Local LLM
- Notification
- Quick Settings
- Floating Widget
- Scheduled Agent
- Multi-modal Context

---

# 27. MVP 验收场景

必须通过以下 10 个 E2E Case：

### Case 01
普通聊天 → Final Answer。

### Case 02
询问电量 → Battery Tool → Answer。

### Case 03
查询日历 → Calendar Tool → Answer。

### Case 04
创建日历 → Confirmation → Calendar Tool。

### Case 05
拒绝权限 → Tool 不执行。

### Case 06
LLM Timeout → Retry → Error。

### Case 07
Tool Timeout → Retry → Error。

### Case 08
Process Death → Task Recovery。

### Case 09
重复恢复 → 不重复创建事件。

### Case 10
Memory Retrieval → 找到历史事实并展示来源。

---

# 28. Definition of Done

一个 Feature 只有同时满足以下条件才能进入 Release：

- [ ] Unit Test
- [ ] Integration Test
- [ ] Error Handling
- [ ] Permission Handling
- [ ] Cancellation
- [ ] Timeout
- [ ] Logging
- [ ] Privacy Review
- [ ] UI Empty State
- [ ] UI Error State
- [ ] Process Death Review
- [ ] 文档更新

---

# 29. 风险矩阵

| 风险 | 等级 | 应对 |
|---|---|---|
| Android 后台限制 | 高 | MVP 不依赖长期后台运行 |
| Accessibility 不稳定 | 高 | Beta 能力 |
| Prompt Injection | 高 | Policy + Untrusted Data |
| Cloud Privacy | 高 | Explicit Egress |
| Local LLM 性能 | 高 | MVP 后置 |
| Vector DB 兼容性 | 中 | VectorStore 抽象 |
| Tool 重复执行 | 高 | Idempotency |
| Memory 错误事实 | 中 | Confidence + Source |
| Context 超限 | 中 | Budget + Ranking |

---

# 30. V2.0 相比 V1.0 的关键优化

## 原问题 1：目标过于绝对

V1：

> 隐私绝对安全。

V2：

> Local-first + Explicit Data Egress。

原因：只要支持 Cloud LLM，就无法保证所有数据永不离开设备。

## 原问题 2：Loop 只有流程，没有可靠性

V2 增加：

- Task
- Timeout
- Retry
- Cancel
- Recovery
- Idempotency

## 原问题 3：Tool 只有功能列表

V2 增加：

- Schema
- Permission
- Risk Level
- Policy
- Confirmation
- Audit
- Idempotency

## 原问题 4：Memory 只有存储，没有治理

V2 增加：

- Source
- Confidence
- Expiration
- Delete
- User Control
- Sensitive Filter

## 原问题 5：WorkManager 定位过度

V2 明确：

> WorkManager 是后台任务调度器，而不是无限后台 Agent Runtime。

## 原问题 6：Accessibility 过早进入 MVP

V2 将其移动到 Beta，避免第一阶段被 Android 系统行为、厂商差异和安全策略拖慢。

---

# 31. 推荐 MVP 最小闭环

不要一开始实现完整 Mobile Agent。

第一条真正应该跑通的链路是：

```text
User
 ↓
Chat UI
 ↓
AgentTask
 ↓
Loop Engine
 ↓
Cloud LLM
 ↓
Tool Call
 ↓
Policy Engine
 ↓
Battery / Calendar Tool
 ↓
Tool Result
 ↓
Loop
 ↓
Final Answer
 ↓
Room
```

第二条：

```text
User
 ↓
Conversation
 ↓
Memory Gate
 ↓
Fact
 ↓
Embedding
 ↓
Local Memory
 ↓
Future Retrieval
```

第三条：

```text
Task
 ↓
Tool Execution
 ↓
Process Death
 ↓
App Restart
 ↓
Task Recovery
 ↓
Idempotency Check
 ↓
Continue / Complete
```

只有这三个闭环全部跑通后，才值得继续投入 Accessibility、Local LLM、Floating Widget、Notification Agent 等复杂能力。

---

# 32. 最终产品判断

原 V1.0 的方向是成立的，尤其是：

- Local-first
- KISS Loop
- Android Native Tool
- 三层 Memory
- Cloud/Local LLM Adapter
- Room + FTS5
- Compose
- Coroutine/Flow

这些可以作为产品核心。

但 V1.0 更接近“架构蓝图”，还不是严格意义上的“可执行 PRD”。

V2.0 的核心变化是把 Agent 从：

> “一个会调用 LLM 的 Android App”

提升为：

> “一个具有 Task、Policy、Tool、Memory、Recovery、Security 和 Observability 的本地 Agent Runtime”。

因此建议实际开发时严格采用：

**Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4**

而不是直接开发完整 Agent。

其中 **Phase 0 的 Fake LLM + Fake Tool + State Machine** 是整个项目最重要的技术验证点；如果这一层无法稳定运行，后续接入真实 LLM、Accessibility 和 Local Model 只会把问题复杂化。
