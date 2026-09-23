# 晨间简报 — 执行

开始前确认 `09-23-soul-rules` 的注入已经在当前树上。否则先完成那个子任务。

## Order

1. 在 `ScheduleMath.kt` 增加 `MORNING_BRIEF_DRAFT`。
2. `ScheduleMathTest` 断言文案含「日历」「电量」「记忆」，含只读限制，不含 `{` 与 `calendar_create`。
3. `ScheduleSettings` 增加「晨间简报」按钮，只改本地 `draft` 与 `daily`。
4. 不改 `DougieScheduleReceiver` 的通知正文。若需要，补一条测试：`formatScheduleNotice` 仍不含草稿常量。
5. 手点路径：添加一条每天简报，确认通知正文无草稿；点击后输入框为常量且未出现工具卡，直到用户发送。

## Validation

```bash
./gradlew :app:testDebugUnitTest --tests com.dougie.app.ScheduleMathTest
```

## Rollback

删除按钮和常量。已存日程用设置里现有删除。

## Do not

- 不在接收器里查询日历、电量或记忆。
- 不调用 `TaskManager.submit`。
- 不把草稿放进通知正文。
