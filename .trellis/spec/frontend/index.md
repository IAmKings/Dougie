# Frontend Development Guidelines

> Jetpack Compose conventions for Dougie (`:feature:*`, `:app`).

## Overview

Screens collect `StateFlow` from ViewModels. There is no React, no Hilt, and no shared `:core:ui` module yet.

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Directory Structure](./directory-structure.md) | `:feature:chat` / settings / memory / history / debug / `:app` | Filled |
| [Component Guidelines](./component-guidelines.md) | `*Route` / `*Screen`, `DougieColors` | Filled |
| [Hook Guidelines](./hook-guidelines.md) | Compose `remember` / `LaunchedEffect` (not React `use*`) | Filled |
| [State Management](./state-management.md) | TaskManager + PreferenceStore | Filled |
| [Quality Guidelines](./quality-guidelines.md) | JVM UI tests, channel leak | Filled |
| [Type Safety](./type-safety.md) | Kotlin sealed UI models, kotlinx.serialization at tool boundary | Filled |

**Language**: Spec text is English; user-facing product strings are Chinese.
