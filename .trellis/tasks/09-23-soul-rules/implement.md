# 常驻规则 — 执行

## Order

1. `:core:llm` 增加 `StandingRules.clamp` 与 `systemPrefix` / `localPrompt` 参数。默认 `""`，现有调用点先不用改行为。
2. 补 `ChatPromptAssemblerTest`：空白前缀仍以身份开头且不含「常驻规则」；非空含原文且身份在前；241 个汉字码点被截断；`localPrompt` 在工具协议打开时规则仍在 JSON 协议句之前。
3. `ProviderSettings` + `PreferenceStore` 读写与 `setStandingRules`。若已有 JVM 偏好测试则补一条；没有 Android 双的测试则不新开 Robolectric，截断由 llm 测试覆盖。
4. `OpenAICompatibleProvider`、`ChatLlmProvider` 增加 lambda，默认 `""`。现有测试保持通过。
5. `DougieApplication` 把开关与文本接进两个 Provider。
6. `SettingsViewModel` / `SettingsScreen`：顶部卡片、立即保存、保存配置抄当前值、恢复默认。文案用中文。
7. 确认没有把正文写进日志。

## Validation

`JAVA_HOME` 指向 JDK 17：

```bash
./gradlew :core:llm:test :data:preferences:test --tests com.dougie.core.llm.ChatPromptAssemblerTest
```

偏好模块没有对应测试类时，以 `:core:llm:test` 为准，并人工看设置页：默认关、写入、关闭、恢复默认、再点保存配置。

## Rollback

先还原 `DougieApplication` 的 lambda，再还原组装器参数。偏好字段留在数据类里也不会改变提示词。

## Do not

- 不改 `LOCAL_TEACH_NAMES`。
- 不把规则放进 `AgentTask` 或 Room。
- 不新增设置测试框架。
