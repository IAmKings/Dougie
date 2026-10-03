# 任务终态一致性 — 技术设计

## 1. markCancelled（TaskManager）

```kotlin
private suspend fun markCancelled() {
    val current = _task.value ?: return
    if (isTerminal(current.status)) return
    val failed = current.copy(status = FAILED, lastError = CANCELLED, ...).stampEndedAtIfTerminal()
    _task.value = failed
    withContext(NonCancellable) {   // 已取消协程里 withContext 会立即抛 CancellationException
        persist(failed)
        reloadTranscript()
    }
}
```

`_task.value` 更新留在 NonCancellable 外（StateFlow 不挂起）。catch 块保持 `markCancelled(); throw e` 语义。

## 2. recoverInterrupted（TaskStore.kt 顶层函数）

```kotlin
suspend fun recoverInterrupted(store: TaskStore): List<AgentTask> {
    val stale = store.listRecent(Int.MAX_VALUE).filter { !it.isTerminalRow() }
    val recovered = stale.map { it.copy(status = FAILED, lastError = INTERRUPTED, streamingText = null, confirmDeadlineAt = null).stampEndedAtIfTerminal() }
    recovered.forEach { store.upsert(it) }
    return recovered
}
```

- 终态判断 `status == COMPLETED || FAILED` 与 `TaskManager.isTerminal` 一致（顶层私有小函数，避免跨类暴露）。
- 返回顺序 = listRecent 顺序（最新在前）；调用方 `lastOrNull { conversationId 匹配 }` seed。
- 副作用说明：恢复行的 `updated_at` 被刷新会跳到历史列表顶部——纯外观，不处理。
- 一次性全表 decode：仅启动时执行一次，量级 = 任务总数，可接受；列索引优化属另一任务。

## 3. persist 节流（TaskManager）

- 构造参数 `persistIntervalMs: Long = 250L`，放参数表末尾（默认值不破坏既有调用）。
- emit 回调：`_task.value` 照旧即时更新；`shouldPersist(task)` 通过才 `persist`：

```kotlin
private fun shouldPersist(task: AgentTask): Boolean {
    if (isTerminal(task.status)) return true            // 终态必落盘
    val now = System.currentTimeMillis()
    if (lastPersistAtMs == 0L || now - lastPersistAtMs >= persistIntervalMs) {
        lastPersistAtMs = now
        return true
    }
    return false
}
```

- 首个 emit（`lastPersistAtMs==0`）必落盘：保证「提交」尽快可见于 DB。
- persist 在 loop 协程内顺序执行，无并发写乱序问题；不加锁。
- 用 `System.currentTimeMillis` 而非虚拟时钟：节流是性能优化、非正确性逻辑；测试通过把 interval 设为 0 / Long.MAX_VALUE 两个极端绕开时钟依赖。
- `markCancelled` 直接 persist，不走节流。

## 4. verified JSON helper 去重（core/tool）

新文件 `VerifiedResultJson.kt`：

```kotlin
object VerifiedResultJson {
    fun withFlag(json: String, verified: Boolean): String  // 解析失败原样返回
    fun fromJson(json: String): Boolean?                   // 缺键/解析失败 → null
}
```

`AppIntentTool`/`SmsComposeTool` 删除各自的 private 实现并改调用。两个工具的现有测试即回归网（字节级断言在）。

## 5. toolCallId 常量（LoopEngine companion）

`MATCH_THEN_TAP_MATCH_CALL_ID` / `MATCH_THEN_TAP_TAP_CALL_ID` / `INTENT_ROUTE_CALL_ID` / `INTENT_ROUTE_RETRY_CALL_ID`，替换 4 处字面量。纯重命名级改动。

## 6. bridge 接线测试（app/src/testSideload）

- 位置 `app/src/testSideload/kotlin/com/dougie/app/ChannelToolsBridgeWiringTest.kt`——变体专属测试源集，play 变体不编译该文件（play 的 ChannelTools 是空实现）。
- Context 用 `ContextWrapper(null)`：`scriptPrivileged=false` 分支不触碰 context；android.jar stub 允许构造。
- 用例：consent=true → 两个 bridge provider 非 null；consent=false → 不填充（先置 null 再断言仍 null）。
- 运行任务 `:app:testSideloadDebugUnitTest`。

## Rollback

单 commit（core/runtime + core/tool + app 测试互不独立：helper 去重触及工具、接线测试触及 bridge——合为一个可整体 revert 的提交；若需分段，runtime 与 tool/app 各一段）。

## Do not

- 不改 `_task`/`transcript` 的 UI 更新时机；不把节流做成协程 timer（保持顺序落盘）。
- 不动 14 条冻结契约；不加依赖（mock 库明确不引入）。
