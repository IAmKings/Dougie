# Implement: Chat 意图短路径

## Checklist

1. `LoopEngine` 增加可选 `IntentPort? = null`。`run` 在 LLM while 之前按 `design.md` 分支；复用现有 Policy / timeout / Audit / `updateLastTrace`（抽私有 `executeRegisteredTool` 若能少复制则抽，禁止第二套确认卡逻辑）。
2. 小函数：`IntentHit` → 工具名；Tool JSON → 中文 `finalAnswer`。放 `:core:runtime`，无 Android。
3. `DougieApplication` 把现有 `intentPort` 传入 `LoopEngine`。`:cli` 不传。
4. `LoopEngineTest`（`:core:runtime:test`）：
   - 高置信 time / battery：COMPLETED、模板文案、spy LLM `stream` 次数为 0。
   - 缺包、未就绪：不 `classify`，LLM 被调用。
   - 低置信、`unknown`、`query_calendar`：LLM 被调用。
   - SCREEN 附件：即使高置信 time 也走 LLM。
   - 分类抛 `AgentException`：走 LLM，任务不因分类失败。
   - 短路径 Audit：toolName `time`/`battery`。
5. 不改 `IntentClassifierTool` JSON 测试；不提交意图权重。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:runtime:test :core:tool:test :app:checkChannelLeak
```

触及 Play 资源时再 `:app:testPlayDebugUnitTest`。

## Risky files

- `core/runtime/src/main/kotlin/com/dougie/core/runtime/LoopEngine.kt` — 勿让短路径绕过 `PolicyEngine` 或把 intent 写入 `AuditLog`。
- `app/.../DougieApplication.kt` — 只接线，不改 Tool 注册表。

## Before start

- `prd.md` / `design.md` 已收敛。实现批准前不改产品代码。
