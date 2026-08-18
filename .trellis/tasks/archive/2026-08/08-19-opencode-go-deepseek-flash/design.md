# Design — OpenCode Go + DeepSeek V4 Flash 预设

## Boundary

- `:core:model` `LlmVendors`：唯一预设表。
- `:data:preferences` `ProviderSettings.DEFAULT_*`：保持 OpenAI，不改存储 schema。
- `:feature:settings`：下拉已 `forEach(LlmVendors.ALL)`，`setVendor` 已填 URL/模型/maxTokens；一般只随 ALL 出现新项。
- `:core:llm` `OpenAICompatibleProvider`：不改请求字段；用现有 `chatCompletionsUrl`。

## Contracts

| Preset id | label | baseUrl | defaultModel | defaultMaxTokens |
|-----------|-------|---------|--------------|------------------|
| `openai`（默认） | OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | 2048 |
| `deepseek` | DeepSeek | `https://api.deepseek.com/v1` | **`deepseek-v4-flash`** | 2048 |
| `opencode-go` | OpenCode Go | `https://opencode.ai/zen/go/v1` | `deepseek-v4-flash` | 2048 |

HTTP：`POST {trimEnd('/')} /chat/completions`，`Authorization: Bearer <saved key>`，`model` 原样。

`idForBaseUrl` 用 `normalizeBaseUrl` 精确匹配 `preset.baseUrl`，因此 Go 与官方 DeepSeek 不会撞车。

## Data flow

设置选预设 → 表单本地态（未保存）→ **保存配置** → `PreferenceStore`。`submit` 读当前 prefs 进 `CloudLlmConfig`。无迁移任务。

## Compatibility

- 已选 DeepSeek 且磁盘 model 仍为 `deepseek-chat`：继续发旧 id，直到用户改模型或重选预设。
- 自定义 URL 指向 Go 网关：`resolvedVendorId` 应变为 `opencode-go`。

## Trade-offs

- 不发 `thinking`：Flash 默认思考由服务端决定；Dougie 工具循环保持现合同。
- max_tokens 仍 ≤8192：与现夹紧一致，不按 384K 输出窗口扩。

## Rollback

删 `OPENCODE_GO` 并从 `ALL` 去掉；把 DeepSeek `defaultModel` 改回（不建议，别名已死）。prefs 无需迁移回滚。
