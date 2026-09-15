# Implement: 任务卡耗时与 Provider

## Order

1. `AgentTask.startedAt` / `endedAt`；`CompletionPath.toUserLabel()`。`AgentTaskTest`：default null、`copy` 保留。Debug 改用 `toUserLabel()`，`DebugUiStateTest` 不变。
2. `TaskSnapshotCodec` 可选 long；`TaskStoreTest` roundtrip + 旧 JSON 无键仍 null。
3. `stampEndedAtIfTerminal()`：`TaskManager.submit` 写 `startedAt`；emit/`persist`、`markCancelled`、`recoverInterrupted` 写 `endedAt`。补 submit / recover / cancel 断言。
4. `formatTaskDuration` + `toHistoryItem` 两标签；`HistoryItemTest`。`HistoryCard` 元信息行。
5. spec：`database-guidelines.md`（snapshot 时间戳、不 bump、不用 `updated_at`）；`directory-structure.md` / `state-management.md`（History 映射）；`logging-guidelines.md`（仍不 dump snapshot）；`quality-guidelines.md`（HistoryItemTest 覆盖耗时/Provider）；Debug 标签改指向 `toUserLabel()`。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :core:model:test :core:runtime:test :feature:history:testDebugUnitTest :feature:debug:testDebugUnitTest
```

JDK 17。真机：新完成的卡有耗时+Provider；杀进程中断的卡有耗时（若本刀之后提交）；升级前旧卡无耗时；待确认任务确认后耗时含等待。

## Do not

- bump `dougie_tasks.db`、SQL 列、`updated_at` 冒充耗时。
- LoopEngine 每一处 COMPLETED/FAILED 手写时钟。
- Chat 气泡耗时、展开 Loop、小时文案。
- log 输入 / `snapshot_json`。
- `task.py start` 未获规划摘要批准前改产品代码（本文件仅规划）。
