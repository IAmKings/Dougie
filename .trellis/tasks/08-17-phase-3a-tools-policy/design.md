# Design — Phase 3a Calendar Clipboard Policy

## Boundaries

```text
:core:model        RiskLevel, TaskStatus.AWAITING_CONFIRMATION, ToolDescriptor.risk/permission
:core:runtime      PolicyEngine; LoopEngine pauses on L2 via CompletableDeferred
:core:tool         ports: CalendarPort, ClipboardPort + JVM fakes
:tool:system       Android CalendarContract + ClipboardManager + Activity foreground check
:feature:chat      ConfirmCard; confirm/reject callbacks
:feature:permissions  Permission Center
:app               register tools; Activity-based permission grants fed to PolicyEngine
```

## Contracts

```kotlin
enum class RiskLevel { L0, L1, L2 }

enum class TaskStatus { ..., AWAITING_CONFIRMATION }

data class ToolDescriptor(
  val name: String,
  val description: String = "",
  val properties: Map<String, ToolParamSpec> = emptyMap(),
  val riskLevel: RiskLevel = RiskLevel.L0,
  val androidPermission: String? = null, // e.g. android.permission.READ_CALENDAR
)

sealed class PolicyDecision {
  data object Allow : PolicyDecision()
  data class DeniedPermission(val permission: String) : PolicyDecision()
  data object NeedsConfirmation : PolicyDecision()
}

class PolicyEngine(private val isGranted: (String) -> Boolean) {
  fun decide(descriptor: ToolDescriptor): PolicyDecision
}
```

LoopEngine after sanitize:
1. `decide`
2. DeniedPermission → fail skip copy
3. NeedsConfirmation → status AWAITING_CONFIRMATION, `emit`, `await confirm` (LoopEngine.confirmGate)
4. Allow → execute as today

`TaskManager.confirm()` / `reject()` complete the gate. New submit while awaiting confirmation is ignored (already busy).

Foreground: `ClipboardPort.isAppForeground(): Boolean` from ProcessLifecycleOwner in `:app`.

CalendarPort:
```kotlin
suspend fun queryUpcoming(limit: Int): String // JSON
suspend fun createEvent(title: String, startIso: String, idempotencyKey: String): String
```

## Data flow

```text
LLM ToolCall calendar_create
  → Sanitizer
  → Policy L2
  → Chat Confirm Card
  → user Confirm
  → CalendarPort.createEvent(idempotencyKey)
  → TOOL_RESULT → next LLM turn
```

OpenAI: build `tools` from `tools.values.map { it.descriptor }`.

## UI

- Confirm Card: 确认 / 拒绝 equal weight; show args JSON; L2 badge.
- Permission Center: sections 日历; status; 去授权 button (ActivityResultContracts).
- Chat top shield → permissions (in addition to settings).

## Trade-offs

| Choice | Why |
|--------|-----|
| Pause same coroutine with Deferred | Avoid rewriting LoopEngine as a state machine machine |
| In-process idempotency map | Phase 4 owns durable store |
| Skip Location/screen this slice | Independently verifiable; OpenCV is a separate risk |
| Permission miss = skip fail | Simpler than mid-loop Activity request; Center still requests grants for next turn |

## Rollback

If confirm deadlock: auto-reject on timeout; L2 tools unregistered.
