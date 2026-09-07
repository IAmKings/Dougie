# Design

Sideload-only `WindowManager` overlay stays in `DougieOverlayService` (not Compose, not `:feature:chat`).

Collapsed ball: `FrameLayout` or `ImageView` 48dp × 48dp. `GradientDrawable` oval fill `DougieColors.Primary` `#3D5198`. `ImageView` `R.drawable.dougie_logo` (merged from `:feature:chat`), inset ~8dp, `ColorFilter` white `SRC_IN` so the mark reads on blue. `contentDescription` = `R.string.app_name`. Touch/drag/`expand`/`collapse`/`captureThenOpenChat` unchanged.

Do not tint or edit `dougie_logo.xml` (launcher + Chat LOCAL). Do not use `super_dougie` / `dougie_logo_unavailable`.

Expanded panel stays `TextView` rows. Play flavor must not import overlay types.

Tests: extend sideload source scan (`OverlayCopyTest` or sibling) so `DougieOverlayService.kt` contains `dougie_logo` and the collapsed view is not `setText(app_name)`. `PlayShortcutCopyTest` / `checkChannelLeak` unchanged.
