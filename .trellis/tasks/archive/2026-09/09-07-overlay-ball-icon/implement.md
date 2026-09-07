# Implement

1. `DougieOverlayService` collapsed view → 48dp round logo ball (white-tinted `dougie_logo`).
2. `OverlayCopyTest` (or sibling) asserts source: `dougie_logo`, no collapsed `TextView`+`app_name`.
3. Spec: `.trellis/spec/frontend/quality-guidelines.md` + `logging-guidelines.md` collapsed chrome sentence.
4. `./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak`（JDK 17）。

真机：侧载开悬浮球，收起为圆标；点开两菜单。
