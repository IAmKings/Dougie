# Design: 思考→工具卡状态切换

## Boundary

只改 `:feature:chat` Compose。不改 `toChatUiState` 列表结构、`listKey`、TaskManager。不 bump DB。

## Enter vs switch

`nextChatItemEnter` 仍把未见过的 `ToolCard` 放进 `playKeys`（Confirm 除外）。差别在 `ChatFeed`：

- `UserMessage` / `Thinking` / `AgentMessage`：现网 `ChatItemEnterMotion`（200ms FastOutSlowIn，淡入 + 8dp）。
- `ToolCard`：新 `ToolSwitchEnterMotion`（150ms `LinearOutSlowInEasing`，只淡入，无 `translationY`）。思考之后和确认之后共用，因为都是新 `tool-{id}`。

`remember { playEnter }` 锁首帧，与气泡进入 / Confirm 相同。`ANIMATOR_DURATION_SCALE == 0f`：无位移可关，仍只跑淡入（与气泡进入降级一致）。

不要把 `ToolCard` 从 `playKeys` 拿掉后再做第二套 seen。不要 `LazyItemScope.animateItem()`。

## Same-key content

`ThinkingChip`：`live` / 文案变化用 `AnimatedContent`（或等价 fade），150ms LinearOutSlowIn。第一次组合直接显示当前态。

`ToolCallCard`：标题 `label`（准备/正在调用/已调用/失败）用 `AnimatedContent`，150ms LinearOutSlowIn。结果 JSON 区不另做一套。第一次组合不从空白切过来。

不要改 `listKey`。不要把思考芯片和工具卡合成一行。

## Follow / overlay

不改 `shouldFollowChatFeed`、`pendingFocusKey`、Confirm 覆盖层 250ms 上滑。Confirm 在时列表里没有 `ToolCard`；确认后 `playKeys` 含新 `tool-{id}`，走 `ToolSwitchEnterMotion`。

## Tests

现有 `ChatUiStateTest`（含 `nextChatItemEnter` 仍对 ToolCard 给 playKeys）继续过。无新 mapper 也可不增测试；若抽出 `isBubbleEnterItem` / 时长常量，只锁「ToolCard 不走 8dp」。无 Compose UI 测试。

## Don't

- 不确定进度条、共享元素、Confirm 离场。
- 给 ToolCard 再叠 200ms 气泡进入。
- 打开窗口重播 live→死或卡上文案。
