# Implement — Phase 0 skeleton Chat Fake Loop

## Checklist

1. 创建 Gradle 工程：root `settings.gradle.kts`、`gradle/libs.versions.toml`、AGP + Kotlin + Compose BOM；`minSdk` ≥ 26。
2. 建立空模块 `:core:model`、`:core:llm`、`:core:tool`、`:core:runtime`、`:feature:chat`、`:app`；`:core:*` 使用 `java`/`kotlin` JVM plugin，不用 `com.android.library`。
3. 实现 model：`AgentTask`、`TaskStatus`、`LlmResponse`、`ToolCall`、`ToolResult`。
4. 实现 `LlmProvider` + `FakeLlmProvider`（三步 ToolCall 剧本）。
5. 实现 `Tool` + `FakeBatteryTool`（固定 JSON：`battery_percent` / `charging`）。
6. 实现 `LoopEngine` + 内存 `TaskRepository`；对外 `StateFlow<AgentTask?>`。
7. JVM 测试：3 次 Tool Loop → COMPLETED；取消路径可后置但不要阻塞。
8. `:feature:chat`：主题色、TopBar、气泡、Thinking、Tool 卡、输入栏、空态；ViewModel 映射状态。
9. `:app`：`MainActivity` + 注入 Fake + 应用图标（`Dougie-logo.svg` 转 vector/png）。
10. `./gradlew :core:runtime:test :app:assembleDebug`。

## Validation

```bash
./gradlew :core:runtime:test :app:assembleDebug
```

手动：安装 debug APK → 点空态示例或输入「我现在手机还有多少电？」→ 确认三条 Tool 卡 + Final。

## Review gates

- core 模块 `build.gradle.kts` 无 `android` plugin。
- Chat 不出现单独「正在思考」而无 loop/tool。
- 文案 Dougie，不是 Waku。

## Rollback points

- 步骤 1–2 失败：停在空模块，不写业务。
- 步骤 7 失败：禁止做 UI 美化，先修状态机（PRD 生死线）。
- 步骤 10 失败：不宣称 Phase 0 完成。

## Follow-up before start

- 规划摘要已给用户，等待明确批准后再 `task.py start`。
- 不在本任务改根目录 `PRD.md` 令牌表。
