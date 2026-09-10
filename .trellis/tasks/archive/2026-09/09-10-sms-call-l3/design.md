# Design: L3 SMS compose + dial

## Tools

| Name | Args | Android |
|------|------|---------|
| `sms_compose` | `to` STRING, `body` STRING | `Intent.ACTION_SENDTO` + `Uri.parse("smsto:" + encoded)` + extra `sms_body` |
| `phone_dial` | `number` STRING | `Intent.ACTION_DIAL` + `tel:` encoded. Never `ACTION_CALL`. |

Shared `PhoneNumber.canonical(raw): String` in `:core:tool` (digits, optional leading `+`, 8–20 digits after strip). Invalid → `INVALID_TOOL_ARGS`.

Port: extend `AppIntentPort` **or** new `TelecomPort.composeSms(to, body)` / `dial(number)` returning `{ok:true}` JSON. Prefer **new `TelecomPort`** so VIEW https stays separate and we never parse sms as VIEW.

FakeTelecomPort records last action.

Register in `DougieApplication` tools map (main, both flavors). IdempotencyStore same as calendar/intent.

## Manifest

Play+sideload: `<queries>` for `ACTION_SENDTO` / `ACTION_DIAL` (Android 11+). No `SEND_SMS`, no `CALL_PHONE`.

## Policy / UI

`RiskLevel.L3` → existing confirm. `toolDisplayName`: 发短信 / 打电话. Confirm: 将打开系统短信或拨号并填入内容，需你再按发送或呼叫。确认后才会打开；拒绝则跳过。

Foreground gate: reuse `isAppForeground()` like AppIntent.

## Prompt

Do not add to `LOCAL_TEACH_NAMES`. Remote inventory includes them when registered.

## Tests

- PhoneNumber + tool tests: tel/sms not via AppIntent; Fake launch; idempotency; bad number.
- ChatUiStateTest display + confirm.
- ChatPromptAssemblerTest local omits names.
- PlayShortcutCopyTest / manifest leak: no SEND_SMS/CALL_PHONE.
