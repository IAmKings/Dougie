# Design: Phase 5j Model Download

## Boundaries

| Piece | Module |
|-------|--------|
| `ModelPack`, `ModelInstaller` | `:core:tool` |
| `OkHttpModelGet` | `:tool:system` |
| Catalog URLs | Injected; no LLM tool |

## Contracts

`ModelInstaller.install(pack, destRoot, userConfirmed, onProgress)`

HTTPS only. SHA-256 hex (lowercase) of each file. Write `name.part` then rename.

## Rollback

Unregister installer from Application.
