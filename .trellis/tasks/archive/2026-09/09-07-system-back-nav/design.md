# Design

`AppRoute` 与预览标志留在 `:app`。纯函数：

```
data class BackNav(val route: AppRoute, val previewOpen: Boolean)
fun consumeBack(state): BackNav?  // null = 不拦截
```

- Chat + previewOpen → 关预览，route 不变（其它路由上的残留预览不拦截 pop）
- OpenApps/Debug → Settings
- Settings/Permissions/Memory/History → Chat
- Chat 无预览 → null

`MainActivity` `BackHandler(enabled = consumeBack != null)` 写回 `routeState` / `previewState`。

不设 `android:enableOnBackInvokedCallback` 本刀除非 BackHandler 在目标 API 收不到手势（实现时若手势仍 finish，再开，且仍不要自定义 Predictive 动画）。
