# Design — Play / Sideload Flavors

## Boundaries

```text
:app play sourceSet          ChannelHooks without tap; no a11y
:app sideload sourceSet      ChannelHooks + onboarding; depends :tool:accessibility
:tool:accessibility          Android lib: stub TapSwipeTool + AccessibilityService (sideload only)
:app build.gradle.kts        flavorDimensions, BuildConfig.IS_SIDELOAD
```

## Contracts

```kotlin
// app/src/play/.../ChannelConfig.kt  and sideload counterpart OR BuildConfig
object ChannelConfig {
  val isSideload: Boolean // BuildConfig.IS_SIDELOAD
}

fun extraTools(...): Map<String, AgentTool>  // empty on play
```

`DougieApplication` in `main` calls `ChannelTools.register(...)` defined per flavor to avoid play compiling accessibility types.

## Manifest

`app/src/sideload/AndroidManifest.xml` merge:

```xml
<service
  android:name=".sideload.DougieAccessibilityService"
  android:exported="false"
  android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
  <intent-filter>
    <action android:name="android.accessibilityservice.AccessibilityService" />
  </intent-filter>
  <meta-data
    android:name="android.accessibilityservice"
    android:resource="@xml/accessibility_service_config" />
</service>
```

Service implementation can be a no-op (no click dispatch).

## Onboarding

Sideload MainActivity wrapper: if `sideload_consent` false, full-screen checklist (Accessibility 用途、非银行/支付、可撤销)，两按钮「同意并继续」/「退出」。Store in PreferenceStore or sideload-only SharedPreferences key `sideload_a11y_consent`.

TapSwipeTool: L3 equivalent — if LoopEngine only special-cases L2 today, either extend PolicyEngine for L3 always-confirm or reuse L2 NeedsConfirmation plus extra consent check inside the tool (`consent && IS_SIDELOAD`).

## Leak check

Script or Gradle task after assemble:

```
grep -R AccessibilityService app/build/intermediates/merged_manifest/playDebug
# must be empty
grep -R AccessibilityService app/build/intermediates/merged_manifest/sideloadDebug
# must hit
```

## Trade-offs

| Choice | Why |
|--------|-----|
| Separate `:tool:accessibility` module | Class-level leak into play if tool lives in `:core:tool` |
| Stub service | Proves flavor isolation without shipping TapSwipe automation |
| Default flavor play | Existing `assembleDebug` / Android Studio run stays Play-shaped |

## Rollback

Remove flavors; move files back to main.
