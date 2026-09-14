# Implement: Chat 按住说话流式回显

## Order

1. `HoldSpeechRecorder.snapshot()` + `FakeHoldSpeechRecorder` 可追加 PCM；JVM：未 start 为空、start 后 snapshot 变长、stop 后空。
2. `AudioRecordHoldRecorder`：采集中拷贝 `collected` → float；勿把列表引用泄漏出去。
3. `voiceOverlayStatus(holding, transcribing, partial)`；`ChatUiStateTest`：有字显示字、空白仍「正在录音」、松手仍「正在进行本地识别...」。Overlay 传入 `partial`，最多两行。
4. `MainActivity`：按住后 Default 周期 snapshot+transcribe 更新 `partial` State；`finishHold` 先 cancel 再 `stop()` 全文转写；`appendVoiceTranscript` 只在成功终稿。
5. `./gradlew :core:tool:test :feature:chat:testDebugUnitTest :app:checkChannelLeak`。真机 PJZ110：按住看 overlay 出字，松手草稿才变。

## Validation

```bash
./gradlew :core:tool:test :feature:chat:testDebugUnitTest :app:checkChannelLeak
```

JDK 17。Play/sideload zip 仍无第二套 ASR。

## Risk / rollback

- 周期 decode 与 `stop()` 抢 sherpa lock → in-flight skip + 先 cancel。
- 卸掉改动只影响 overlay；松手路径应与现网一致。

## Do not

- 新增流式模型 / `OnlineRecognizer` / catalog 行。
- 把 PCM 或转写写进 Logcat / `AuditLog` / 通知。
- `submit`、改 `speech_input` JSON、改 15s 上限。
- `task.py start` 前未获规划批准。
