# Design: 向量语义记忆

## Boundary

- `:core:memory`：`EmbeddingPort`、余弦、混合 `search` / `upsert` 包装。零 `android.*`。
- `:data:memory`：SQLite v2 增加 `embedding BLOB`；读写该列。
- `:tool:system`（或现有 ONNX 端口旁）：Android `EmbeddingPort`，模型未就绪则 `isReady()=false`。
- `:core:tool` catalog：`embed` 包 `models/embed/`，HTTPS + SHA-256；设置页多一行。
- `:app`：注入包装后的 `MemoryStore`；模型变为 ready 后 Default 上 `backfillMissing()`。
- 不改 `LoopEngine` 调用点，不改 `MemoryGate` 抽取。

## EmbeddingPort

```kotlin
interface EmbeddingPort {
    fun isReady(): Boolean
    /** 失败返回 null，调用方降级 FTS。禁止 log 文本。 */
    suspend fun embed(text: String): FloatArray?
}
```

JVM 测试用 Fake：为固定中文句子返回可分的向量（「美式」与「咖啡」相近，「电量」远）。生产路径不接 Fake。

Android 实现：ONNX Runtime（已随 sherpa JNI 存在则复用 load 纪律：先 `isAvailable()` 再 class-load）。**不要**拿 `models/intent/model.onnx` 当句向量。

权重：catalog id `embed`，`EmbedModelLayout.DIR = "models/embed"`。Shipped device path is `HashBagEmbeddingPort` on `tokenizer.json` until a hashed sentence pack exists (`MODEL_FILE` reserved). JVM paraphrase AC uses Fake vectors, not hash-bag cosine. Empty catalog URL → Settings 尚未配置下载地址. CI 单测不下载、不入库 ONNX。

## HybridMemoryStore

包装任意 `MemoryStore`：

| 方法 | 行为 |
|------|------|
| `search` | 先 `inner.search`（关键词）。若 `!isReady()` 原样返回。否则 embed 问句；对 `inner.list()` 中**已有** embedding 的条目算余弦；≥ 阈值的按分排序；**语义命中在前、关键词补齐**，按 `id` 去重，截断 `limit`。embed 失败 = 纯关键词。**禁止 await `backfillMissing()`**；NULL 行经 `idleScope.launch` 或启动 Default 补齐，本轮可能 miss。 |
| `upsert` | ready 时写入前填 `embedding`（float32 LE `ByteArray`）；未 ready 则 embedding=null。 |
| `list/delete/clear` | 转 inner。 |
| `backfillMissing` | `list()` 里 embedding==null 的逐条 embed 再 upsert；在 Default；失败跳过该条。 |

阈值默认 **0.45**，单测锁一组 Fake 向量，不靠真模型调参。

事实量按 PRD 暴力扫 `list()`（< 1K）。不在 SQL 里做向量索引。

## SQLite

`DB_VERSION = 2`。`onUpgrade(1→2)`：`ALTER TABLE memory_facts ADD COLUMN embedding BLOB`。禁止 drop。`toEntry` / INSERT 读写该列。null blob = 未嵌入。

## Settings / leak

`OfficialModelCatalog.standard()` 增加「语义记忆」行，两渠道同一 catalog。`isInstalled` 认 `models/embed` 布局。Play `ChannelHooks.seedBundledModels` 仍不拷 embed。`checkChannelLeak`：play/sideload zip 均不得含 `models/embed`（与 chat 一样按需下载）。

Probe：文件在且能跑一次短文本 embed 即 ok；失败中文错误，不贴向量。

## Logging

禁止 fact `content`、问句、float 向量、模型路径以外的本地绝对路径。

## Rollback

用户不下载模型 = 行为与 v0.1.0 记忆召回相同。下载后可删 `filesDir` 下 embed 目录再降级。DB v2 列可空，旧代码读不到该列会出问题——因此必须随 App 一起升级，不能只发模型。
