# Design — Phase 0 skeleton Chat Fake Loop

## Boundaries

```text
:app                Android Application，组装 Fake 依赖，NavHost 仅 Chat
:feature:chat       Compose UI，收集 TaskUiState StateFlow
:core:runtime       LoopEngine / TaskManager（JVM）
:core:model         AgentTask、TaskStatus、LlmEvent、ToolCall 数据类（JVM）
:core:llm           LlmProvider 接口 + FakeLlmProvider（JVM）
:core:tool          Tool 接口 + FakeBatteryTool（JVM）
```

禁止：`:core:*` 依赖 `android.*`；`:feature:chat` 直接读电池/日历。

## Contracts

### AgentTask（最小字段）

`taskId`, `input`, `status`, `loopCount`, `maxLoops=8`, `toolTrace: List<ToolTraceEntry>`, `finalAnswer?`, `lastError?`

`ToolTraceEntry`: `toolCallId`, `toolName`, `argsSummary`, `resultJson?`, `status`

### LlmProvider

`suspend fun generate(context: LoopContext): LlmResponse`  
`LlmResponse` = `FinalAnswer(text)` | `ToolCall(id, name, argsJson)`

Phase 0 不做 streaming。

### Fake LLM 剧本

对任意用户输入：

1. loop 0：`ToolCall(battery, {})`
2. 收到 tool result 后 loop 1：再一次 `ToolCall(battery, {})`（证明循环，而非一次成功就结束）
3. 第二次 result 后 loop 2：第三次 `ToolCall(battery, {})`
4. 第三次 result 后：`FinalAnswer` 引用三次结果中的电量字段

这样满足「3 次 Tool Loop」，与产品演示句「还有多少电」兼容。

### LoopEngine

- `CoroutineDispatcher` 可注入（测试用 StandardTestDispatcher）。
- 每次状态变化 `persist` 可先做成内存 `MutableStateFlow<AgentTask>`。
- Tool 执行前生成 `toolCallId`；Fake Tool 忽略副作用，仍接收幂等键参数。

## Data flow

```text
User send
  → TaskManager.create(input)
  → LoopEngine.run on Default dispatcher
  → FakeLlm.generate
  → FakeBatteryTool.execute
  → StateFlow<AgentTask>
  → ChatViewModel.map to bubbles
  → ChatScreen
```

## UI mapping（Stitch Chat）

| 设计元素 | Compose |
|---|---|
| TopAppBar 头像+标题+「出境策略: 仅本地」 | `DougieTopBar`，logo 用 `Dougie-logo.svg` |
| 用户气泡右对齐、描边 primary | `UserBubble` |
| 「思考中... [KISS 循环 n]」呼吸动画 | `ThinkingChip`，`status-thinking` `#566ab2` |
| Tool 卡片左侧状态条 | `ToolCallCard` |
| 底栏输入 + 发送 | `ChatInputBar` |
| 空列表 | 空态文案 + 可点击示例 chip |

Color tokens in `:feature:chat` 从 Stitch HTML 抄一份 `DougieColors`（primary `#3D5198`，primaryContainer `#566AB2`，surface `#F8FAF9`）。不在本任务改 `PRD.md` §11.4。

## Trade-offs

| 选择 | 原因 | 放弃 |
|---|---|---|
| 内存 StateFlow 不做 Room | Phase 0 生死线是状态机，不是持久化 | 杀进程恢复 |
| Fake 三次都调 battery | 实现简单且 UI 有真实卡片 | 三种不同 Fake Tool |
| 单 Chat 路由 | 第一刀可验收 | Permission/Memory 导航 |

## Compatibility / rollback

- 新建工程，无迁移。
- 回滚：删除 Gradle 模块与 `settings.gradle.kts` 引用；文档任务不受影响。
- 不引入 Play flavor；后续加 flavor 时 `:core` 无需改契约。
