# prompt 预算与注入防护 — 执行计划

## Order

1. ChatPromptAssembler：clampToolResult + LOCAL_MAX_INPUT_CHARS + userBlock 合并分支 + LOCAL_AFTER_TOOL_RESULTS 新文案 + UNTRUSTED_DATA_NOTE 注入 systemPrefix。
2. OpenAICompatibleProvider：role=tool content 包 clampToolResult。
3. 测试：新增 3 类（输入保留、clamp、标注）；更新 2 处冲突断言。
4. 验证：`./gradlew :core:llm:test` → 全量 AC6 套件。

## Validation

- 新测试先红后绿（clamp/标注/输入保留在未修复实现上必然红——userBlock 替换输入、无 clamp、无标注）。
- 真机回归清单（最小档）：①「现在几点？另外明天提醒我开会」带工具结果追问场景；② 云端长工具结果（js_eval 大输出）不撑爆上下文。

## Rollback points

- 单提交整体 revert。

## Do not

- 不改冻结契约；不加依赖；不 amend；不 push。
