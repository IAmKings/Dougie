# Implement

## Checklist

1. `ChatPromptAssembler`：`looksLikeLocalToolAsk` / `looksLikeLocalIdentityAsk` / `localToolProtocolActive`；`localPrompt` 按谓词决定是否把 taught 传入 `systemPrefix` 与 `localToolProtocol`；闲聊后缀不点名厂商；身份问在用户话之后追加 `LOCAL_IDENTITY_LOCK`；无协议时 after-tool 仍用现有 AFTER，且 `systemPrefix` 不带清单。
2. `ChatPromptAssemblerTest`：AC1–AC4（身份/问候无协议；几点与剪贴板仍全表；`IDENTITY` 干净；「你在哪」不激活；身份问锁在用户话之后，问候/工具问不加锁）。改会受影响的旧断言（带工具的身份类 input 若有）。
3. `ChatLlmProvider`：ToolCall 仅当 `localToolProtocolActive(task, descriptors)`。
4. Spec：`directory-structure.md` Shared Chat prompt assembler + `:core:llm` / `:tool:chatllm` 行；`quality-guidelines.md` 测例描述。
5. 不改 `skipIntentShortcut`、MiniRBT、云端 `systemPrefix`、catalog。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:llm:test
```

真机侧载：0.6B 与 2B 各「你是谁」（AC5）；同一档「现在几点了」（AC6）。不 Logcat prompt。

## Risky / rollback

- 闸门漏「几点」→ 时间问退化成闲聊。测里锁死「现在几点了」。
- 闸门过宽（「打开」）→ 个别闲聊仍带全表；本轮接受。
- 只改 prompt 不改 Provider → 2B 仍可能执行身份问的 `time` JSON。
- 把禁令写进 `IDENTITY` → 污染云端；只放 local 后缀。闲聊后缀/身份锁也不要点名 OpenBMB / MiniCPM / Qwen（2B 会复读）。
- 回滚：还原 assembler 每轮教协议 + Provider 见 JSON 就发 ToolCall。
