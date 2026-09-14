# Design: Chat 按住说话流式回显

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` | `HoldSpeechRecorder.snapshot()`；Fake 可逐步加长 PCM；`voiceOverlayStatus` 仍在 `:feature:chat` |
| `:tool:system` | `AudioRecordHoldRecorder` 采集中拷贝 snapshot；`SherpaJni.decode` 仍一次一 stream，同一把 ASR/TTS lock |
| `:app` | 按住期间 Default 上周期性 `snapshot` + `transcribe`，把**文本**推给 overlay；松手取消周期任务后对 `stop()` 全文再转一次再 `appendVoiceTranscript` |
| `:feature:chat` | overlay 增加 `partial` 字符串；无 PCM |

`:core:*` 仍无 Android。不经 `TaskManager`。不新 catalog 行。

## Data flow

```
Pointer down（现网门控不变）
  → HoldSpeechRecorder.start()
  → overlay: 「正在录音」
  → Default loop ~400ms:
       snapshot() 拷贝已收 PCM
       样本太短 / 上一拍仍在 decode → skip
       transcribe(snapshot) → 非空则更新 partial
       抛错 → 忽略，保持上一行或「正在录音」
Pointer up 或 15s
  → 取消 loop
  → overlay: 「正在进行本地识别...」
  → stop() 全文 transcribe → append 或既有错误文案
  → 清空 partial
```

`speech_input` 的 3 秒 `capture()` 不走 snapshot。

## Contracts

```
interface HoldSpeechRecorder {
    fun start(): Boolean
    fun snapshot(): SpeechUtterance   // 未在录：空 samples；必须拷贝
    suspend fun stop(): SpeechUtterance
}

fun voiceOverlayStatus(holding: Boolean, transcribing: Boolean, partial: String = ""): String
```

- holding && partial.isNotBlank() → `partial.trim()`（不 log）
- holding → `正在录音`
- transcribing → `正在进行本地识别...`
- else → `""`

`appendVoiceTranscript` 不变。最终仍以 `stop()` 全文为准，不用最后一拍 partial 顶替。

`SherpaJni.decode`：每次 snapshot 新建 stream；与 TTS 同 lock。按住时 mic 已禁用播报，不并行 speak。周期 decode 同时只允许一拍（in-flight skip）。

最短开解约 0.4s（`sampleRate * 2 / 5`）。间隔约 400ms，不做成产品旋钮。

## Compatibility

- 不改 Agent 快照 / codec。杀进程仍丢未发送草稿。
- 不新增 `FOREGROUND_SERVICE_MICROPHONE`。
- 已装 Paraformer 的 Play/侧载用户无需再下载。

## Risks

- 15s 末尾全文 decode 比短 snapshot 慢：松手仍显示「正在进行本地识别...」，与现网一致。
- `synchronized` 长音频 decode 会占 sherpa lock：按住期间不播 TTS；loop skip in-flight。
- Overlay 长句：最多约 2 行，超出 ellipsis；不进草稿。

## Scenario: hold partial overlay

### 1. Scope / Trigger
Composer hold-to-talk overlay. Not `SpeechInputTool`.

### 2. Signatures
`HoldSpeechRecorder.snapshot()` / `voiceOverlayStatus(..., partial)` / existing `SpeechEngine.transcribe`.

### 3. Contracts
Partial text never writes `chatDraftState`. PCM never enters `:feature:chat`. Cancel partial loop before `stop()`.

### 4. Validation & Error Matrix
Partial throw → keep last overlay line. Release empty/fail → existing `SPEECH_*` / `TOOL_FAILED`, draft unchanged.

### 5. Good/Base/Bad
- Good: overlay shows growing text; release appends once.
- Base: <0.4s utterance → overlay stays 「正在录音」 until release final.
- Bad: write partial into TextField; second ASR catalog; log transcript.

### 6. Tests
`voiceOverlayStatus` partial vs 正在录音；Fake snapshot grows and `transcribeCount` ≥ 1 before stop; `SpeechInputToolTest` unchanged.

### 7. Wrong vs Correct
Wrong: OnlineRecognizer + new HF row; `await` back-to-back decode on Main.
Correct: existing OfflineRecognizer; Default; copy snapshot; final `stop()` decode.
