---
name: Reconcile chest contents from a design file
status: todo
parent: Import
depends-on: [150, 160]
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
`/uidesigner status [file]` reports how a placed copy drifts from the file, and
`/uidesigner sync [file]` applies the file's contents to the selected named
chests, reporting the same drift.

## Background
- Names (150) are the join key; `scaffold` (160) creates the world side.
- The file is desired state and the world observed state; orphans are reported,
  never deleted.
- World chests are found through the existing `ChestCapture` →
  `DoubleChestGrouper` path over a FAWE selection.

## Scope
- Pure reconcile engine (no Bukkit) that joins the world's `List<UiChest>` to
  file entries by trimmed, case-insensitive name and classifies each:
  `InSync`, `Updated`, `Missing` (file-only), `Orphan` (world-only), and
  `Unjoinable` (unnamed/duplicate world chests). `InSync`/`Updated` compare
  normalized contents (material plus item name, by row/slot).
- `status`: selection-scoped report only, writes nothing.
- `sync`: the same report, then writes file contents into each matched chest
  (inventory items and item custom names, plus the chest name). Chests outside
  the selection and orphans are untouched.
- Structure drift between file and world (`rows` or name mismatch) is reported,
  not auto-fixed.
- `/uidesigner status [file]` and `/uidesigner sync [file]` wired in
  `UiDesignerCommand`; the default argument is the configured `output-file`. Add
  both to help and tab completion.
- Update `docs/design.md`, `docs/architecture.md`, and `docs/manual-test.md`.

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- Manual dev-server: after `scaffold`, `status` reports every chest in-sync;
  editing one world chest then `status` reports it as differing, `sync` restores
  the file contents, and a re-`status` is in-sync again.
- A file entry with no world chest is reported `missing`; a named world chest
  absent from the file is reported `orphan` and never deleted.
- Unnamed or duplicate world chests are listed as unjoinable, not matched.
- `status` never mutates the world.

## Out of scope
- Auto-repairing structure (missing chests, wrong rows), deletions, undo.
- Writing world to file (`save` already does that).

## Notes
- Keep the direction explicit: `save` means world wins, `sync` means file wins.
  That is what makes the loop predictable.
