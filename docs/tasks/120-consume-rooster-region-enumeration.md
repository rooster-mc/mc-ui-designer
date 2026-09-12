---
name: Consume rooster-region lazy block enumeration
status: todo
parent: Polish
depends-on: []
reviewers: [tester, correctness, architecture, readability]
---

## Goal
`rooster-region` ticket 060 (commit `4be035e`) added `Region.blockPositions` and
`Region.loadedBlockPositions`. Replace the hand-rolled triple loop in
`capture/ChestScanner.kt` with the library enumeration and delete the TODO.

## Scope
- `capture/ChestScanner.kt`: drop the `x`/`y`/`z` loops and the manual
  `isChunkLoaded(x shr 4, z shr 4)` guard; iterate
  `region.loadedBlockPositions`, map each `BlockPos` through `region.blockAt`,
  keep only chest/trapped-chest materials, and cast the block state to `Chest`
  to build `ChestContent`. Behaviour and result ordering may change only where
  the region-order is unobservable; the returned set of chests must be
  identical.
- Remove the `ChestScanner` TODO marker.
- Update `docs/architecture.md` if it names the scan loop.

## Out of scope
- Chest grouping (`DoubleChestGrouper`) and the `rooster-region` API itself
  (shipped in `4be035e`).

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- `ChestScanner.scan` returns the same chests as before for the same region
  (same positions, same cloned item contents).
- No TODO remains in `ChestScanner.kt`.

## Notes
- The library change is picked up through the existing composite build; no
  publish step.
