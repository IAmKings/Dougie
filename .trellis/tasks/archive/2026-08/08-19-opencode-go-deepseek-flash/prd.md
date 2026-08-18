# OpenCode Go 接入 DeepSeek V4 Flash 默认配置

## Goal

设置页增加 **OpenCode Go** 可选预设，一键填入 Go 网关 URL 与 `deepseek-v4-flash`；官方 DeepSeek 预设的默认模型同步改为 Flash。新装仍默认 OpenAI。用户只需粘贴对应 API Key 并保存。

## Background

厂商预设在 `LlmVendors`（`:core:model`）。`ProviderSettings.DEFAULT_*` 现为 OpenAI / `gpt-4o-mini`。选预设会填 `baseUrl` / `model` / `maxTokens` 并保留 API Key；改 URL 偏离预设则 `vendorId` 变为 `custom`。`OpenAICompatibleProvider` POST `{baseUrl}/chat/completions`，body 含 `model`、`stream`、`max_tokens`、`messages`、`tools`；`max_tokens` 夹在 16..8192。出站仍受 `allowCloud`。

官方 DeepSeek 预设仍为 `https://api.deepseek.com/v1` + `deepseek-chat`。该别名已于 2026-07-24 退役。现模型 id：`deepseek-v4-flash`、`deepseek-v4-pro`。OpenCode Go 网关为 `https://opencode.ai/zen/go/v1`，chat 路径 `/chat/completions`；CLI 的 `opencode-go/deepseek-v4-flash` 仅 provider 前缀，请求 body 的 `model` 必须是 `deepseek-v4-flash`。

## Requirements

- **R1** 新增预设 `id=opencode-go`，label `OpenCode Go`，插入 `ALL`（建议紧挨 DeepSeek 之后）。设置下拉随 `LlmVendors.ALL` 列出。
- **R2** 选中 OpenCode Go 后：`baseUrl=https://opencode.ai/zen/go/v1`，`model=deepseek-v4-flash`，`maxTokens=2048`（`defaultMaxTokens`）。
- **R3** Provider 对上述 URL 发 `/chat/completions`；body `model` 为 `deepseek-v4-flash`，禁止 `opencode-go/` 前缀。
- **R4** 不内置 Key。Go Key 与官方 DeepSeek Key 都走现有保存/鉴权。无 Key / 未开出站行为与现云厂商相同。
- **R5** `LlmVendors.idForBaseUrl` / `resolvedVendorId` 将规范化后的 Go URL 识别为 `opencode-go`（含尾斜杠）。
- **R6** `ProviderSettings.DEFAULT_VENDOR_ID/URL/MODEL` 仍指向 OpenAI。不迁移、不覆盖已写入 EncryptedSharedPreferences 的厂商/URL/模型。
- **R7** 官方 DeepSeek 预设：`baseUrl` 不变，`defaultModel` 改为 `deepseek-v4-flash`。仅重新点选「DeepSeek」才刷新表单模型；磁盘上已存的 `deepseek-chat` 保持原样直到用户改或重选。
- **R8** JVM 单测覆盖 R2/R5/R6/R7。

## Out of Scope

- 本仓库 `opencode.json` / OpenCode CLI `/connect`。
- `thinking`、`reasoning_effort`、`--variant`、把 `max_tokens` 上限抬过 8192。
- OpenCode 网页「Enable models hosted in China」开关或设置页长文案；403/401 走现有 LLM 失败文案。
- 真机付费 Go 订阅联调（可受阻，不挡合并）。
- 静默把已存 `deepseek-chat` 改写成 Flash。

## Acceptance Criteria

- [x] 设置厂商列表含 OpenCode Go；点选后 URL/模型/maxTokens 为 R2。
- [x] 点选 DeepSeek 后模型为 `deepseek-v4-flash`，URL 仍为官方 `.../v1`。
- [x] 新装（无 prefs）默认仍为 OpenAI / `gpt-4o-mini`。
- [x] `idForBaseUrl("https://opencode.ai/zen/go/v1/") == "opencode-go"`。
- [x] 请求 URL 为 `{baseUrl}/chat/completions` 且 JSON `model` 无 `opencode-go/` 前缀。
- [x] 无 Key 或出站关闭时不新增特殊错误路径。

真机（PJZ110 play debug）2026-08-19 人工验收可用。

## Deferred (next week, separate task)

本地模型导入：SAF 选文件 → 官方 catalog SHA-256 校验 → 写入 `filesDir` layout；设置行提供 **测试** 按钮，验证该本地模型是否可加载/可推理。不并入本任务。

## Key Decisions

- **D1** 新装默认仍 OpenAI；Go 仅为可选预设。
- **D2** 官方 DeepSeek 默认模型改为 Flash，URL 不变。
- **D3** 不改 HTTP 合同；不把 CLI provider 前缀写入 body。
- **D4** 已保存配置不静默覆盖。
- **D5** 中国区托管：本轮不产品化（无开关、无新文案）。
