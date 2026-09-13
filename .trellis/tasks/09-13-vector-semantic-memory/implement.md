# Implement: 向量语义记忆

## Order

1. `:core:memory`：`EmbeddingPort` + `cosine` + `HybridMemoryStore` + Fake；单测：未 ready=关键词；ready 时同义命中、无关不进；upsert 写入 embedding；backfill 补 null。
2. `:data:memory`：v2 additive `embedding BLOB`；CRUD 带上列；单测若无 Android 则靠 JVM Hybrid。不要改成 drop。
3. `EmbedModelLayout` + catalog 行 + `isInstalled` 分支；`checkChannelLeak` 禁 `models/embed`。
4. Android `EmbeddingPort`：模型缺失 → not ready；不 log 文本。设置页出现「语义记忆」下载行（复用 `OfflineModelDownloads`）。
5. `DougieApplication`：`HybridMemoryStore(RoomMemoryStore, port)`；catalog 安装成功或启动时 Default `backfillMissing()`。
6. 发布 hashed 权重到 GitHub Releases，填 `DEFAULT_EMBED_*`。未发布前 catalog URL 可空则该行显示未配置（与其它 offer 相同），JVM 测试仍绿。
7. spec：`database-guidelines.md` v2 列与 additive upgrade；`directory-structure.md` Hybrid + embed catalog；`logging-guidelines.md` 不打向量。

## Validation

```bash
./gradlew :core:memory:test :core:runtime:test :core:tool:test :app:checkChannelLeak
```

JDK 17。真机：记忆门开，存「我喜欢喝美式」，下完 embed 后问「我平时喝什么咖啡」应出现来源；不下载模型时问「美式」仍走 FTS。

## Risk / rollback

- **v0.1.0 已发出**：`onUpgrade` drop 会清空记忆。第一步就把 v2 改成 ALTER。
- 意图 ONNX 误用为 embed → 召回乱。禁止。
- `list()` 全表扫只在 <1K；不在本片加索引。
- 权重未发布时不要把空 URL 当已安装。

## Do not

- 改 MemoryGate 词表、LoopEngine 预算数字、Play APK 内置 onnx。
- `task.py start` 前未获规划批准。
