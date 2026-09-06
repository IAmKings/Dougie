# 系统返回与滑动返回二级页

## Goal

系统返回键和边缘滑动返回与顶栏返回同一条路由：先关附件预览，再退二级页，Chat 上再退才离开应用。不做预测性返回动画。

## Background

`MainActivity` 用 `AppRoute` + `when` 换页。无 `BackHandler`。设置/权限顶栏回 Chat；Debug/OpenApps 回 Settings。记忆/历史是底栏。Chat 预览点空白关闭。边缘滑动目前 finish Activity。

## Requirements

- R1 `:app` 用 `BackHandler` 消费非 Chat 返回，目标与顶栏一致：OpenApps/Debug → Settings；Settings/Permissions/Memory/History → Chat。
- R2 Chat 且预览打开：返回关预览。
- R3 Chat 无预览：不拦截，保持 Activity 默认（离开或进后台）。
- R4 不改 feature 顶栏；不引入 Navigation Compose；不做 Predictive Back 转场。抽出可测的 pop 函数（route + 是否有预览）。

## Out of scope

- 预测性返回动画、全屏跟手转场、多 Activity。

## Acceptance Criteria

- [ ] AC1 JVM：`popAppRoute`（名称以实现为准）覆盖 Chat+预览关预览、Settings→Chat、Debug/OpenApps→Settings、Chat 无预览表示交给系统。
- [ ] AC2 设置页系统/手势返回到 Chat，进程仍在（真机）。
- [ ] AC3 设置→开发者：返回到设置。
