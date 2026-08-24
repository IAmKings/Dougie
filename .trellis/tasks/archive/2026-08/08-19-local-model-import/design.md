# Design — 外部模型目录

## Boundary

- `:data:preferences`：增加 `modelTreeUri`（可空字符串）。保存配置不必绑死；选目录后立即 persist（与「保存配置」解耦，避免忘保存丢授权）。
- `:app`：SAF tree、`takePersistableUriPermission`、DocumentFile 读写、下载临时文件拷进树。
- `:core:tool`：`ModelImporter` / 哈希匹配 / `filesDir` 同步。扫描逻辑尽量 JVM：输入「相对路径 + 字节/临时 File 列表」，不要 DocumentFile。
- `:feature:settings`：目录行 + 三行下载/测试（ASR / TTS / 意图理解）；不碰 ContentResolver。
- `:tool:system`：引擎仍只读 `filesDir`。TTS 烟测不 play。

## Layout on the tree

`{tree}/models/asr/{model.int8.onnx,tokens.txt}`  
`{tree}/models/tts/{model.onnx,tokens.txt,lexicon.txt}`  
`{tree}/models/intent/{model.onnx, tokenizer, labels.txt}`（文件名以官方 catalog 为准；无 `model.gguf` / `quant.id`）

ASR / TTS / 意图必须复用同一个 `models` 目录。SAF `createDirectory` 在找不到已有文件夹时会新建 `models (1)`；实现必须 `listFiles` 识别 `models` / `models (1)` / `models(2)` 并写入已有树，禁止每个 pack 各建一套。

下载写入这些名字。识别时：先按 layout 名读；若缺，对该子目录其它文件做哈希匹配后 rename/copy 成 layout 名（可选 MVP：只认 layout 名，第三方需已放对名字或我们按哈希写入 layout 名）。

**MVP 识别：** 子目录内所有文件哈希，与 pack specs 做一一匹配（同现 `ModelImporter.matchSources`），命中则同步到 filesDir 并写成 layout 名。

## Data flow

1. 用户选树 → persist URI → `scanAndSync()`.
2. 对每个 offer：从树列出该 `relativeDir` 下文件 → 拷到 cache → `ModelImporter.importFiles` 到 `filesDir`。失败则该行未安装。
3. 下载：确认 → HTTP 到 cache → 哈希 → `DocumentFile.createFile` 写入树（layout 名）→ `importFiles` 到 filesDir。
4. 探针只打 filesDir。

无树：`configured` 下载按钮禁用，目录行显示「未选择」。

授权丢失（重装）：prefs 仍有 URI 但 `persistedUriPermissions` 空 → 当作未选择，提示「请再次选择模型目录」。

## Compatibility

Play/sideload 各存自己的 URI；用户选同一系统文件夹即共享文件。sideload seed 仍只填 filesDir，不自动灌进外部树。

## Rollback

去掉目录行与树写入；下载恢复只写 filesDir。
