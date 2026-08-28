# Implement

1. `appendVoiceTranscript` + `ChatUiStateTest`（空草稿 / 追加空格 / 空白 spoken 不改）。
2. `:core:tool` `HoldSpeechRecorder` + Fake；15s 上限常量；既有 `SpeechInputToolTest` 仍过。
3. `:tool:system` 实现按住 `AudioRecord`；`capture()` 3 秒路径不删。
4. `:app`：Chat 申请 `RECORD_AUDIO`；门控；Default 转写；错误进附件错误行。
5. `ChatScreen` 启用麦克风按住/松手；任务忙时禁用。
6. `./gradlew :core:tool:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak`

勿把 PCM/转写写入通知 extra 或 Logcat。勿 `submit`。勿改 `speech_input` JSON。
