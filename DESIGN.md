# Dougie Stitch Design Resources

## Source

- Stitch project: `Dougie`
- Project ID: `9130174572964021196`
- Retrieved: 2026-08-16
- Source: Stitch MCP (`https://stitch.googleapis.com/mcp`)
- Screens discovered: 75

## Local resource layout

The downloaded resources are grouped under `design/` by functional area:

- `品牌/` — In-app Chat: Super (`Super-Dougie.svg` → `super_dougie.xml`) when remote LLM is usable; official `Dougie-Logo.svg` (`dougie_logo.xml`) when only a local chat LLM is ready; Noob (`Noob-Dougie.svg` → `dougie_logo_unavailable.xml`) when none are configured or a remote call failed. Launcher stays `Dougie-Logo.svg` with 16% inset. `Dougie-Logo.png` / `Super-Dougie.jpeg` are sources/mocks, not Android drawables.
- `Agent与工具/` — Agent Console, tasks, tools, routing, semantic memory, and registries
- `权限与系统/` — permissions, privacy, providers, model loading, policies, and system states
- `语音与对话/` — speech recognition, audio analysis, Dougie, and conversation views
- `其他功能/` — remaining functional screens not matched to a narrower category

Each page keeps its Stitch screen ID in the filename and includes the available `.html` and `.png` resources. The screen title is preserved in the filename for quick lookup.

## Design direction

The Dougie screens form a desktop-and-mobile product system centered on local agents, tools, permissions, model/runtime state, and multimodal interaction. Preserve the following principles when reusing these resources:

- Treat permissions, privacy, model state, and failure recovery as first-class product flows.
- Keep system status and agent activity inspectable without overwhelming the primary task.
- Use clear empty, loading, degraded, and recovery states alongside the successful path.
- Reuse the Stitch-generated HTML as the visual reference and the PNG as the layout snapshot.

## Notes

Some Stitch records may be non-rendered asset screens or may expose only one downloadable resource. Those are retained when returned by the Dougie project, while the categorization focuses on functional pages.
