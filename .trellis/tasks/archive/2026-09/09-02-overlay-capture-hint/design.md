# Design: 侧载悬浮球截屏可发现性

## Boundaries

| Piece | Where |
|-------|--------|
| 展开面板 + 截/开聊 | sideload `DougieOverlayService` only |
| 设置文案 | `app/src/sideload/res/values/strings.xml` |
| Chat 引导 | `:app` 注入 Chat，不改 `IntentRouteAnswers` |
| 权限中心上层显示 | sideload `ChannelHooks` 槽位；`:feature:permissions` 只吃可选 extra item |
| Play | `ChannelHooks` no-op；strings 无「上层显示」 |

## Overlay

`FLAG_NOT_FOCUSABLE` 下用第二块 overlay 视图做两项按钮（固定中文，不是用户 prompt）。展开时球仍可点以收起。选「截取屏幕」后：`visibility=INVISIBLE` 面板与球 → pin → restore → launch（沿用现网，避免球进画面）。

缺 `canDrawOverlays`：`ACTION_MANAGE_OVERLAY_PERMISSION`，不 launch Chat。缺投屏：现网 `overlayAttachError` + Chat。

## Chat hint

`ChannelHooks.screenShortcutHint(task): String?`：sideload 在 `COMPLETED` && `LOCAL_INTENT` && 唯一/最后成功工具名为 `screen_capture` 时返回引导句，否则 null。Play 恒 null。

`MainActivity` 传 `ChatScreen` 的只读提示（附件状态行或终答下方 `Text`，非 `ChatItem.AgentMessage`）。`startReplySpeak` / 自动播报继续用 `task.finalAnswer`。手动播报回调不要把 hint 拼进 `onSpeakReply` 的 text。

## Permissions

`PermissionsRoute` 增加可选 `extraItems` / `onOpenOverlaySettings`。Sideload 提供「上层显示」：`granted = Settings.canDrawOverlays`，点击走与设置开关相同的系统 URI。不要把 `DougieOverlayService` 类型写进 `:feature:permissions`。

## Compatibility

单击即截的旧手势取消。Play 气泡文案不动。
