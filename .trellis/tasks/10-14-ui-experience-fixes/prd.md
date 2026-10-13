# UI 体验修复四项

## Goal

设置返回记住滚动位置、Splash 主题跟随、聊天历史不再重放流式、消息长按复制

## Requirements

- R1 设置主列表 ScrollState 提升到路由宿主（MainActivity），子页返回不再回到列表顶部。
- R2 values（浅色）主题补 windowBackground #F8FAF9（夜间已有 #191C1C），Splash 底色跟随系统深浅模式。冷启动 Splash 由系统按系统深浅模式渲染属平台限制，应用内主题偏好经 wrapThemeContext 已生效。
- R3 聊天打字机进度提升到屏幕级 SnapshotStateMap（key=listKey），完成的消息滚出再滚回立即整段显示，不再重放流式。
- R4 Agent/User 气泡 combinedClickable 长按复制全文，Toast「已复制」。

## Acceptance Criteria

- [x] AC1 双 flavor 编译 + 既有测试全绿（含 feature/chat、feature/settings）。
- [x] AC2 真机验证四项体验（设置返回位置、Splash 底色、历史不重放、长按复制）。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
