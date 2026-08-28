# Design

## Boundaries

| Module | Owns |
|--------|------|
| `:core:tool` | `HoldSpeechRecorder` 起停合同 + `HOLD_MAX_MS = 15_000`；`SpeechRecorder.capture()` 仍为 Tool 的 3 秒；`append` 规则的纯函数可放 `:feature:chat` |
| `:tool:system` | `AudioRecord` 按住采集（前台 Activity，无新 FGS） |
| `:app` | 权限申请、门控、转写调度、把文本写进 `chatDraftState` |
| `:feature:chat` | 麦克风按住/松手回调、录音中视觉；`appendVoiceTranscript(draft, spoken)` JVM 可测 |
| `:feature:permissions` | 不改合同；中心项仍可开关系统权限 |

不把 PCM 传入 `:feature:chat`。不经 `TaskManager`。

## Data flow

```
Pointer down
  → 忙/已在录：忽略
  → 无 RECORD_AUDIO：RequestPermission，本段不录音
  → 非前台 / 无模型 / 引擎未就绪：错误行，不录音
  → HoldSpeechRecorder.start()
Pointer up 或 15s
  → stop() → SpeechUtterance
  → 空采样：SPEECH_EMPTY
  → engine.transcribe on Default
  → appendVoiceTranscript → composer
仍按住且已因 15s 结束：直到 ACTION_UP 再允许下一段
```

授权对话框打断按住：当取消/失败，不转写。授权成功不自动开麦。

## Contracts

```
fun appendVoiceTranscript(draft: String, spoken: String): String
```

- `spoken` 空白 → 返回原 `draft`
- `draft` 空白 → `spoken.trim()`
- 否则 `draft.trimEnd() + " " + spoken.trim()`

`HoldSpeechRecorder`：`start()` / `suspend fun stop(): SpeechUtterance`；内部到 15s 等价于 stop。采样 16 kHz mono，与现 ASR 一致。

`SpeechInputTool` / `FakeSpeechPort.listen()` 不变。

## Compatibility

- 不改 Agent 任务快照、不写转写进 codec。
- 杀进程丢未发送草稿（与现 composer 一致）。
- 不新增 `FOREGROUND_SERVICE_MICROPHONE`。

## Risks

- 按住与滚动冲突：只在麦克风 `IconButton` 上 `pointerInput`。
- JNI 转写可能数秒：输入栏显示短暂 busy，避免连按。
- Play 无模型：`SPEECH_MODEL_MISSING`，引导已有设置下载即可，本切片不改设置页。
