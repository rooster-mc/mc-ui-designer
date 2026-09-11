---
name: Double-chest grouping
status: todo
parent: MVP
depends-on: [030]
reviewers: [tester, correctness]
---

## Goal
Merge the two halves of a double chest into a single design entity with 6 rows;
single chests stay 3 rows.

## Scope
- `DoubleChestGrouper`: receives the `Region` alongside the `List<ChestContent>`
  (one entry per chest block), returns merged entries.
- Detect doubles via Bukkit's `DoubleChest` holder (or block-data
  `LEFT`/`RIGHT` + facing); reach each block via
  `region.world.getBlockAt(position.x, position.y, position.z)`, group both
  halves and read the shared inventory once.
- Deterministic canonical position for the merged entry.
- Singles pass through with 3 rows.

## Acceptance criteria
- Unit tests (MockBukkit) cover: single chest, double chest with both halves
  present, each orientation (`N`/`S`/`E`/`W`), and a selection containing only
  one half.
- A double chest yields exactly one entry with 6 rows and all 54 slots.
- No chest is emitted twice.

## Out of scope
- Chest naming (next ticket).

## Notes
- Prefer `Chest.getInventory().holder as? DoubleChest` to reach both sides,
  looking each block up through the injected `Region`
  (`region.world.getBlockAt(position.x, position.y, position.z)`); fall back to
  block-data geometry if needed. Document which was used.
- "Only one half selected" is ambiguous; decide (treat as single, or error) and
  document the choice in the ticket's review notes.
