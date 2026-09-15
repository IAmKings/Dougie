# Design: 删除会话窗口

## Boundary

| 模块 | 职责 |
|------|------|
| `:core:runtime` `TaskStore` | `deleteByConversation(id): Int` 扫全表、删匹配行；`default` 直接 return 0 |
| `InMemoryTaskStore` / `SqliteTaskStore` | 内存 map；SQLite `DELETE FROM agent_tasks WHERE task_id IN (…)` |
| `TaskManager` | `deleteConversation(id)`：busy 或 default → return；删 store；清 title；若 `currentId==id` 则 `openConversation(DEFAULT)` |
| `:feature:history` | 非默认节「删除」+ 确认框；`onDelete(id)` |
| `:app` | 接线：busy 门、`taskManager.deleteConversation`、History refresh |

不加列、不 bump `dougie_tasks.db`。`:core:*` JVM 纯净。

## Store

`listByConversation` 同款解码过滤。收集 `task_id` 后一次 `DELETE … WHERE task_id IN (?,?,…)`。空 IN 不执行。返回删除条数。

## UI

与「改名」同一 sticky `Row`：默认只有改名；其它节 `改名` + `删除`（`DougieColors.Error`）。对话框：标题「删除会话」，正文「将删除「{显示名}」中的全部任务，且无法恢复。」按钮 删除 / 取消。

展开工具步骤、点卡打开对话不变。删除按钮消费点击，不 `openConversation`。

## Don't

- SQL `conversation_id` 列、DB bump。
- 删 Memory / audit / idempotency。
- log 自定义名。
- Chat 顶栏删除。
