# Design

`exampleArgs(name)`：无槽 `{}`；`clipboard_write` `{"text":"示例文字"}`；`calendar_create` `{"title":"开会","startIso":"2026-09-08T15:00:00+08:00"}`；`app_intent` `{"uri":"https://example.com"}`。Parser 已支持 args 对象。Policy / Confirm Card 不改。

## Rollback

从 teach 名单去掉这三名并删 exampleArgs 分支。
