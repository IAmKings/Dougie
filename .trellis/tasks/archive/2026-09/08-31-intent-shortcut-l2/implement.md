# Implement: L2 写操作短路径

## Checklist

1. `IntentRouteAnswers`：`toolNameFor` 增加两标签；`parseShortcutArgs`；create/write 的 `formatFinalAnswer`；槽位单测（有引号 / 明天下午三点开会 / 无时刻）。
2. `completeFromIntentIfMatched`：抽出 args 再 `executeToolPass`；抽不出 return null。
3. `LoopEngineTest`：确认自动 `engine.confirm()` 的 create/write 短路径；拒绝；无时刻仍 LLM。
4. 更新 `.trellis/spec/backend/directory-structure.md` LoopEngine 短路径句。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:runtime:test
```

## Risky files

- `LoopEngine.kt`：L2 短路径若在确认前 return null，会误进 LLM 再弹一张卡。
- 槽位过宽会把「定个日程」写成错误时间。
