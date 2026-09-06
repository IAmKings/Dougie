# Implement

1. `:app` 抽出 `consumeBack` + JVM 测（`:app:testPlayDebugUnitTest` 或放 `app/src/test`）。
2. `MainActivity` `BackHandler` 接线。
3. Spec：`component-guidelines.md` Navigation 条补系统返回与顶栏一致。
4. `./gradlew :app:testPlayDebugUnitTest`（相关测例）

## Rollback

删除 BackHandler 与 pop 函数。
