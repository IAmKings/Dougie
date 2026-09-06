# Design: 有对话 LLM 则跳过 MiniRBT

## Routing

`SelectingLlmProvider` 增加只读谓词（名称以实现为准），语义：

```
cloudConfigured() || (local != null && localReady())
```

`DougieApplication`：`skipIntentShortcut = { provider.<该谓词> }`。

`LoopEngine` 仍只读 lambda，不读 `llm.isLocal` 决定是否 classify（测试里 `SpyLocalLlm.isLocal == true` 仍要能测短路径）。

| 云端配置 | 本地对话包 | MiniRBT | 对话 |
|---------|-----------|---------|------|
| 是 | * | 跳过 | 远程 |
| 否 | 就绪 | 跳过 | 本地（无工具） |
| 否 | 未就绪 | 跑 | 拦截/失败；命中则模板 |

## Compatibility

- 「现在几点了」在 Super 下改为远程 tool/口答，不再省一次出境。
- 「截个屏」在有 LLM 时不再 `LOCAL_INTENT` 钉图。
- 无 LLM 时短路径、L2 确认卡、附件满截屏 Halt 不变。

## Follow-up

LiteRT 发出 `ToolCall` 后，本地「现在几点」才能打到 `time`；本设计不预埋引擎 API。
