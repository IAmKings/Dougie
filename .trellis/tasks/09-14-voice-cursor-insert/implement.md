# Implement: 语音插入到输入框光标处

## Order

1. `insertVoiceTranscript(draft, start, end, spoken)` 纯函数 + JVM 测试；`appendVoiceTranscript` 改为末尾插入的委托。
2. Chat `TextField` 改为 `TextFieldValue`（选区随草稿 hoist 到 `MainActivity`）。
3. 松手成功：按当前 `selection` 插入并设置新光标。失败不改 `TextFieldValue`。
4. 进程恢复 / 定时提醒预填：光标放在该字符串末尾。
5. `./gradlew :feature:chat:testDebugUnitTest`

## Do not

- 改 overlay 部分识别、ASR catalog、`submit`。
- 把 PCM/转写写入 Logcat。
