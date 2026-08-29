# Implement

1. `UserFacingErrors.TTS_REPLY_UNAVAILABLE`；`AgentTask.speakReply`；codec + `TaskManager.submit`/`retry` 传递；相关 JVM 快照/任务测试。
2. TTS `stop()`：打断 `SherpaJni` 播放；宿主 `speakFinal` 仅离线、不降级系统 TTS。`SpeechOutputToolTest` 回归。
3. `append` 成功置 `voiceUsedThisDraft`；`send(..., speakReply)`；`COMPLETED` 调度播报；`onStop`/新发送停播。
4. `ChatScreen`：`speakingReply` 发送钮→停止；附件行「正在播报...」；失败写「语音回复暂不可用」。
5. 验证：

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME=/Users/pauldeman/.gradle
./gradlew :core:model:test :core:runtime:test :core:tool:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

勿把 `finalAnswer`/PCM 写入通知 extra 或 Logcat。勿改 `speech_output` 成功 JSON。勿对正式回复走系统 TTS。
