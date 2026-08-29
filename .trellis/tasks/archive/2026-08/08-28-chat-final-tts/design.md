# Design

## Boundaries

| Module | Owns |
|--------|------|
| `:core:model` | `AgentTask.speakReply: Boolean = false` |
| `:core:runtime` | `TaskManager.submit(..., speakReply)`；`TaskSnapshotCodec` 编解码，缺键=false |
| `:core:tool` | `TtsEngine` / `PreferOfflineTtsPort` 增加可中断 `stop()`；`TtsSpeakText.forOffline` 仅用于 `speakFinal`；离线未就绪时**宿主路径不调用 fallback**（与 Tool 短提示降级分开） |
| `:tool:system` | `SherpaJni` 播放可 `stop`（打断 `AudioTrack.write` 循环）；`AndroidSpeechPort` 暴露 `speakReply`/`stopPlayback` |
| `:app` | `voiceUsedThisDraft`；`speakingReply`；观察 `COMPLETED` 后调度播报；`onStop` 停播；`onSpeakReply` 重播 `finalAnswer` |
| `:feature:chat` | `speakingReply` / `onStopReply` / `onSpeakReply`；发送钮变停止；最新完成气泡 **播报**；状态句 **正在播报...** |

`:feature:chat` 不接收 PCM / 不调 Loop。通知文案仍不得含 `finalAnswer` 全文。

## Data flow

```
成功 ASR 追加 → voiceUsedThisDraft = true
onSend(text)
  → speakReply = voiceUsedThisDraft
  → voiceUsedThisDraft = false
  → TaskManager.submit(..., speakReply)
Loop → COMPLETED
MainActivity：若 speakReply && finalAnswer 非空 && 本 taskId 未播过
  → 离线 isReady？否 → 附件行 TTS_REPLY_UNAVAILABLE
  → 是 → speakingReply=true → Default 上 speak(finalAnswer) → 结束/失败清 speaking
Stop / 新 submit / onStop → port.stop() → speakingReply=false
气泡 播报 → startReplySpeak(finalAnswer)  // 不要求 speakReply
retry → submit 拷贝 current.speakReply
```

宿主播报**禁止**走 `PreferOfflineTtsPort` 的系统 TTS 分支。可单独 `speakFinal(text)`：仅 offline ready 才 speak，否则失败文案 `语音回复暂不可用`。

## Contracts

```
UserFacingErrors.TTS_REPLY_UNAVAILABLE = "语音回复暂不可用"
fun AgentTask.speakReply: Boolean  // default false
```

Chat 输入栏：`speakingReply` 时主按钮 `Icons.Filled.Stop`，`enabled=true`（即使草稿空），点击 `onStopReply` 不 `onSend`。

## Compatibility

- 旧 `dougie_tasks` 行无 `speakReply` → false。
- `speech_output` JSON 仍为 `ok`+`backend`。
- 不新增 FGS。

## Risks

- `speak` 与 ASR JNI 同锁：播报中按住麦克风可能卡住；播放中禁用麦或 stop 后再录。
- 模型已调 `speech_output` 时可能播两遍；本切片不拦截。
- 长回复合成慢：`speakingReply` 在开始合成时就为 true，停止必须能取消等待。
