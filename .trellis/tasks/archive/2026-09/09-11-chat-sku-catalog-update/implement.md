# Implement: 对话 SKU 随发版更新

## Checklist

1. `ChatModelLayout`：`legacyNames` 或文档约定「改官方名时把旧名留给 guessSku」；`locate` 官方名优先。引擎在 **路径** 变化时 close。
2. 过期判定：官方名缺失但 SKU 可 locate → stale；官方名在场则异步 SHA vs catalog。`OfflineModelRowUi.needsUpdate`。
3. `OfflineModelDownloads.request`：stale 可确认下载；current 仍不可下。`copyInto` 对 stale 同名文件允许覆盖。
4. 导入成功后删应用私有目录该 SKU 旧文件名；不碰 SAF 树。测试夹具用小文件。
5. 设置行：「更新」与使用并存；确认文案区分更新 vs 首次下载。Chat 不改。
6. Play 过滤不变。JDK 17：`:core:tool:test` `:feature:settings:testDebugUnitTest` `:app:testPlayDebugUnitTest` `:app:checkChannelLeak`。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :feature:settings:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

真机（升版模拟）：把 catalog 官方名改成测试名不可行；用 JVM 夹具即可。侧载确认「更新」与「使用」同在、下完后 filesDir 旧名消失。

## Risky

- 主线程 SHA 1.5GB → ANR。
- `request` 仍在 `installed` 时 return → 永远不能更新。
- `copyInto` 跳过已有官方名 → 同名换 SHA 失败。
- 删旧文件误删其它 SKU 或 SAF 树。
- 路径变了但引擎只看 sku id → 仍跑旧权重。
