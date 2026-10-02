# 树状态提取器与短信编辑验证 — 执行计划

## Order

1. **Stage 1 — core/tool 状态提取与验证**
   - 新建 `TreeSnapshotPort.kt`（`TreeSnapshot` data class + fun interface + `FakeTreeSnapshotPort` 可脚本化）。
   - 新建 `TreeVerifier.kt`（`awaitContains` 轮询 + `contains` 空白不敏感包含；delay 累计计时）。
   - 测试 `TreeVerifierTest`（AC5 全部轮询语义 + 匹配语义）。
   - 验证：`./gradlew :core:tool:test`。

2. **Stage 2 — SmsComposeTool 接入**
   - 构造器加 `treeProbe: () -> TreeSnapshotPort? = { null }`、`verifyTimeoutMs = 5_000`、`verifyPollMs = 250`；按 design 数据流接入；复用 AppIntentTool 的 withVerified/verifiedFromJson 模式（私有实现）。
   - 测试：AC1（含空白变体命中）、AC2（未命中 + 不缓存 + JSON 无用户内容）、AC3（无探针字节一致）、AC4（重放 + 新键真实重发）。
   - 验证：`./gradlew :core:tool:test`。【回滚点 1】

3. **Stage 3 — 无障碍实现与接线**
   - `tool/accessibility`：`AccessibilityTreeSnapshotPort`（BFS，上限 300 节点，service/root null → null）。
   - app main：`TreeProbeBridge`；`DougieApplication` 的 SmsComposeTool 传 `treeProbe = { TreeProbeBridge.provider }`。
   - sideload `ChannelTools`：填充 bridge。
   - 验证：`./gradlew :tool:accessibility:test :app:checkChannelLeak`。【回滚点 2】

4. **Stage 4 — 全量**
   - `./gradlew :core:model:test :core:tool:test :core:runtime:test :tool:accessibility:test :app:checkChannelLeak`（AC6）+ `:core:llm:test`（冻结契约）。

## Validation

- 各 Stage gradle 命令见上；真机侧（sideload）验证「编辑器打开且正文可见 → 已验证；杀掉短信应用重试路径 → 诚实失败」不进 CI，记任务笔记。

## Rollback points

- Stage 2 前 revert Stage 1；Stage 3 前 revert Stage 2；app 接线单独 revert 即恢复 Play 现状。

## Do not

- 不打印快照/正文到任何输出；不新增日志。
- 不 amend；不 push；work commits 先行、簿记提交在后。
