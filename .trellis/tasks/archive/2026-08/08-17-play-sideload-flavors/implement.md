# Implement — Play / Sideload Flavors

## Checklist

1. App flavors + BuildConfig + `buildConfig = true`.
2. `:tool:accessibility` module; only `sideloadImplementation`.
3. Sideload AccessibilityService no-op + xml; play sourceSet ChannelTools empty.
4. Preference consent; sideload onboarding UI; TapSwipeTool gated.
5. PolicyEngine L3 = always confirm (if not already).
6. Leak check via merged manifests after assemble.
7. Update directory-structure spec.

## Validation

```bash
./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug :core:runtime:test
# then inspect play vs sideload merged manifests
```

## Review gates

- play manifest: no AccessibilityService
- play gradle deps: no :tool:accessibility
- no sideload store URL in `app/src/play` or `app/src/main` strings
- Fake 3-loop still green

## Rollback

Delete flavor sourceSets; `sideloadImplementation` line.
