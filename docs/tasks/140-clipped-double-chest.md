---
name: Abort export on a double chest clipped by the selection
status: todo
parent: Polish
depends-on: []
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
If a FAWE selection clips a double chest so only one half is captured, the
export currently emits a 3-row `UiChest` from the captured half and silently
drops the other half's items. Detect it and abort with an actionable error
instead of writing a corrupted design.

## Background
- `ChestScanner` only scans blocks inside the selection, so at most one half is
  captured.
- `DoubleChestGrouper.match` needs both halves (`halvesIn` requires 2, the
  geometry fallback needs `byPosition[partner] != null`), so a lone half falls
  through to the single-chest branch. Note `DoubleChestGrouperTest` currently
  pins this behavior in `only one half selected is treated as a single chest`.
- A `Chest` is knowably half a double when its block data `type` is `LEFT`/
  `RIGHT` (a lone chest is `SINGLE`), or when its `inventory.holder` is a
  `DoubleChest`.

## Scope
- Make `DoubleChestGrouper.group` report clipped halves instead of silently
  treating them as singles. Suggested shape: return a result carrying
  `chests: List<UiChest>` plus `clipped: List<ClippedHalf>`, where a
  `ClippedHalf` names the captured position, the partner position, and whether
  the partner lies inside the selection bounds.
  - Partner outside the selection → "outside your selection".
  - Partner inside the selection but not captured → it was in an unloaded chunk
    (`loadedBlockPositions` skipped it).
- `commands/UiDesignerCommand.kt`: add a `SaveOutcome` case for the clipped
  chest(s) and short-circuit **before** calling the exporter, so no file is
  written. Message (error colour) names the chest and partner and tells the
  user how to fix it, e.g. "Cannot export: the chest at (x, y, z) is half of a
  double chest whose other half at (x2, y2, z2) is outside your selection.
  Expand the selection to include both halves." Use a distinct wording for the
  unloaded-chunk case. If several are clipped, name the first and mention the
  rest.
- Keep single chests and fully-captured doubles exactly as today.
- Tests in `DoubleChestGrouperTest` (replace the "treated as a single chest"
  test; cover the holder path, the geometry path, both partner-location cases)
  and `UiDesignerCommandTest` (no export call, message content).

## Out of scope
- Auto-including the missing half (deliberately rejected: the selection is the
  source of truth).
- Any `rooster-region` change; `Region.contains`/bounds are available.

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- A selection containing exactly one half of a double chest writes no output
  file and reports the captured and partner blocks with a fix hint.
- A selection with both halves present still exports one 6-row chest.
- A selection with only single chests is unaffected.
- New manual entry in `docs/manual-test.md` for a real selection clipping a
  real double chest.

## Notes
- Chosen UX: fail closed. Silent truncation is the worst outcome for a tool
  whose job is to describe the design faithfully.
