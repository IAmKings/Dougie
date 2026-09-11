# Design: 对话 SKU 随发版更新

## States

Per chat offer, derive from dest + extraRoot `models/chat/`:

| State | Meaning | Settings actions |
|---|---|---|
| missing | no file for this SKU | 下载 |
| current | official filename present and SHA matches catalog | 使用 / 使用中 |
| stale | SKU present via old name / heuristic, or official file SHA mismatch | 更新 + 使用 / 使用中 |

`request()` must run for `stale`, not only `missing`. `installed` stays true for stale so probe/使用 still work.

SHA of a 1.5GB file is not computed on the Compose main path. Filename mismatch ⇒ stale without hashing. Official name present ⇒ hash off-main (or on first settings collect) keyed by path + length + mtime; until hash returns, treat as current if name matches (avoid flashing 更新), then flip to stale if SHA differs. Do not log path or digest.

## Load path

`ChatModelLayout.locate` already prefers official filename then `guessSku`. Keep that for stale (old name). After the new file lands, official name wins. Close/recreate LiteRT engine when the resolved **path** changes, not only when SKU id changes.

## Download / cleanup

HTTPS install + tree write + import as today, targeting the **new** official filename. Same-name SHA bump: overwrite that file (`copyInto` must not skip a stale official file).

After import SHA OK: delete other files in app-private chat dirs whose `guessSku(name, length)` (or previous catalog alias list) equals this SKU and whose name ≠ new official name. Do not delete siblings of other SKUs. Do not delete SAF tree files.

Optional `previousFileNames` on layout is unnecessary if guessSku still maps the old HF name; if the old name would not match (rare), keep a small `legacyNames(sku)` list in `ChatModelLayout` updated when catalog filenames change.

## Catalog

SKU ids stay. Maintainers bump `MODEL_FILE` / `MINICPMx_FILE`, DEFAULT SHA/URL (and 0.6B BuildConfig) in the App release. `matchesFileName` / size bands may need a tweak when HF names change; that is the same release as the catalog bump.

## Play

No chat rows; no update UI. Leak check unchanged.
