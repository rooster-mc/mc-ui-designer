# readability review — 150 (Require unique chest names for export)

## Round 1

### Verdict

Ship with two naming fixes: two entries in `MessagesTest.allMessages()` label
`save` messages with a `chestEdit` prefix, and one `JsonExporterTest` name claims
"empty" while the test pins whitespace verbatim. The changed source and the new
validator otherwise read cleanly and are ktlint-shaped.

### Findings

#### 1. `allMessages()` labels the new `save` messages as `chest-edit` messages

- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/util/MessagesTest.kt:213-217`
- Problem: `"chestEditUnnamed" to unnamedChestsMessage(...)` and
  `"chestEditDuplicate" to duplicateNamesMessage(...)` label messages that are
  produced by `UiDesignerCommand.save` (`SaveOutcome.UnnamedChests` /
  `SaveOutcome.DuplicateNames`). Both functions live in `commands/UiDesignerCommand.kt`,
  not `ChestEditCommand.kt`. The sibling keys `chestEditNamed`, `chestEditNoTarget`,
  `chestEditNotAChest`, and `chestEditUsage` really are `chest-edit` messages, so the
  prefix is a meaningful signal that is now wrong for two entries. The keys are used
  in assertion failure messages, so a future failure will point at the wrong command.
- Suggested fix: rename to `saveUnnamed` / `saveDuplicate` (fitting `saveSuccess`),
  and leave the `chestEdit*` keys to actual chest-edit messages.

#### 2. `JsonExporterTest` name says "empty name" but the test asserts whitespace is kept

- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporterTest.kt:95-107`
- Problem: the test named `a blank chest name is preserved as an empty name`
  constructs a chest with `name = "   "` and asserts `decoded.single().name == "   "`.
  Nothing in the test is empty; the invariant being pinned is that a blank/whitespace
  name round-trips verbatim instead of being stripped to `null`. A reader scanning
  test names is told the wrong invariant.
- Suggested fix: rename, e.g. `a blank chest name is preserved verbatim` (or
  `whitespace in a chest name is preserved`).

### Non-findings

- The local `val named` in `UiDesignerCommand.save`
  (`UiDesignerCommand.kt:101-106`) holds chests that may still be blank before
  `validateForExport` runs, which reads slightly against the following `validation.unnamed`
  check. I left it: "named" is the term `docs/architecture.md` uses for this stage
  ("the named, grouped chests"), so renaming would fight the established wording.
- `ChestNamer.clear` (`ChestNamer.kt:28-30,39-41`) and the blank branch of
  `ChestNamer.setName` have no production caller left after `/chest-edit clear` was
  dropped; only `ChestNamerTest` reaches them. It is a general public utility outside
  this ticket's stated scope, so I did not ask for removal here, but a follow-up
  cleanup could delete them.
- `ExportValidation.isValid` (`ExportValidation.kt:14`) is exercised only by tests,
  while `save` branches on `unnamed`/`duplicates` so it can pick the right outcome.
  That is a harmless derived convenience, not a duplicate of the production decision.
- `UiChest.requiredPosition()` moving from `JsonExporter` to `UiChest.kt`
  (`UiChest.kt:22-23`) removes a private copy and gives the validator the same
  accessor; the `internal` visibility is right for one module.
- `ExportValidation.kt` is one small, single-purpose file: two result types plus the
  pure function, with the unnamed and duplicate passes each readable top-to-bottom.
- `unnamedChestsMessage` / `duplicateNamesMessage` (`UiDesignerCommand.kt:222-242`)
  sit with the other message builders next to the shared private `coords()` helper,
  and the nested `joinToString` in `duplicateNamesMessage` is easy to follow.
- No comments were added anywhere under `src/`; the only comments in touched files
  (`ChestEditCommand.kt:15`, `JsonExporter.kt:64`) are pre-existing *why* comments.
- No changed line exceeds 100 columns, and wrapping, trailing commas, and import
  order match the surrounding ktlint style.
