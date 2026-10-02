# 动作验证层 — 技术设计

## Boundary（模块归属与依赖方向）

| 模块 | 新增/改动 | 约束 |
|---|---|---|
| `:core:model` | `ToolResult.verified`、`ToolTraceEntry.verified`、`UserFacingErrors.VERIFY_FAILED` | 纯数据；零新依赖；禁 `android.*` |
| `:core:tool` | `ForegroundProbePort`、`ForegroundVerifier`、`FakeForegroundProbe`、`AppIntentTool` 验证接入 | 纯 JVM；可依赖 model；Fake 放 main 源码（既有惯例） |
| `:core:runtime` | `LoopEngine` verify 分支 + 捷径重试；`ToolPass.Success` 透传 verified | 纯 JVM；不感知 Android |
| `:tool:accessibility` | `AccessibilityForegroundProbe`（薄适配器） | sideload-only；实现 core:tool 接口 |
| `:app` main | `ForegroundProbeBridge` + `DougieApplication` 接线 | 不得 import sideload 类型 |
| `:app` sideload | `ChannelTools` 填充 bridge | Play `ChannelTools` 零改动 |

依赖方向不变：`model ← tool ← runtime ← app`；accessibility 只依赖 `core:tool`。

## Contract（verified 三态）

- `true`：确定性观察确认效果已发生（前台包名 == 期望包名）。
- `false`：已检查且未生效。工具返回 `isFatal=false` 的 `ToolResult`，JSON 为 `{"ok":false,"error":VERIFY_FAILED,"verified":false}`——它是可重规划的观察，不是终态错误。
- `null`：未验证（无探针/动作无后置条件）。行为与今日完全一致，成功 JSON 不加键（Play 渠道字节不变）。

`verified` 镜像进 `ToolTraceEntry`，重试预算从 trace 计数推导，不新增 `AgentTask` 字段。

## Verification 数据流（app_intent）

```
executeToolPass
  → AppIntentTool.execute
      1. 前置检查不变（isAppForeground / 幂等命中直接返回）
      2. launchView ok
      3. 期望包名 = 显式 package 参数 ?: canonical URI "package:$pkg" 之 $pkg；http/geo → null
      4. probe == null 或期望包名 == null → verified=null，原 JSON 原样返回
      5. ForegroundVerifier.awaitForeground(expected, probe, 5000, 250)
           true  → JSON 注入 "verified":true → 写 IdempotencyStore → 返回
           false → 返回 {"ok":false,"error":VERIFY_FAILED,"verified":false}，isFatal=false，不写幂等
```

- 验证发生在写幂等缓存**之前**：失败结果不缓存，重试才会真实重发（AC6）。
- 5s 轮询 < LoopEngine `toolTimeoutMs` 15s，单次 pass 内不冲突。

## Loop FAIL 分支（core/runtime）

1. `executeToolPass` 成功路径：trace 写入 `verified`，`ToolPass.Success` 增加 `verified` 字段透传。
2. LLM 路径（`run` 主循环）：`ToolPass.Success` 且 `verified==false` → 正常 `loopCount+1` 继续；下一轮 THINKING 前，若 trace 中 `verified==false` 计数 ≥ `MAX_VERIFY_FAILURES(2)` → `fail(VERIFY_FAILED)`。observation 回灌无需新通道（ChatPromptAssembler 已注入 resultJson）。
3. 捷径路径（`completeFromNamedShortcut`）：`pass.verified==false` → 以 `intent-route-retry-1` 重跑一次 `executeToolPass`（新 toolCallId 避开幂等缓存；同一已确认动作的重验，不再触发确认门——由调用点控制，PolicyEngine 本身不改）→ 仍 `false` → `fail(VERIFY_FAILED)`；`null/true` 走原逻辑。
4. `formatFinalAnswer` 调用顺序不变（捷径路径在 verified==false 时提前 fail，不会走到 ok==true 文案门）。

## Wiring（app）

- `ForegroundProbeBridge`（app main，object + `@Volatile var provider: ForegroundProbePort?`）：沿用 `DougieAccessibilityService.instance` 的静态持针先例，解决「main 源集不能 import sideload 类型」的时序问题（bridge 在 ChannelTools.register 时被填充，晚于 AppIntentTool 构造）。
- `DougieApplication`：`AppIntentTool(..., foregroundProbe = { ForegroundProbeBridge.provider?.currentForegroundPackage() })`（fun interface SAM 转换）。
- sideload `ChannelTools`：`AndroidGesturePort()` 提为局部变量，同时喂给 `TapSwipeTool` 与 `AccessibilityForegroundProbe(gesture)`，register 时写入 bridge。
- Play：bridge 恒 null → probe lambda 返回 null → 全链路 verified=null。

## Compatibility

- 旧持久化数据：两个新字段均有默认值，data 层若手工映射 ToolTraceEntry 需同步补列（实现时 grep 确认）。
- `LocalToolContractEval` 14 条冻结集：不新增工具、不改 jsonl/ALLOWED_TOOLS/LOCAL_TEACH_NAMES；app_intent 结果 JSON 仅在 sideload 真机 verified 时多一个键，契约评测不校验该键。
- `FakeLlmProvider`/`LocalToolCallParser` 依赖 `resultJson != null` 判协议态，verified 字段不影响。

## Rollback

按 Stage 分四段提交（model / tool / runtime / app+accessibility）。任一 Stage 失败可独立 revert；JSON 键注入仅在探针非空时发生，revert app 接线即回到今日行为。

## Do not

- 不得让 LLM 充当验证器（验证只做确定性本机检查）。
- 不得把 verified=false 缓存进 IdempotencyStore。
- 不得在 `:core:*` 引入 `android.*`；不得在 Play 源集 import sideload 类型。
- 不得为过测试放宽 5s 轮询或跳过失败路径断言。
