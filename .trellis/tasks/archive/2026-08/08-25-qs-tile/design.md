# Design: Quick Settings Tile

## Boundaries

| Layer | Owns |
|-------|------|
| `:app` | `DougieChatTileService`, Tile label/icon, `chatLaunchIntent()`, `MainActivity` launchMode / `onNewIntent` |
| `:feature:chat` | Unchanged Chat UI; Tile does not import chat Compose |
| `:core:*` | Untouched. Tile must not call `TaskManager` |

## Contracts

### TileService

- Class: `com.dougie.app.DougieChatTileService` (or equivalent, one file).
- Manifest in `app/src/main` (both flavors):

```xml
<service
    android:name=".DougieChatTileService"
    android:exported="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

- `onClick`: `unlockAndRun` if locked (optional; if omitted, document that lockscreen click may no-op until unlocked). Prefer `unlockAndRun { startActivityAndCollapse(chatIntent) }` so the panel collapses.
- API 34+: `startActivityAndCollapse(PendingIntent)` overload if the Activity Intent API is deprecated; use the support path that compiles against `compileSdk 35` / `minSdk 26`.

### Intent

`chatLaunchIntent(packageContext: Context): Intent` —

- Component: `MainActivity`
- Action: `Intent.ACTION_MAIN` (or explicit class only)
- Extra (optional): `com.dougie.app.extra.OPEN_CHAT` = true for future routes; Chat is already default.
- Flags: `NEW_TASK | SINGLE_TOP | CLEAR_TOP`

Must not put user text, keys, or task ids in extras.

### MainActivity

- `android:launchMode="singleTop"` on the existing activity.
- `onNewIntent`: `setIntent(intent)` so Compose does not need the extra this slice (default route is Chat). If the user was on Settings, **this slice still brings Chat to front** by resetting in-memory `route` — that requires lifting `route` to Activity state that `onNewIntent` can set.

**Decision**: Tile always shows Chat, even if the Activity was on Settings. Implementation: hoist `route` with `mutableStateOf` owned so `onNewIntent` / first `onCreate` can set `AppRoute.Chat` when the Tile extra is present. If extra absent (launcher icon), keep current route / default Chat on cold start.

## Data flow

Tile click → TileService → start MainActivity → ChatRoute collects existing `TaskManager.task` (no submit).

## Compatibility

- minSdk 26: `TileService` available (API 24+).
- Play and sideload share `src/main` service; no flavor split.
- `checkChannelLeak` must not treat `TileService` as Accessibility leak (verify the Gradle task regex; extend allowlist only if it false-positives).

## Trade-offs

- Open Chat only vs submit from Tile: submit would skip visible confirm UX for L2 tools. Rejected for this child.
- Feature module Tile: would still need `:app` merge and Activity class; keep service in `:app`.

## Rollback

Remove service from manifest + delete Tile class + revert `launchMode`. Users may need to remove a leftover Tile once; no data migration.
