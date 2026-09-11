# Design: 本地 Chat 引擎预热

## Boundary

预热只存在侧载 `:tool:chatllm` + `DougieApplication`。Play `ChannelHooks` 无 provider，调用点必须 no-op。`:core:llm` 可放纯谓词 `shouldWarmLocalEngine(cloudConfigured, localReady) = !cloudConfigured && localReady`，与 `SelectingLlmProvider.isLocal` 对齐（忽略 `local == null`，Play 上 local 为 null 时 Application 根本不调 warmup）。

`ChatLlmEngines.tryCreate` 仍是同步 `initialize()`。预热 = 把这次调用挪到 Default 线程，抢在第一句 `stream()` 之前。

## Triggers

`DougieApplication` `appScope` 收集 `settings` + `activeChatSku`（或在 onCreate 先踢一脚再 collect）：

| 条件 | 动作 |
|------|------|
| `!cloudConfigured && sku 可定位` | `ChatLlmProvider.warmup()` → Default 上 `ensureEngine()`，吞掉异常 |
| `cloudConfigured && inFlight == 0` | `releaseIfIdle()` 关引擎省显存 |
| SKU/路径变化 | 现有 `ensureEngine` 关旧开新；预热 collect 会再踢一脚 |

`cloudConfigured` = `allowCloud && apiKey.isNotBlank()`，与 `SelectingLlmProvider` 相同。

## Engine slot

`warmup()` 与 `stream()` 都进 `ensureEngine()`。sku+path 已匹配则直接返回。`inFlight > 1` 时不切换（现网）。预热不计为「用户生成」，不要把 warmup 的 inFlight 设成挡住 SKU 切换——warmup 若占用 inFlight，设置换档会被误拒。

**Decision**：`inFlight` 只由 `stream()` 加减。`warmup()` 只拿 `lock` 调 `ensureEngine()`，不改 `inFlight`。换档时若真有 stream，仍拒绝关引擎。

## Failures

预热 GPU/CPU 都失败：引擎保持 null。`localChatReady` 仍 true。用户发送再走一遍 tryCreate；仍失败 → 现有 `CHAT_ENGINE_NOT_READY`。不在启动时弹窗。

## UI

不改 Chat。发送若撞上未完成的 warmup，THINKING 等到锁释放（2B 剩余 < 15s，60s 超时仍够）。

## Logging

不打 model path、sku 文件名以外的路径、prompt。Debug 若需要只允许「warmup ok/fail」无路径。

## Spec

`directory-structure.md` `:tool:chatllm` 行：进程启动（及 SKU/云端谓词变化）在 Default 上预热激活档；云端配置则释放空闲引擎。
