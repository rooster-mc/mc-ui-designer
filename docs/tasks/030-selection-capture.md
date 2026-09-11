---
name: FAWE selection capture and chest scan
status: todo
parent: MVP
depends-on: [020]
reviewers: [tester, correctness, architecture]
---

## Goal
Turn the player's FAWE selection into a list of chest blocks and their
inventories, with no double-chest merging yet.

## Scope
- `SelectionSource` interface: `fun selectionOf(player: Player): Region?`
  (region = min/max corners + world), plus a `FaweSelectionSource`
  implementation reading the FAWE session selection.
- `ChestScanner`: given a region, iterate blocks, keep `CHEST` and
  `TRAPPED_CHEST`, and read each inventory into a capture-side `ChestContent`
  (position + `ItemStack` list).
- `ChestContent` (capture-side model, Bukkit types allowed here) maps to the
  serializable model separately.
- Friendly failure when the player has no selection.

## Acceptance criteria
- Unit tests with a fake `SelectionSource` cover: no selection, selection with
  no chests, selection with several single chests.
- Only chest blocks are captured; other containers/blocks ignored.
- Contents (material + custom name) are read correctly for a chest with items.
- Iteration order is deterministic (by `x`, `y`, `z`).

## Out of scope
- Double-chest merging (next ticket).
- Item NBT/amount beyond what `docs/data-format.md` needs.

## Notes
- Keep FAWE out of `ChestScanner`; it should take a plain region. This is the
  main testability seam.
- Large selections: a full block iteration is acceptable for MVP, but note the
  cost and prefer a chest mask/iterator if FAWE offers one.
- FAWE API: `WorldEdit.getInstance().sessionManager` / `LocalSession.selection`.
  Verify exact API against the FAWE version resolved by the BOM.
