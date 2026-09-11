---
name: "/uidesigner save export command"
status: todo
parent: MVP
depends-on: [010, 020, 030, 040, 050]
reviewers: [tester, correctness, architecture, readability, ux]
---

## Goal
`/uidesigner save` captures the FAWE selection, groups chests, and writes the
JSON to the configured output path.

## Scope
- `UiDesignerCommand` with subcommands `save`, `reload`, `help` (CommandAPI).
- `save` pipeline: `SelectionSource` → `ChestCapture` (region + contents) →
  `DoubleChestGrouper(region, contents)` → `JsonExporter` → `config.outputFile`.
- Feedback: number of chests exported and the written path; errors for no
  selection, empty selection, and IO failure.
- Permission node for `save` (e.g. `uidesigner.save`), default op.
- Runs on the main thread for block access; file IO must not block dangerously
  long (acceptable for MVP, note if moved async).

## Acceptance criteria
- Manual end-to-end on the dev server: place singles + a double, fill them,
  name one, select with FAWE, `/uidesigner save`, and the file matches
  `docs/data-format.md`.
- Output overwrites atomically; a failed write leaves the previous file intact.
- Command unit/integration tests with fakes for the happy path and each error.
- `/uidesigner reload` re-reads config.

## Out of scope
- Import/apply.
- Progress reporting for very large selections.

## Notes
- This is the integration point; keep orchestration thin and delegate logic to
  the packages in `docs/architecture.md`.
