# Implement

1. `AttachmentMeta` + `AgentTask.attachments`；codec 无像素；旧快照仍可解码。
2. Store：多 id；`lastScreen` / pin；单测满 4 与 pin 不覆盖他帧。
3. Chat UI：菜单、芯片列表、全屏预览、满额提示。映射函数 JVM 测。
4. `:app`：Picker、TakePicture、截屏入列表；Overlay 满额失败文案。
5. Provider：`allowCloud` + jpeg 回调；测试四类请求体（无附件 / 仅截屏 / 相册+关云 / 相册+开云）。
6. `checkChannelLeak`；`UserFacingErrors` 满额中文。

```
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:runtime:test :core:llm:test :feature:chat:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

勿改 MediaProjection FGS 线程约定。勿把 URI/像素写入通知 extra。
