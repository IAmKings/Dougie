# Design: 渠道拆分的悬浮 / 气泡

## Boundaries

| Flavor | Mechanism | Permission |
|--------|-----------|------------|
| `play` | Existing `TaskProgressNotifier` + `NotificationCompat.BubbleMetadata` (API 29+); tap still Chat | `POST_NOTIFICATIONS` only (already). No `SYSTEM_ALERT_WINDOW` |
| `sideload` | `DougieOverlayService` in `app/src/sideload/` : `TYPE_APPLICATION_OVERLAY` floating ball | `SYSTEM_ALERT_WINDOW` in **sideload** manifest only |

Shared: `chatLaunchIntent` in `app/src/main`. Overlay types must not be referenced from `main` / `play` (same pattern as `:tool:accessibility`).

Settings rows: `ChannelHooks.shortcutLayerSettings(...)` — play vs sideload composables in flavor sourceSets so play strings cannot mention sideload.

## Play bubbles

- Attach bubble metadata to notification id 48 when `!BuildConfig.IS_SIDELOAD` and API ≥ 29.
- `setDesiredHeight`, `setAutoExpandBubble(false)`, intent = `chatLaunchIntent`.
- OEM may ignore bubbles; fallback is the existing shade notification (already shipped).
- Do not request overlay. User enables bubbles in system notification settings.

## Sideload overlay

- Preference `overlayEnabled` (sideload `SharedPreferences` or `PreferenceStore` extra — prefer a small sideload-only store to keep play APK free of overlay keys if the key name is harmless; a boolean in PreferenceStore is OK if play never shows the toggle).
- Default `false`. Toggle off → stop service / remove view.
- `Settings.canDrawOverlays`; if false, open `ACTION_MANAGE_OVERLAY_PERMISSION`.
- Service `exported=false`. Touch: click → start Chat + collapse panel if needed; drag updates `LayoutParams.x/y`.
- No Loop, no task text on the ball (icon + **Dougie** only) so lockscreen overlay does not leak prompts.

## Leak scanner

Extend `checkChannelLeak` play needles: `SYSTEM_ALERT_WINDOW`, `TYPE_APPLICATION_OVERLAY`, `DougieOverlayService`, `BIND_ACCESSIBILITY` already covered. Sideload must contain `DougieOverlayService` and `SYSTEM_ALERT_WINDOW`.

Play classpath must not include overlay class (class lives only under `sideload` sourceSet).

## Compatibility

- minSdk 26: Play bubbles no-op below 29 (shade notice only). Sideload overlay API 26+ uses `TYPE_APPLICATION_OVERLAY` (API 26).
- Two app ids already coexist.

## Rollback

Remove sideload service/manifest permission and play BubbleMetadata. Scanner assertions revert.

## Trade-off vs single overlay for both

User's split matches §17.4: Play stays store-safe; sideload opts into overlay like TapSwipe.
