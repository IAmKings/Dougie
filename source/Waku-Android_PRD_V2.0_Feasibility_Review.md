# Waku-Android Local-First Agent PRD V2.0 可行性审核报告

> **审核日期**：2026-08-13  
> **审核对象**：Waku-Android Local-First Agent PRD V2.0  
> **审核结论**：B+（可行，但需调整预期）

---

## 一、总体评估

V2.0 相比 V1.0 是一次关键的质量跃升——它从"架构蓝图"转变为"可执行基线"，核心方向（Local-first、Tool-driven、Recoverable）是成立的。

但文档中存在**时间估算偏乐观、部分技术假设需要验证、以及缺失关键约束**等问题。

---

## 二、架构可行性：✅ 高

### 优势

- **技术栈选择**（Kotlin + Compose + Coroutines + Hilt + Room + WorkManager）是 Android 现代开发的标准答案，无技术债风险。
- **模块划分**（`:core:*` + `:feature:*` + `:tool:*`）遵循 Clean Architecture，边界清晰。
- **Task 作为一级执行单位 + 状态机** 的设计是本文档最正确的决策，它把"对话"提升为"可恢复的计算任务"。
- **Tool Policy Engine** 的三层校验（Schema → Permission → Risk）设计合理，是 Prompt Injection 防护的正确解法。
- **Explicit Data Egress** 策略务实，修正了 V1.0 的绝对化隐私承诺。

### 建议补充

- **缺少 ANR 防护设计**。Agent Loop 运行在主线程协程上时，Tool 超时（15s）+ LLM 超时（60s）如果调度不当，极易触发 ANR。
- **应明确要求**：Loop Engine 运行在自定义 `CoroutineDispatcher`（如 `Dispatchers.Default` + 自定义线程池），且 UI 层仅收集 `StateFlow`。

---

## 三、时间可行性：⚠️ 偏乐观（最大风险）

文档给出的 Roadmap 隐含 **5–8 周** 的 MVP 周期，但实际需要 **8–12 周**（假设 2–3 人 Android 团队）。

| 阶段 | 文档估算 | 现实估算 | 差距原因 |
|---|---|---|---|
| Phase 0 架构验证 | 3–5 天 | 3–5 天 | ✅ 合理，Fake 驱动开发是正确的 |
| Phase 1 Cloud MVP | 1–2 周 | 3–4 周 | ⚠️ **严重低估**。真实 LLM 的 Tool Calling 不可靠（模型经常输出不符合 schema 的参数），需要大量防御性编码；Streaming + StateFlow 的并发状态管理也容易出 bug |
| Phase 2 Local Memory | 1–2 周 | 2–3 周 | ⚠️ Semantic Memory 接口容易，但实现（embedding 模型选型、量化、推理性能）需要验证 |
| Phase 3 Android Tools | 1–2 周 | 2 周 | ⚠️ 权限策略在不同 Android 版本（10–16）行为差异大，需要兼容性测试 |
| Phase 4 Reliability | 1 周 | 2–3 周 | ❌ **严重低估**。Process Death Recovery + Idempotency + Audit Log 是系统工程，1 周只能做 demo 级别 |

### 关键建议

1. 将 **Phase 1 拆分为两个子阶段**：先跑通 Non-streaming + 1 个 Tool，再引入 Streaming + 多 Tool。
2. **Phase 4 不应是"1 周收尾"**，而应作为**贯穿全程的质量基线**（每个 Phase 都包含对应的可靠性验收）。

---

## 四、技术可行性：⚠️ 存在待验证点

### 1. Semantic Memory / Vector Store（风险：中）

文档建议"首期可选 sqlite-vec / ObjectBox"。

- **sqlite-vec**：在 Android 上需要 NDK 编译，且性能在大数据量下未经验证。
- **ObjectBox**：支持向量检索，但引入第三方数据库会增加包体积和学习成本。

**更务实的建议**：MVP 阶段 Semantic Memory 可用 **ONNX Runtime 本地跑 embedding 模型 + 暴力余弦相似度计算**（数据量 < 1K 时性能可接受），避免引入额外的 Vector DB 依赖。当事实数量 > 5K 时再考虑索引优化。

### 2. Local LLM（风险：高，已识别）

文档正确地将 Local LLM 后置到 Beta。但需注意：

- 即使是 Cloud LLM，Android 端的 **Token 计数** 也需要本地实现（用于 Context Budget）。
- **建议**：使用 `tiktoken` 的 Kotlin 移植版或近似算法。

### 3. Process Death Recovery（风险：高）

文档要求"在 Tool 执行前后杀进程，不产生重复副作用"，这是正确的目标。但实现上：

- Android 的 `WorkManager` 确实可恢复，但 **实时 Agent Loop 不适合用 WorkManager**（文档已修正这一点）。
- 如果 Agent Loop 运行在前台 Service/Activity 中，进程被杀后恢复需要依赖 **Room 中的 Task 状态 + `SavedStateHandle`**，且 LLM 的流式响应无法恢复（必须重试）。

**建议明确**：Process Death Recovery 的边界是"Task 状态可恢复、Tool 副作用可幂等"，而非"LLM 流可断点续传"。

---

## 五、安全与合规可行性：⚠️ 有缺失

### 1. Google Play 政策（未提及）

- **AccessibilityService**：文档将其移出 MVP 是明智的，但需提前告知团队——Google Play 对 Accessibility 的审核极其严格，未来进入 Beta 时需要提交"为什么需要此权限"的视频演示，且不能用于自动化点击第三方 App（可能违反开发者政策）。
- **后台限制**：前台 Service 需要声明 `foregroundServiceType`，Android 14+ 限制更严。

### 2. 数据分类与 Prompt Injection（已识别）

文档对 Secret 数据的处理（禁止进入 Prompt）是正确的。但需补充：

- **剪贴板读取（Clipboard Read）** 在 Android 12+ 有 `READ_CLIPBOARD` 的隐私指示器（Privacy Indicator），且 Google Play 可能要求解释为何需要后台读取剪贴板。
- **建议**：MVP 中剪贴板 Tool 仅在前台激活时可用。

### 3. 日志脱敏（已覆盖）

文档禁止在日志中记录 Prompt 全文、GPS 坐标等，这是合规基线。

**建议增加**：Release 构建必须关闭所有 LLM 原始响应的日志输出。

---

## 六、LLM 可靠性：⚠️ 需要强化

文档假设 LLM 能稳定输出符合 schema 的 Tool Call，但现实中：

- GPT-4o/Claude 3.5 的 Tool Calling 成功率约 **95–98%**，仍有 2–5% 的幻觉（参数类型错误、必填字段缺失、编造不存在的 Tool）。

**建议增加**：`ToolCallSanitizer` 层，在 Schema Validate 之前先进行**参数类型强制转换**和**默认值填充**，而不是直接拒绝导致 Task 失败。

另外，文档的 **Context Token Budget（8K）** 在移动端是合理的，但未说明如何计算 Token（不同 Provider 分词器不同）。

**建议**：采用统一的最坏情况估算（1 token ≈ 0.75 个汉字 / 0.25 个英文单词），并在接近 Budget 时优先截断 Tool Result 而非 Memory。

---

## 七、测试策略可行性：✅ 方向正确，但需落地

文档要求覆盖 Unit / Integration / Instrumentation / Security Test，这是正确的分层。但 MVP 阶段需要**明确优先级**：

| 测试类型 | MVP 优先级 | 说明 |
|---|---|---|
| Loop State Machine Unit Test | P0 | 必须在 Phase 0 完成 |
| Tool Schema + Policy Unit Test | P0 | 必须在 Phase 3 完成 |
| Process Death Integration Test | P1 | 可用 `adb shell am kill` 自动化 |
| LLM→Tool→LLM Integration Test | P1 | 建议用 Fake LLM 做契约测试，而非依赖真实 API |
| Security Test | P2 | MVP 可用手动检查清单替代自动化 |
| Accessibility Instrumentation | Beta | MVP 不涉及 |

---

## 八、给工程团队的 5 条关键建议

### 1. Phase 0 是生死线

如果 5 天内 Fake LLM + Fake Tool + State Machine 无法稳定跑通 3 次 Loop，说明状态机设计有缺陷，**必须暂停后续开发，先修状态机**。不要带着不稳定的核心进入 Phase 1。

### 2. Semantic Memory MVP 降级

MVP 的 Semantic Memory 不需要完整的 embedding + vector search。先用 **FTS5 全文检索 + 关键词匹配** 实现"找到历史事实"，Beta 阶段再引入向量语义检索。

### 3. Idempotency 从第一天开始

Calendar 创建等 Tool 的幂等键（`taskId + toolCallId`）必须在 Phase 1 就实现，**不要等到 Phase 4 再补**。后期补幂等性等于重写 Tool 层。

### 4. 明确"不恢复 LLM 流"

Process Death Recovery 的验收标准应修改为"Task 可重新提交或返回错误状态"，而非"无缝续传"。移动端 LLM 流中断后重试是行业通用做法。

### 5. 增加 OEM 兼容性预算

Android 13–16 在三星、小米、华为等厂商的后台策略差异巨大，建议预留 **20% 的工期**用于处理厂商特定的权限行为和后台限制。

---

## 九、一句话总结

> **这是一个方向正确、架构扎实的 PRD，但请把 MVP 周期从"6 周"的心理预期调整为"10 周"，并把 Phase 4 的可靠性工作分散到每个阶段中，而不是留到最后一周。**
