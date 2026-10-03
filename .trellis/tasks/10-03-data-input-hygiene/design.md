# 数据与输入卫生 — 技术设计

## 1. Schema 迁移（TaskDbHelper）

- `DB_VERSION = 2`。
- `onCreate`：idempotency 建表直接含 `created_at INTEGER NOT NULL DEFAULT 0`；其余表不变。
- `onUpgrade(db, oldVersion, newVersion)`：`if (oldVersion < 2) ALTER TABLE idempotency ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0`。删除原 DROP 逻辑；注释写明「未来版本在此追加增量步骤，禁止 DROP」。
- v1 遗留行 created_at=0 → 首次 prune 即被清（等价于一次性清理历史幂等行，安全：跨重启不重放任务，恢复路径统一翻 FAILED）。

## 2. 惰性清理（data/tasks）

两个 store 各自持有 `@Volatile private var lastPruneAtMs = 0L`：

```kotlin
private fun maybePrune() {
    val now = System.currentTimeMillis()
    if (now - lastPruneAtMs < PRUNE_INTERVAL_MS) return
    lastPruneAtMs = now
    try { db.delete(...) } catch (_: Exception) { /* 清理失败不影响主路径 */ }
}
```

- `SqliteIdempotencyStore.put`：先 `maybePrune()` 再插入；清理 `created_at < now - 24h`；PRUNE_INTERVAL_MS = 1h；IDEMPOTENCY_TTL_MS = 24h。
- `SqliteAuditLog.record`：先 `maybePrune()` 再插入；`DELETE FROM audit_log WHERE id NOT IN (SELECT id FROM audit_log ORDER BY id DESC LIMIT 500)`；AUDIT_MAX_ROWS = 500（与 listRecent coerceIn 上限一致）。
- 清理异常静默（有注释）：卫生清理永不阻断工具调用/审计主路径。

## 3. 级联删除（SqliteTaskStore）

- `deleteByConversation`：既有事务内、删除 agent_tasks 之后，对 `ids` 执行 `db.delete("idempotency", "idempotency_key LIKE ?", arrayOf("$id%"))`（逐 id；UUID 无 LIKE 通配符字符）。
- `deleteByTaskId`：包 `beginTransaction`，删除 agent_tasks 行 + 同 key 前缀的 idempotency 行。

## 4. Sanitizer 上限（core/runtime）

```kotlin
ToolParamType.STRING -> {
    if (prim.content.length > MAX_STRING_CHARS) throw AgentException(UserFacingErrors.INVALID_TOOL_ARGS)
    JsonPrimitive(prim.content)
}
ToolParamType.OBJECT -> {
    if (value.toString().length > MAX_OBJECT_CHARS) throw ...
    if (jsonDepth(value) > MAX_OBJECT_DEPTH) throw ...
    value
}
```

- 常量：`MAX_STRING_CHARS = 10_000`、`MAX_OBJECT_CHARS = 64_000`（> js/py 自身 32KiB 限制）、`MAX_OBJECT_DEPTH = 8`。
- `jsonDepth`：递归JsonObject/JsonArray 计深，标量=0。
- 拒绝（而非截断）：截断会静默改语义（如短信正文被剪）。
- 既有行为保持：缺参数/类型不可强转回退默认值或拒绝的逻辑不动。

## 5. 结构化重复检测（core/llm）

```kotlin
fun isRepeatOfSuccessfulCall(task: AgentTask, call: LlmEvent.ToolCall): Boolean {
    val incoming = canonicalJsonOrNull(call.argsJson)
    return task.toolTrace.any { trace ->
        trace.status == ToolTraceStatus.SUCCESS && trace.toolName == call.name &&
            (if (incoming == null) trace.argsSummary.trim() == call.argsJson.trim()
             else canonicalJsonOrNull(trace.argsSummary) == incoming)
    }
}
```

`canonicalJsonOrNull` = parse 失败返回 null；`JsonElement.equals` 结构化（键序无关）。主源码当前无调用方，纯 API 卫生。

## 6. 测试与验证边界

- core/runtime（ToolCallSanitizerTest）与 core/llm（LocalToolCallParserTest）新增用例，全 JVM。
- data/tasks 无测试源集：SQL 行为（TTL 命中、级联删除、行数上限）列入真机走查清单（`adb shell run-as com.dougie.app.sideload` 查库），本任务 AC 以编译 + checkChannelLeak + 真机清单覆盖，PRD 已声明。

## Rollback

三个 Stage 独立提交（data / runtime / llm），各自可 revert；DB 迁移是单向的（ALTER 加列无害，回滚代码后多余列不碍事）。

## Do not

- 不恢复 onUpgrade 的 DROP 行为；不在 data 层引入测试依赖；不改幂等键拼接格式；不动冻结契约。
