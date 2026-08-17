# Design — Phase 1a Cloud LLM Battery Tool

## Boundaries

```text
:core:model        + EgressPolicy, LlmRequest/LlmEvent (minimal), errors
:core:policy       EgressPolicyChecker (JVM) — new module if needed, else :core:runtime
:core:llm          OpenAICompatibleProvider (OkHttp, JVM) + keep FakeLlmProvider
:core:runtime      EgressGateway, LoopEngine talks only to Gateway + AgentTool map
:tool:system       Android library: DeviceBatteryTool
:data:preferences  Android library: encrypted API key + policy flags
:feature:settings  Provider form + egress consent
:feature:chat      Navigate to settings; show egress/network errors
:app               Wire OkHttp, BatteryTool, PreferenceStore, NavHost
```

Prefer `:core:runtime` for `EgressGateway` if a new `:core:policy` would be empty besides one function. If checker + PolicyDecision types grow past ~2 files, add `:core:policy`.

## Contracts

### EgressGateway
```kotlin
suspend fun complete(request: LlmRequest, provider: LlmProvider): LlmResponse
```
Phase 1a is **non-streaming**: collect provider `Flow<LlmEvent>` internally or call a `complete()` on the provider. Public LoopEngine can stay `LlmResponse` based.

- If `!policy.allowCloud && !provider.isLocal` → throw/return error mapped to `FAILED` + user text `云端调用已被拦截。请先在设置中授权数据出境。`
- Fake provider `isLocal = true` so JVM tests unchanged.

### OpenAICompatibleProvider
- `POST {baseUrl}/chat/completions` (trim trailing slash)
- Headers: `Authorization: Bearer <key>` (never log)
- Body: messages + tools (battery function schema)
- Parse `choices[0].message.tool_calls[0]` → `LlmResponse.ToolCall`
- Parse `choices[0].message.content` → `LlmResponse.FinalAnswer`
- Timeouts via OkHttp (connect 15s, call 60s)

### DeviceBatteryTool
- name `"battery"`
- Read extras from `ACTION_BATTERY_CHANGED`; percent = level/scale; charging = status charging/full
- Same JSON keys as Fake: `battery_percent`, `charging`

### Preferences
- `allow_cloud: Boolean` default false
- `egress_consent_at: Long?`
- `base_url`, `model`
- `api_key` in EncryptedSharedPreferences (MasterKey)

## Data flow

```text
Chat send
  → TaskManager
  → LoopEngine
  → ContextBuilder (Phase 1a: user + tool traces only, no Memory)
  → EgressGateway.complete
  → OpenAI HTTP
  → ToolCall battery
  → DeviceBatteryTool
  → second LLM complete
  → FinalAnswer
  → StateFlow → Chat
```

Settings save updates store; next `submit` reads current policy/provider from app-held factory or reload per request.

## UI

- Chat TopBar existing settings/shield control → `feature:settings` route.
- Settings: Dougie 标题；出境开关 + 固定说明「本次请求可能将输入、必要上下文和 Tool Result 发送至第三方 LLM 服务。」；URL / Key / Model 字段；保存。
- Colors: reuse `DougieColors` (extract to `:core:ui` only if copy-paste would exceed one file; otherwise duplicate tokens once in settings — prefer small `:core:ui` **only** if both features already share more than colors).

## Trade-offs

| Choice | Why |
|--------|-----|
| Non-streaming complete() | Phase 1a; 1b adds Flow to UI |
| Fake stays isLocal | Tests and emulator without keys |
| EncryptedSharedPreferences | PRD §9.2 Keystore; not plaintext prefs |
| Battery in `:tool:system` | Keep core JVM-pure |

## Rollback

Feature flags: if HTTP is broken, debug can inject Fake by not enabling allowCloud. Revert settings module by pointing AppBar nowhere.
