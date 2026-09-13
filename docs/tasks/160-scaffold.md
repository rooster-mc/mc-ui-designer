---
name: Import a design file as named chest scaffolds
status: todo
parent: Import
depends-on: [150]
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
`/uidesigner scaffold [file]` reads a design JSON and materializes named, empty
chests (correct `rows`, real linked doubles) at an anchor, so a layout can be
inspected in-world and filled later by `sync`.

## Background
- `DesignJson` and the `UiChest` model live in `export/`; deserialization is
  pure Kotlin and can reuse them.
- The format carries no positions, so scaffold owns the physical layout and the
  world is the layout authority.
- `ChestNamer.setName` names both halves of a double; a 6-row entry must become a
  real linked double.

## Scope
- Pure reader in `export/` (e.g. `JsonImporter`) that reads a file into
  `List<UiChest>` and validates the schema: `rows` in 1..6, slot ranges, item
  ids resolve to `Material`, and names are non-blank and unique. Errors name the
  file and offending entry. Default argument is the configured `output-file`.
- New `place/` package (Bukkit-coupled, thin): a placer that takes the imported
  chests plus an anchor and facing and places chest blocks. A `rows == 6` entry
  becomes two adjacent blocks linked `LEFT`/`RIGHT`; every chest faces the
  player. Place empty, then name via `ChestNamer`.
- Layout: a single row across the player's view at the anchor's Y. Anchor is the
  targeted block, falling back to the block in front of the player's feet.
- Fail closed: pre-check every target position; if any is not replaceable, or
  the player occupies one, place nothing and report the count plus the first
  obstruction.
- `/uidesigner scaffold [file]` wired in `UiDesignerCommand` with a
  `ScaffoldOutcome` set (placed, no world/replaceable target, parse failure,
  obstruction, IO failure). Add to help and tab completion (subcommands and
  `.json` files in the data folder).
- Update `docs/design.md`, `docs/architecture.md` (package tree, seams), and
  `docs/manual-test.md`.

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- Manual dev-server: scaffold places empty, named chests including one real
  6-row double; opening each shows the right size and name.
- `save` immediately after `scaffold` reproduces the same names and rows with
  empty contents, proving the round trip.
- Obstruction aborts atomically: no chests placed, message names the first
  blocked position.
- A malformed file (bad `rows`, blank/duplicate name) places nothing and reports
  the offending entry.
- A missing file or bad path gives a clear error.

## Out of scope
- Writing contents (`sync`, 170).
- Grid/wrap layouts, undo, and `force` overwrite of non-replaceable blocks.
- Positions or rotation stored in the JSON.

## Notes
- Keep the reader pure and the placer thin/testable. MockBukkit cannot form real
  doubles, so double pairing needs a manual-test entry.
- `scaffold` is not idempotent yet; re-running places a fresh row. Repair is
  deferred.
