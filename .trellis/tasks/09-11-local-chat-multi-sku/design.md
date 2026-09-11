# Design: 多档对话 SKU

## Layout

`models/chat/` 内三个文件名：

| SKU | 文件 |
|---|---|
| `chat-qwen06` | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` |
| `chat-minicpm1b` | `minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm` |
| `chat-minicpm2b` | `MiniCPM5-2B_int4.litertlm` |

`ChatModelLayout` 改为按 SKU 解析文件名；`isPresent(dir, sku)`；`isPresent` 无 sku 时只表示「任意已装」不得点亮 ready。

`OfficialModelCatalog.standard()` 侧载追加两行（或三行独立 chat offer，0.6B 替换现单一 `chat()`）。offer `id` 必须唯一。`relativeDir` 仍是 `models/chat`。

## Import

扫描/导入某个对话 offer 时，sources **只收该 spec 的文件名**（或只 hash 匹配该 spec），不要把目录里其它 `.litertlm` 送进 `matchSources`。`willReplace` 对对话 SKU 为 false。

## Active sku

`PreferenceStore`：`active_chat_sku` 字符串。非法或文件缺失 → 视为未激活（ready false），Chat 走现有缺包文案。

`ChatLlmProvider.ensureEngine`：读激活路径；sku 变化则 `engine.close()` 再创建。进程内单例仍保留。

`ChannelHooks.localChatReady` / `DougieApplication.localReady`：激活档 `isPresent`。

## UI

`OfflineModelRowUi` 增加 `active: Boolean`。已装非激活显示「使用」。`OfflineModelDownloads.activate(id)` 写 prefs 并回调 `onChatSkuChanged`（`:app` 关引擎）。

确认下载：HTTPS 缓存 → 树 → import；文案带体积、不覆盖其它对话包。

## Compatibility

- 已装 0.6B 用户：默认 `chat-qwen06`（文件仍在）。
- Play：`AppOfflineModels` 过滤所有 `chat-*`。
- 探针仍可手拷；产品不读 `/sdcard/Dougie`。
