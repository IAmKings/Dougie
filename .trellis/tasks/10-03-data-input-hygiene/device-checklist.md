# data-input-hygiene 真机走查清单（data 层 SQL 无 JVM 测试，此清单为验收闭环）

前置：sideload debug 包（`com.dougie.app.sideload`）。

1. DB v2 迁移：先用 v1 版本跑出历史任务 → 覆盖安装本版本 → 确认历史任务/会话完好（不再 DROP）。
2. 幂等 TTL：`adb shell "run-as com.dougie.app.sideload ls -l databases/"`；执行若干工具调用后查
   `run-as ... sqlite3` 不可用时用 `cat databases/dougie_tasks.db | grep -c idempotency` 级别粗查，
   或导出 DB：`adb shell "run-as com.dougie.app.sideload cat databases/dougie_tasks.db" > local.db`。
   预期：新行 created_at 非零；跨天遗留行（v1 时代）首次工具调用后被清。
3. 级联删除：删除一个会话 → 导出 DB → 确认该会话任务的幂等行（key 前缀 = taskId）已消失，audit_log 保留。
4. audit 行数上限：连续执行 500+ 次工具调用（开发者页跑评测可快速累积）→ 导出 DB 确认 audit_log 行数 ≤ 500。
5. Sanitizer：让云端模型对 clipboard_write 灌 >10K 文本 → 预期任务失败且文案「工具参数无效，已拒绝执行。」。
