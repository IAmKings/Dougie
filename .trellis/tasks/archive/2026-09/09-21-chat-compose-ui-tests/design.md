# Design: Compose UI 五态测试

## Boundaries

| Module | Owns |
|--------|------|
| `:feature:chat` `src/test` | Robolectric Compose 规则 + 五态断言 |
| `ChatScreen` | 尽量不改；已有中文文案作 matcher |
| CI | 仍跑 `testDebugUnitTest`，无新 job |

## Contracts

```
class ChatScreenFiveStateTest {
  @get:Rule val compose = createComposeRule()
  // setContent { ChatScreen(uiState = …, listState = rememberLazyListState(), onSend = {}) }
}
```

- User：`onNodeWithText(userText)`；语音：`onNodeWithText("语音转写")`。
- Thinking：`onNodeWithText("思考中… [循环 1]", substring = true)`；`onNodeWithText("正在思考", substring = true).assertDoesNotExist()`。
- Tool：`onNodeWithText("电池工具")`（或注入的 display name）；`onNodeWithText("展开")` 当 `resultJson != null`；不出现 raw JSON 关键字段。
- Confirm：`onNodeWithText("确认")` + `onNodeWithText("拒绝")`；`onNodeWithText("确认", useUnmergedTree)` 只 overlay。
- Final：终答；`onNodeWithText("来源：…")` 当有 citation。

失败：`onNodeWithText("任务失败：", substring = true)`。

`includeAndroidResources = true`。Robolectric `@GraphicsMode(NATIVE)` 若向量 Inflate 失败再开，先最小配置。

## Compatibility

不改 `listKey`、overlay 行为、prefs。`ChatUiStateTest` 仍在。

## Risks

- Robolectric + Compose BOM 2024.12 版本要对上；失败则钉具体 robolectric 4.14+。
- Confirm overlay 可能需 `useUnmergedTree`。
- 顶栏头像 `ColorFilter` 不在本刀测像素。
