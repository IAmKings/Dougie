# Design — Phase 3b Location + Screen Sense

## Boundaries

```text
:core:model        AndroidPermissions.ACCESS_COARSE_LOCATION
:core:tool         LocationPort, LocationTool
                   ScreenFrameStore, ScreenCapturePort, ScreenCaptureTool, ScreenMatchTool
                   GrayscaleNccMatcher (pure JVM)
:tool:system       AndroidLocationPort, AndroidScreenCapturePort (MediaProjection)
:feature:permissions  location + projection rows
:app               FGS for projection if required; register tools
```

## Contracts

```kotlin
interface LocationPort {
  suspend fun lastKnownCoarse(): String // JSON, no logging
}

class ScreenFrame(
  val id: String,
  val width: Int,
  val height: Int,
  val gray: ByteArray, // row-major 0..255, never logged
)

interface ScreenFrameStore {
  fun put(frame: ScreenFrame)
  fun last(): ScreenFrame?
}

interface ScreenCapturePort {
  suspend fun capture(): ScreenFrame // throws / returns denial
}
```

`screen_capture` execute: `port.capture()` → store → `{"capture_id","width","height"}`.

`screen_match`: load template gray from `TemplateLibrary` (test fixtures + one bundled id `solid` or `logo`); NCC vs last frame; if last==null or confidence < 0.6 → `ToolResult(isFatal=true)`.

MediaProjection:
- Holder in `:app` / `:tool:system` for consent Intent.
- Permission Center launches `MediaProjectionManager.createScreenCaptureIntent()`.
- Capture uses ImageReader + VirtualDisplay; if API 34+ start a short-lived FGS then stop after one frame.
- If no token: map to `UserFacingErrors.PERMISSION_DENIED` (or a screen-specific copy still user-facing).
- Foreground check like clipboard.

Do **not** add OpenCV maven artifacts.

## Data flow

```text
location → LocationPort → JSON to LLM (coarse)
screen_capture → pixels in Store → metadata JSON to LLM
screen_match → NCC(store.last, template) → JSON to LLM (untrusted)
```

## UI

Permission Center: 位置 (runtime); 屏幕截取 (projection status granted/not).

## Trade-offs

| Choice | Why |
|--------|-----|
| No OpenCV | APK size; algorithm is testable on JVM |
| Metadata-only capture result | PRD: 截图不进 Prompt |
| In-memory frame | Avoid disk/PII files this slice |
| Coarse location only | Privacy; ACCESS_FINE not required |

## Rollback

Unregister the three tools; Permission Center hides new rows.
