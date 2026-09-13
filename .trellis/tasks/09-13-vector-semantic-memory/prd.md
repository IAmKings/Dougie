# 向量语义记忆

## Goal

用户用和原文不同的说法提问时，Agent 仍能找回已记住的事实，并带上现有 `source`。embedding 模型未下载或失败时，召回必须与现网 FTS + `LIKE` 一致，不能变成空结果。

## User value

「我喜欢喝美式」被记住后，问「我平时喝什么咖啡」也能注入记忆；不必先下一包模型才能用记忆。

## Background

- 根 `PRD.md` §7.2：Beta 用本地 embedding + 事实量 < 1K 暴力余弦；不引入 sqlite-vec / ObjectBox。MVP 的 FTS 必须保留为降级。
- `MemoryEntry.embedding` 已是 `ByteArray?`，当前恒 null。`RoomMemoryStore.search` 只做 FTS4 `MATCH` + `LIKE`，SELECT 不含 embedding。`dougie_memory.db` version 1；现网 `onUpgrade` 会 drop 重建，**v0.1.0 已发出，本任务必须改成 additive migration**。
- `LoopEngine.retrieveMemories` 调 `store.search(task.input, limit=5)`，再 5 条 / 800 字预算。Chat 终答展示 `来源：`。
- `MemoryGate` 规则抽取不改。离线下载走 `OfficialModelCatalog` + 设置页（ASR/TTS/intent/chat）。
- 用户决定：Play 与侧载都提供**可选** embedding 下载；未就绪只走 FTS。

## Requirements

- R1 引擎就绪时，同义问句能召回关键词对不上的已存事实，并保留 `source`。
- R2 引擎未安装、损坏或 `embed()` 失败时，`search` 等于现网 FTS + `LIKE`（JVM 上为 needles `contains`），不崩、不空召回。
- R3 新写入事实在引擎就绪时持久化 embedding；已有无向量事实在 Default 上空闲补齐，不阻塞 Loop。
- R4 Play / 侧载 APK **都不内置** embedding 权重；`checkChannelLeak` 增加 `models/embed` 扫描，Play 仍不得含 `*.onnx`。
- R5 设置页增加与意图同类的下载行（标题「语义记忆」）；下载完成不自动当对话引擎，只让记忆召回升级。
- R6 不把事实原文、query、embedding 向量打进 Logcat。

## Out of scope

- 改 MemoryGate 抽取、云端 embedding、sqlite-vec、桌面、对话 LLM、第三方连点。
- 事实量 > 1K 的索引。
- 把 embedding 打进 sideload assets（不计入语音 400MB 预算）。

## Key Decisions

- Play + 侧载都可选下载；缺模型 = FTS。
- `LoopEngine` 继续只调 `MemoryStore.search`；混合召回放在 store 包装层。
- 不把意图分类 ONNX 当句向量（图不同）。
- v0.1.0 用户的 `dougie_memory.db` 只 `ALTER TABLE ADD COLUMN`，禁止 drop。

## Acceptance Criteria

- [x] AC1 引擎就绪：关键字对不上的同义问句召回该事实，Chat 显示来源。
- [x] AC2 引擎未就绪：与现网 FTS 行为一致（`MemoryGateTest` / Loop 记忆注入不回归）。
- [x] AC3 新事实写入后可被同义问句命中；旧事实补 embedding 后同样可命中。
- [x] AC4 `./gradlew :core:memory:test :core:runtime:test :core:tool:test :app:checkChannelLeak` 通过（JDK 17）。
