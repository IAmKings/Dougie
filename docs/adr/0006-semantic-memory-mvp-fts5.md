# Semantic Memory MVP 降级为 FTS5(向量检索后置)

MVP 的语义记忆用 **FTS5 全文检索 + 关键词匹配**实现"找到历史事实",embedding + 向量检索推迟到 Beta(事实量 >5K 时再考虑索引)。数据模型保留 `embedding` 字段(nullable)以便日后升级。理由:端侧向量检索的 sqlite-vec 需 NDK 编译、ObjectBox 增加包体与学习成本,与 MVP"最小闭环"目标冲突。

**Status**: accepted

**Considered Options**: MVP 即引入向量检索(复杂度/包体风险高);永远不用向量(长期检索质量不足)。
