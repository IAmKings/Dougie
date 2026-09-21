# Implement: Compose UI 五态测试

## Order

1. `:feature:chat`：BOM 的 `ui-test-junit4` + `ui-test-manifest`（debug）、Robolectric、`isIncludeAndroidResources = true`。
2. `ChatScreenFiveStateTest`：User / Thinking / Tool / Confirm / Final（+ 失败）语义断言。
3. `./gradlew :feature:chat:testDebugUnitTest`
4. `quality-guidelines.md` 写明 Chat 五态有 Compose UI 测试；其它屏仍无。

## Do not

- 截图金样、`androidTest`、测动效/打字机/sharedBounds。
- 为了测试给气泡加英文 testTag（用已有中文文案）。
- 升版本、改产品交互。
- `task.py start` 前未获规划批准。
