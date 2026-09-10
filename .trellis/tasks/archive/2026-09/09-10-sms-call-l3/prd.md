# 侧载 L3 短信与电话

## Goal

确认后打开**系统短信界面或拨号界面**并填好号码（短信再填正文）。用户仍须在系统界面按发送/呼叫。Agent 不静默发出、不申请 `SEND_SMS`/`CALL_PHONE`。L3 每次确认。不教 0.6B。Play 与侧载都注册。`app_intent` 仍拒绝 `tel`/`sms`。

## Background

- 用户采纳：填好系统界面，不要直接发出。
- PRD Beta L3；日志禁止 SMS 内容。`AppIntentAllowlist` 已拒 tel/sms。
- `app_intent` 要求 Dougie 在前台再 launch；本刀同样。

## Requirements

- R1 工具 `sms_compose`：`to`（号码）、`body`（正文）。`ACTION_SENDTO` `smsto:` + `sms_body`。L3。幂等 `taskId+toolCallId`。
- R2 工具 `phone_dial`：`number`。`ACTION_DIAL` `tel:`（禁止 `ACTION_CALL`）。L3。同样幂等。
- R3 号码：可选 `+` + 数字，长度上限；非法 → `INVALID_TOOL_ARGS`。正文上限（建议 1000 字）。确认卡展示参数。拒绝零 Intent。
- R4 不教 0.6B。不 Logcat 号码/正文。Play 无 `SEND_SMS`/`CALL_PHONE`。`checkChannelLeak` 拒这些权限出现在 play **若我们误加**；本刀本来就不加。
- R5 无解析器则中文失败、零发出。Dougie 非前台 → 与 app_intent 相同失败，不 launch。

## Out of scope

- `SmsManager` / `ACTION_CALL` / 读收件箱 / 通话记录。
- 群发、彩信。JS/Python 调通讯。教 0.6B。

## Acceptance Criteria

- [x] AC1 侧载确认后打开短信 App，收件人与正文已填；用户不按发送则发不出去。拒绝确认零界面。
- [x] AC2 确认后打开拨号，号码已填，未自动接通。`app_intent` `tel:` 仍拒绝。
- [x] AC3 JVM：合法号码 Fake launch 一次；非法号码/重复幂等；本地 prompt 无这两个工具名。Play 源码无 SEND_SMS。
