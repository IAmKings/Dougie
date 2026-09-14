# 语音插入到输入框光标处

## Goal

松手后的识别结果插在输入框**当前光标（或选区）**，插完后光标跟在这段话后面。失败不改草稿和光标。

## User value

现在一律接到字符串末尾，Compose 还把光标打回开头：再打字会插到最前；把光标挪到中间再说话，字却跑到末尾。

## Background

- `appendVoiceTranscript` 只做末尾拼接。`TextField` 吃 `String`，外部整段替换后选区回到 0。
- 按住 overlay 部分字仍不写草稿（`09-14-streaming-asr-hold`）。
- 用户决定：语音与键盘同一位置；连续按住会因光标已在段落后而接到后面。

## Decisions

- 插入点 = `TextField` 选区；有选区则替换选区。
- 插入后光标在识别结果之后（与右侧原文之间的空格之前）。
- 左/右若与识别结果之间没有空白，补一个空格（与现网末尾追加的空格规则一致）。
- 空白/失败识别：草稿与光标不变。按住期间仍不改草稿。

## Requirements

- R1 成功终稿插入当前选区，光标跟在新字后。
- R2 光标在中间时插入中间；在末尾时效果仍为追加。
- R3 失败 / 空音频：既有中文错误，草稿与光标不变。
- R4 不 `submit`；不改 overlay / ASR 包。

## Out of scope

- 按住期间把部分字写入草稿。
- IME composition 细节、多光标。
- 改 15s 上限、`speech_input` Tool。

## Acceptance Criteria

- [x] AC1 空框说话：字在框内，光标在末尾；再打字接在后面。
- [x] AC2 已有文字且光标在中间（或选中一段）再说话：插在（或替换）该处，不接到末尾。
- [x] AC3 光标在末尾再说话：仍接到后面。
- [x] AC4 JVM：`insertVoiceTranscript` 覆盖空框 / 末尾 / 中间 / 选区替换 / 空白 spoken；`:feature:chat:testDebugUnitTest` 通过。
