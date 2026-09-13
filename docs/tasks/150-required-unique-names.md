---
name: Require unique chest names for export
status: done
parent: Import
depends-on: []
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
Make the chest name a real identity: `save` fails closed unless every grouped
chest has a non-blank, case-insensitively unique name, and drop `/chest-edit
clear` so no chest can be made permanently unsavable.

## Background
- `UiChest.name` is nullable and `JsonExporter.orderedChests` silently strips
  blanks (`export/JsonExporter.kt:38`), so an unnamed chest exports as an entry
  with no key.
- `UiDesignerCommand.save` fills names from `ChestNamer.nameOf`
  (`commands/UiDesignerCommand.kt:91-96`) and never checks them.
- `/chest-edit clear` and the greedy-arg reserved-word handling exist only to
  blank a name.

## Scope
- Change `UiChest.name` to a non-null `String`.
- Add a pure validator in `export/` (no Bukkit), e.g.
  `validateForExport(chests): ExportValidation`, returning unnamed positions and
  duplicate groups. Trim before comparing; compare case-insensitively; keep the
  original casing. Validate the grouped chests (after `DoubleChestGrouper`).
- `UiDesignerCommand.save`: run validation after grouping and before resolving
  or writing the output path. New `SaveOutcome` cases (e.g. `UnnamedChests`,
  `DuplicateNames`) with error-coloured messages listing positions; never call
  the exporter on failure.
- `JsonExporter`: stop stripping blank chest names; `orderedChests` no longer
  null-trims `name`.
- `/chest-edit`: remove `clear` (both the literal and the reserved-word
  handling). Blank/whitespace input prints usage and leaves the chest unchanged;
  `clear` becomes an ordinary name. Update the command's usage/help text.
- Update `docs/data-format.md` (name rule, Kotlin model), `docs/design.md`,
  `docs/architecture.md` (validator seam, `ChestEditCommand`), and
  `docs/manual-test.md`.

## Acceptance criteria
- `just build`, `just test`, `just format` pass.
- `save` on a selection containing an unnamed chest writes no file and reports
  the chest position(s).
- `save` with two names differing only by case/whitespace writes no file and
  reports the name and both positions.
- `save` on all-named unique chests writes every `name`.
- `/chest-edit clear` sets the name to "clear"; `/chest-edit` with blank input
  shows usage and does not clear.
- An exported file re-parses with `UiChest.name` non-null.

## Out of scope
- Scaffold/sync (160, 170).
- Any other `/chest-edit` semantics.

## Notes
- Name is the join key for 160/170; this is their prerequisite.
- Unnamed means trims-to-empty; duplicates are detected after trim and
  `lowercase()`.
