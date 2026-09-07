# 权限中心补齐无障碍 L3

## Goal

Sideload 权限中心补齐 **无障碍 / L3** 行，对应已实现的 `tap_swipe`（无障碍服务）。Play 仍不展示、不泄漏 Accessibility 实现。本刀不教本地 0.6B 调用 `tap_swipe`。

## Requirements

- R1 Sideload 权限中心在 `extraItems` 中增加一行：标题 **无障碍**，风险 **L3**，`highRisk=true`。
- R2 未授权按钮打开系统 `Settings.ACTION_ACCESSIBILITY_SETTINGS`；已授权按钮同样进入该设置以便关闭服务。
- R3 `granted` 以 `GesturePort.isConnected()`（`DougieAccessibilityService.instance != null`）为准，从系统设置返回后 `ON_RESUME` 刷新。
- R4 Play `ChannelHooks.accessibilityPermissionItem` 返回 `null`；`:feature:permissions` 不依赖 `:tool:accessibility`。
- R5 文案说明：经确认的点击/滑动；银行、支付、密码管理器不可操作。不把 SCREEN 像素送云。
- R6 不改 Loop / TapSwipeTool 门闩；不把 `tap_swipe` 加入 `LOCAL_TEACH_NAMES`。

## Acceptance Criteria

- [ ] AC1 Sideload 权限中心可见 **无障碍** L3 行；Play 权限中心无该行。
- [ ] AC2 去系统设置后返回，服务已开则显示已授权，已关则未授权。
- [ ] AC3 `:app:checkChannelLeak` 过；Play `ChannelHooks` 不引用 Accessibility 类型。
- [ ] AC4 本地 LLM teach 列表仍不含 `tap_swipe`。

## Notes

用户「先补齐」= 补权限中心展示与系统设置入口，不是补 0.6B 坐标教学。
