# Implement — OpenCode Go + DeepSeek Flash 预设

1. `LlmVendors.kt`：新增 `OPENCODE_GO`；`DEEPSEEK.defaultModel = "deepseek-v4-flash"`；`ALL` 在 DeepSeek 后插入 Go。
2. `LlmVendorsTest`：Go URL（含尾 `/`）→ `opencode-go`；DeepSeek 默认模型断言；`resolvedVendorId` 对 Go URL；确认 OpenAI 仍为 `ProviderSettings` 默认（可在 `LlmVendorsTest` 或 prefs 侧断言 `OPENAI.id`）。
3. 如有厂商列表快照测试则更新；否则 Settings 无需改代码。
4. 可选：`OpenAICompatibleProviderTest` 用 MockWebServer 确认 `.../zen/go/v1` + 尾斜杠仍打到 `/v1/chat/completions` 形态（现有 `chatCompletionsUrl` 已拼接，仅在缺覆盖时补一条）。
5. Spec：`directory-structure.md` 厂商预设句补 Go URL + Flash 模型 id；`state-management.md` 预设句点名 OpenCode Go 不改变默认 OpenAI。
6. 验证：`./gradlew :core:model:test :core:llm:test`（`JAVA_HOME` 用 JDK 17）。不跑真机 Go 订阅。

## 风险文件

- `core/model/src/main/kotlin/com/dougie/core/model/LlmVendors.kt`
- 勿改 `ProviderSettings.DEFAULT_*`、勿改 Provider body 字段。

## Rollback

还原上述两处模型/预设；prefs 不动。
