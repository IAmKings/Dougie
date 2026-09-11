# Design: 本地闲聊闸门

## Boundary

全部逻辑放在 `:core:llm` `ChatPromptAssembler`（JVM-pure）。`:core:llm` 不得依赖 `:core:runtime` 的 `IntentRouteAnswers` / MiniRBT。直球词表是 prompt 合约，不是意图短路径。

`ChatLlmProvider.promptFor` 继续只调 `localPrompt`。解析后是否发出 `ToolCall` 必须与「本轮是否附了 JSON 协议」同一谓词，避免 2B 在身份问仍吐 `{"name":"time"}` 时被 Loop 执行。

云端 `systemPrefix(task, fullDescriptors)` 不改。

## Data flow

```
task.input + descriptors + toolTrace
        │
        ▼
localToolProtocolActive?
  traces(resultJson) 为空
  AND localTeachable 非空
  AND looksLikeLocalToolAsk(input)
        │
        ├─ true  → systemPrefix(task, taught) + 现有 localToolProtocol(taught) + user
        └─ false → systemPrefix(task, empty) + 闲聊后缀 + user
                    looksLikeLocalIdentityAsk 时再在用户话之后追加 LOCAL_IDENTITY_LOCK
                    有 tool 结果时：仍走现有 AFTER（中文作答、不要再 JSON），且不附清单
                    「你好」与工具问不加锁
```

`looksLikeLocalToolAsk`：对原始 `input` 做 `contains`（必中词 + 近邻词）。不规范化掉「了」——「现在几点了」已含「几点」。

`looksLikeLocalIdentityAsk`：你是谁 / 你是什么 / 什么模型 / 哪个模型 / 你叫什么。

闲聊后缀（local-only，不进 `IDENTITY`）：直接用一两句中文回答；不要输出 JSON；不要罗列工具；不要自称其它模型或厂商。IDENTITY、闲聊后缀、身份锁都不要出现 OpenBMB / MiniCPM / Qwen 字样（2B 会复读）。

身份锁（仅身份问、用户话之后）：`只回答：我是 Dougie，运行在用户手机上的本地优先助手。`

## Execute gate

`ChatLlmProvider` 在 `LocalToolCallParser.parse` 之后：

1. 重复成功调用 → 现有 TextDelta「已获得工具结果。」
2. 解析到 ToolCall 且 `localToolProtocolActive` → 发 ToolCall
3. 否则 TextDelta（`stripLeadingQuestion`）。纯 JSON 闲聊可能闪过一行 JSON，本轮接受；**不得执行**。

## Compatibility

- 「你好」仍进 LLM，但不再带工具表（修 0.6B 复读清单）。
- 「现在几点了」仍进 LLM（MiniRBT 保持 skip），prompt 因「几点」带全表 JSON。
- 「把X念出来」/精确打开应用仍 `LOCAL_INTENT`，不经过 prompt。
- 附件/截屏 metadata 仍可出现在 `systemPrefix`（含 SCREEN 行里的 `screen_match` 英文说明）；不因此自动附 `可用工具` 全表。
- Play / catalog / 激活 / `ThinkingConfig(false)` 不变。

## Spec

实施后改 `.trellis/spec/backend/directory-structure.md` Shared Chat prompt assembler：`localPrompt` 仅在直球闸门且无工具结果时教 inventory+JSON；闲聊后缀不点名厂商；身份问在用户话之后锁 Dougie；`ChatLlmProvider` 无协议则不发 ToolCall。`quality-guidelines.md` 的 `ChatPromptAssemblerTest` 一行补上身份问不加协议、锁在用户话之后。
