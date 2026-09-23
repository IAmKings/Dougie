# 晨间简报 — 设计

## Boundary

不新增调度类型。简报是一条 `daily = true` 的普通 `ScheduleItem`，`draft` 使用固定文案。接收器、通知文案、点击填充都走现有函数。

依赖：`ChatPromptAssembler` 已能注入常驻规则。草稿本身不嵌入规则正文；用户发送时由系统前缀带上规则。

## Draft

常量放在 `ScheduleMath.kt`（纯 JVM，`:app` 单测已覆盖这个文件）：

`MORNING_BRIEF_DRAFT`：请用中文做晨间简报，内容为今天的日历、当前电量、一条相关记忆；只读；不要新建日程、不要改剪贴板、不要打开应用。

通知点击继续 `draftForNotificationTap` → `applyScheduleDraft`。接收器继续 `putPendingDraft`，不读日历。

## UI

`ScheduleSettings` 在草稿框旁加 `TextButton`「晨间简报」：

- `daily = true`
- `draft = MORNING_BRIEF_DRAFT`
- 不调用 `ScheduleStore.add`
- 小时分钟保持当前选择

「添加」仍是唯一写入点，并受现有最多 8 条限制。

## Safety

草稿是用户句子，不是工具调用。L2 确认链不改。本任务不在接收器里构造 `AgentTask`。

## Rollback

去掉按钮即可。已经添加的日程仍是普通每日草稿，用户可在现有列表里删除。
