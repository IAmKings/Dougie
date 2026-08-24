# Backend Development Guidelines

> JVM/core conventions for Dougie (`:core:*`, `:data:*`, `:tool:system`).

## Overview

Agent runtime is Kotlin JVM. Android SDK types stay out of `:core:*`. Spec files below are filled from this repository.

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | `:core:*` JVM-pure, `:data:memory`, `:data:tasks` | Filled |
| [Database Guidelines](./database-guidelines.md) | SQLite FTS4 `memory_facts` + `dougie_tasks.db` | Filled |
| [Error Handling](./error-handling.md) | `UserFacingErrors` → Chat FAILED | Filled |
| [Quality Guidelines](./quality-guidelines.md) | JUnit, `checkChannelLeak`, no second ORT | Filled |
| [Logging Guidelines](./logging-guidelines.md) | No prompts/keys/HTTP bodies | Filled |

**Language**: Spec text is English; user-facing product strings are Chinese.
