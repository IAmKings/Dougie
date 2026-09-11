# Implement: 多档对话

## Checklist

1. `ChatSku` / `ChatModelLayout`：三档文件名 + SHA/URL 常量（或 catalog 为唯一来源）。
2. `OfficialModelCatalog` 三行；`isInstalled` 按该档文件。测试夹具。
3. `ModelImporter` / 设置扫描：对话 pack 忽略兄弟 `.litertlm`。
4. `PreferenceStore.activeChatSku` + 默认规则。
5. `ChatLlmProvider` 按激活档建引擎，切换时 close。
6. 设置三行 + 使用/使用中；下载互斥；确认文案。
7. `AppOfflineModels` / probe / `localChatReady`。
8. JDK 17：相关 JVM 测 + `:app:checkChannelLeak`。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:llm:test :feature:settings:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

真机：0.6B 已装时再下 1B 不丢 0.6B；切换后下一句本地走新档。

## Risky

- 导入仍对整个 chat 目录做 hash 双射 → 第二档永远装不上。
- 切换不 close Engine → 仍跑旧权重。
- Play 漏出对话行或 LiteRT。
- 下载完成误激活 2B。
