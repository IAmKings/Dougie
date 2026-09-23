# 常驻规则 — 设计

## Boundary

规则是偏好，不是记忆事实，也不是工具。`:core:llm` 只接收已经决定要注入的字符串。是否启用由 `:data:preferences` 保存，`:feature:settings` 编辑，`:app` `DougieApplication` 在构造 Provider 时读当前值。

`:core:*` 继续零 `android.*`。截断函数放在 `:core:model` 的 `StandingRules`（`:data:preferences` 不能依赖 `:core:llm`），供设置与组装器共用。`limit` 保留编辑中的空格；`clamp` 再 `trim`，只用于进模型的文本。

## Contract

```text
StandingRules.clamp(raw) -> String
```

- 空白折叠：`trim`。空则 `""`。
- 非空则取前 240 个 Unicode 码点（`codePointCount` / `offsetByCodePoints`），不用 UTF-16 `length`。

`ProviderSettings` 增加：

- `standingRulesEnabled: Boolean = false`
- `standingRules: String = ""`

键：`standing_rules_enabled`、`standing_rules`。`PreferenceStore.setStandingRules(enabled, text)` 先 `clamp` 再 `save(copy(...))`。`read()` 缺键时用默认。`save()` 把这两项写入同一加密文件。

设置页「保存配置」沿用主题的做法：提交时从 `store.settings.value` 抄 `standingRulesEnabled` 与 `standingRules`，不从一份可能过期的云端表单草稿里拿。输入变化和开关变化直接调用 `setStandingRules`。

## Prompt

`ChatPromptAssembler.systemPrefix` 增加参数 `standingRules: String = ""`。

- 空白：前缀与现在相同。
- 非空：在 `IDENTITY` 段落后追加 `\n\n常驻规则：\n` + `clamp` 结果，然后再接工具清单、附件、记忆。

`OpenAICompatibleProvider` 与端侧 `ChatLlmProvider` 各增加 `standingRules: () -> String = { "" }`。调用 `systemPrefix` / `localPrompt` 时传入。`DougieApplication` 的 lambda：开关关则 `""`，开则 `settings.standingRules`。

`localPrompt` 在拼 `LOCAL_TOOL` 协议或闲聊后缀之前已经使用 `systemPrefix`，所以规则自然位于协议之前。身份锁定（`LOCAL_IDENTITY_LOCK`）保持在用户句子之后，不替代常驻规则。

## Compatibility

旧偏好文件没有这两个键，读出来是关 + 空，行为与 v0.1.5 相同。不迁移、不升数据库。

## Trade-off

一段自由文本，而不是四栏。占位文案承担「写什么」的说明。240 码点是为了端侧上下文：近期对话预算只有约 800 字，规则再长会把工具协议挤掉。

开关关闭时仍保存正文，避免误关丢稿。「恢复默认」才清空。

## Rollback

去掉注入 lambda（恒为 `""`）即回到旧前缀。偏好键可留着，不读就没有行为。

## Logging

不记录规则正文、不放进 `AuditLog`、`snapshot_json`、通知。测试只断言组装结果，不打印前缀。
