# Compose UI 五态测试

## Goal

用 JVM Compose 语义测试钉住 Chat Bubble 五态 + Confirm Card（§11.5 / §16.3）。默认关终端风、浅色。不做截图、不上模拟器。

## User value

改气泡或确认卡时，`./gradlew :feature:chat:testDebugUnitTest` 能直接红。

## Background

- 用户确认：Robolectric + Compose 语义断言；不上 instrumented；不加截图金样。
- §11.5：User / Thinking / Tool / Confirm / Final。Confirm 是 overlay，不是 feed 行。Tool 默认收起。User 语音标注「语音转写」。Thinking 禁止孤句「正在思考」。
- `:feature:chat` 只有 JUnit。`ChatScreen(uiState, …)` 可注入；`sharedBoundsFor` 默认 `Modifier`。
- 映射已由 `ChatUiStateTest` 覆盖；本刀测 **屏幕上能看见的字/按钮**。

## Requirements

- R1 加 Robolectric + `ui-test-junit4`（走 Compose BOM）。`unitTests.isIncludeAndroidResources = true`。命令仍是 `./gradlew :feature:chat:testDebugUnitTest`。
- R2 五个场景（可一个或多个 test 类）：
  - User：用户文案；`speakReply` 时有「语音转写」，不在气泡 `text` 里。
  - Thinking：`思考中… [循环 n]` 或 `循环 n`，无「正在思考」。
  - Tool：中文工具名；默认无 JSON dump；有 `resultJson` 时有「展开」。
  - Confirm：overlay 有「确认」「拒绝」；feed 不再画第二张 Confirm。
  - Final：终答文案；有 memory `source` 时「来源：」。
- R3 失败气泡「任务失败：」可附在 Final 场景或单独一条。
- R4 不测打字机节奏、进入位移、Chat↔History sharedBounds、终端风/深色截图。不改产品行为（除非为了 testTag 且规格允许用中文 `contentDescription` / 文案，优先已有文案）。

## Out of scope

- History/Settings/Debug 界面测试。
- 截图金样、Espresso、真机 CI。
- 规则 D、上架、升版本。

## Technical notes

- 构造最小 `ChatUiState`（可复用 `ChatUiStateTest` fixtures）。`createComposeRule()` + `onNodeWithText`。
- 矢量头像需要 includeAndroidResources；不要为测试改 drawable hex。
- 通过后改 `quality-guidelines.md`：Chat 五态有 Compose UI 测试；其它屏仍无。

## Acceptance Criteria

- [x] AC1 `ChatScreen` 五态 + Confirm 均有语义断言（上表文案/按钮）。
- [x] AC2 `./gradlew :feature:chat:testDebugUnitTest`（含 Robolectric）过。
- [x] AC3 无截图目录、无 `androidTest`、不改 Chat 产品交互。
