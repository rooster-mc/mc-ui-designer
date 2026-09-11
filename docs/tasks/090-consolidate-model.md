---
name: Use rooster-region BlockPos and consolidate model
status: todo
parent: MVP
depends-on: ["080"]
reviewers: [tester, correctness, architecture, readability]
---

## Goal
Adopt the library's `BlockPos` and `Region.blockAt`, and remove the now-empty
`model` package by moving `UiChest` next to the exporter.

## Scope
- Replace `dev.cypdashuhn.uidesigner.model.BlockPos` with
  `dev.rooster.region.BlockPos`; delete `model/BlockPos.kt`.
- Move `UiChest.kt` (`UiChest`/`UiRow`/`UiSlot`/`DesignJson`) from `model/` into
  `export/`, next to `JsonExporter`; delete the `model` directory.
- Replace `capture/RegionExt.blockAt` with the library's `Region.blockAt`;
  delete `RegionExt.kt`.
- Simplify `FaweSelectionSource`: drop the cross-world guard now that the
  library's `worldEditSelection()` is world-scoped.
- Update `docs/architecture.md` for the package changes.

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- No `model` package remains; no local `BlockPos` and no local region block
  accessor.
- Capture, export and commands import `dev.rooster.region.BlockPos`.
- Behaviour is unchanged (capture/export/command tests still pass).

## Out of scope
- JSON format or command behaviour changes.
- Changes to `rooster-region`.

## Notes
- `UiChest` is the export payload, so it lives with `JsonExporter` in `export/`.
- Depends on rooster-region tickets 040 (`BlockPos`, `Region.blockAt`) and 050
  (world-scoped `worldEditSelection()`), both merged.
