# Implement: Debug page

1. `AuditEntry` + `AuditLog.listRecent`; `SqliteAuditLog` + in-memory/no-op tests.
2. Module `:feature:debug` (copy history gradle/compose pattern).
3. `DebugViewModel` maps task + audit; tests assert no resultJson/prompt fields.
4. Settings row 「开发者」; `MainActivity` route.
5. Spec: frontend directory-structure + logging (UI must not dump args).
6. `JAVA_HOME=.../openjdk@17/... ./gradlew :feature:debug:testDebugUnitTest :core:runtime:test :app:checkChannelLeak`

## Risky

- Widening `AuditLog` fun interface → all fakes in LoopEngine tests must compile (default empty list or new methods on interface with default in Kotlin).
