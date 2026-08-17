# Implement — Phase 3b Location + Screen Sense

## Checklist

1. LocationPort + LocationTool L1; Android LocationManager last known coarse; tests.
2. ScreenFrameStore + GrayscaleNccMatcher + ScreenMatchTool tests with a synthetic white square on black.
3. ScreenCaptureTool metadata-only; Fake port in JVM; Android MediaProjection if token else deny.
4. Permission Center location request + projection launcher; manifest ACCESS_COARSE_LOCATION, FOREGROUND_SERVICE / mediaProjection if FGS used.
5. Register tools in DougieApplication; keep PolicyEngine isGranted.
6. Never pass gray/base64 into OpenAI message builder (only ToolResult JSON).

## Validation

```bash
./gradlew :core:runtime:test :core:tool:test :core:llm:test :app:assembleDebug
```

## Review gates

- Fake 3-loop still green
- Capture JSON has no pixel payload
- Match without frame fails
- core JVM-only

## Rollback

Drop FGS/projection; leave location only if capture is unstable.
