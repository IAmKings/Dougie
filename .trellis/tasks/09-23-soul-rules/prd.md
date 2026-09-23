# 常驻规则

## Goal

用户在设置里写一段本地规则。开关打开且文本非空时，云端和端侧的下一轮都把这段话放在默认身份之后。关掉或恢复默认后，回复只看到现在这句身份。

## User value

可以规定「回答先给结论」或「不要主动建议打开应用」，不必每次在输入框里重复。

## Confirmed facts

- 默认身份是 `ChatPromptAssembler.IDENTITY`（`core/llm/.../ChatPromptAssembler.kt`）：「你是 Dougie，运行在用户手机上的本地优先助手。用中文回答。」云端 `OpenAICompatibleProvider` 与端侧 `localPrompt` 都从 `systemPrefix` 开始。
- 设置存在 `PreferenceStore` 的 `EncryptedSharedPreferences`。主题、终端风是立即写入；「保存配置」必须把这些当前值抄回去，避免被旧表单冲掉（`.trellis/spec/frontend/state-management.md`）。
- 组装后的提示词禁止进 Logcat（`.trellis/spec/backend/logging-guidelines.md`）。常驻规则同样禁止。

## Requirements

- R1 设置页滚动区顶部有卡片「常驻规则」：开关「使用常驻规则」（默认关）、多行输入、字数 `n/240`、按钮「恢复默认」。占位提示写明可以写：我是谁、回答风格、不许做什么、常用流程。说明文案：「关闭或空白时仍用默认身份。立即生效，不必保存配置。」
- R2 文本按 Unicode 码点截断到 240，存在加密偏好里。开关和文本立即保存。「保存配置」必须带上当前这两项，不能把它们写空。
- R3 开关关，或去掉空白后为空：系统前缀与今天一致，以 `IDENTITY` 开头，不含「常驻规则」。
- R4 开关开且非空：`IDENTITY` 仍是第一段，下一段以「常驻规则：」开头接用户原文。云端 `systemPrefix` 与端侧 `localPrompt` 使用同一段。端侧工具协议打开时，规则仍在协议说明之前。
- R5 「恢复默认」关闭开关并清空文本。只关开关时保留文本，再次打开仍是那段话。
- R6 不把规则写入日志、审计、通知或任务快照。

## Acceptance Criteria

- [x] AC1 默认安装或恢复默认后，`systemPrefix` 与现有 `ChatPromptAssemblerTest` 的身份断言一致。
- [x] AC2 打开开关并写入「回答先给结论」后，云端前缀和端侧 `localPrompt` 都包含这七个字，且仍以默认身份开头。
- [x] AC3 关掉开关后，同一段存储文本不再出现在前缀里。注入 lambda 在开关关闭时传空串；空白串测试覆盖前缀不含「常驻规则」。
- [x] AC4 第 241 个码点不会进入模型前缀，界面计数停在 240。
- [x] AC5 先改规则再点「保存配置」，规则仍在；日志与审计里没有这段正文。`save()` 从当前 `settings` 抄两项。没有新增日志点。偏好读写没有 JVM 测试（依赖 Android Keystore）。

## Out of scope

- 四个独立表单、技能市场、多文件插件、按会话覆盖、把规则放进记忆页或 SOUL 文件目录。
- 改工具表、改端侧关键词表。
