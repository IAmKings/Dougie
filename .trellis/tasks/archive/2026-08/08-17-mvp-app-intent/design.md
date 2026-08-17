# Design — MVP App Intent Tool

## Boundaries

```text
:core:tool         AppIntentPort, AppIntentTool, UriAllowlist
:tool:system       AndroidAppIntentPort
:app               register tool + foreground + IdempotencyStore
:core:runtime      existing L2 confirm + PolicyEngine
```

## Contracts

```kotlin
interface AppIntentPort {
  fun launchView(uri: String, packageName: String?): String // JSON {ok:true, launched:"https://example.com"}
}

object AppIntentAllowlist {
  fun validate(uri: String, packageName: String?): String // canonical uri or throw AgentException
}
```

Allowed:
- https/http with non-empty host
- geo:
- package:com.foo.bar  OR uri https + package extra for specific app

Launch package: `package:com.android.settings` → getLaunchIntentForPackage.

Idempotency: same as CalendarCreateTool.

Foreground: ClipboardPort-style `isForeground()`.

## Data flow

```text
LLM app_intent
  → Sanitizer (uri string required)
  → allowlist (before Policy/Confirm; tel/file/sms never show a card)
  → Policy L2 confirm
  → foreground check
  → idempotency
  → port.launchView
```

## Trade-offs

| Choice | Why |
|--------|-----|
| L2 not L1 | Leaving Dougie to another app is user-visible side effect |
| No tel/sms | PRD §3.2 |
| Allowlist not denylist | Safer against intent:// wrappers |

## Rollback

Unregister `app_intent`.
