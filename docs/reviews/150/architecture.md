# Architecture review — 150 (Require unique chest names for export)

## Round 1
### Verdict
Ship with one doc fix. The validator seam is placed correctly (pure `export`,
called by `UiDesignerCommand` before the output path is resolved), the command
stays thin, and `docs/architecture.md` matches the code. `docs/data-format.md`
and `docs/design.md` still attribute the name gate to `JsonExporter`, which does
not enforce it.

### Findings
#### 1. data-format/design docs place the name gate in the exporter; the live seam is `UiDesignerCommand` + `validateForExport`
- Location: `docs/data-format.md:75-77` (`name` is required; **the exporter**
  validates ... before writing and fails closed), echoed at
  `docs/design.md:125-130` ("The exporter fails closed and writes no file").
- Problem: `JsonExporter` does not validate anything. `toJson`/`export`
  (`src/main/kotlin/dev/cypdashuhn/uidesigner/export/JsonExporter.kt:16-46`) only
  orders and serializes; the gate is `UiDesignerCommand.save`
  (`commands/UiDesignerCommand.kt:107-113`) calling `validateForExport` and
  returning before `exporter(...)`. The tests already document this: a blank
  name still serializes on its own
  (`JsonExporterTest`, "a blank chest name is preserved as an empty name"). So
  data-format.md now contradicts `docs/architecture.md:143-150`, which correctly
  attributes validation to `UiDesignerCommand`, and a reader could reasonably
  trust the schema doc and call `JsonExporter.export` directly, bypassing every
  invariant the ticket adds.
- Suggested fix: In `docs/data-format.md`, say the *save command* rejects the
  file via `validateForExport` before the exporter is invoked, and reserve "the
  exporter" for what `JsonExporter` actually owns (ordering, slot-name
  blank-stripping). Same one-line correction in `docs/design.md` ("The save path
  fails closed before writing").

### Non-findings
- **`ExportValidation.kt` package/purity.** It sits in `export/`, imports only
  `dev.rooster.region.BlockPos`, and has no Bukkit dependency, so the
  "`export` is pure Kotlin" seam holds. A separate file (rather than a method on
  `JsonExporter`) is the right split: validation is a distinct concept the
  command composes.
- **`requiredPosition()` moved to `UiChest.kt` (`UiChest.kt:22-23`).** It is now
  `internal` and shared by `JsonExporter` and `ExportValidation` instead of being
  duplicated; that removes drift and is a better home than `JsonExporter`.
- **`SaveOutcome.DuplicateNames` carrying `List<DuplicateNameGroup>`.** The
  command already depends on `export` (`UiChest`, `JsonExporter`), and the
  message bodies stay in the command file per the established convention
  (`architecture.md:168-169`), so the boundary is unchanged.
- **`architecture.md` seam text matches the code.** Validation runs after naming
  and grouping, before `configProvider().outputFile` is resolved (line 107 vs
  114), no exporter call on failure, grouper emits `name = ""`, `JsonExporter`
  no longer null-trims chest names while slot-name stripping is intact, and the
  data-flow diagram (lines 200-207) reflects the extra gate. The package listing
  now names `ExportValidation.kt` and drops the `clear` literal from
  `ChestEditCommand.kt:35`.
- **Extendability to 160/170 without a rewrite.** `UiChest.name` becoming
  non-null lets the importer decode the file directly; the scaffold path supplies
  names from the file and carries `position = null` (`@Transient`), and does not
  need `validateForExport`, whose `requiredPosition()` contract is
  export-specific. Duplicate handling on import stays a drift case rather than a
  shared validator, which is acceptable because the file is exporter-produced.
  New container types (barrels/shulkers) never touch this seam.
- **`ExportValidation.isValid` is only read by tests**
  (`ExportValidationTest`), while `save` branches on `unnamed`/`duplicates`
  separately. It is a harmless result-object convenience and not worth removing
  or routing through.
- **No other living doc is stale.** Design/data-format were updated on the
  ticket branch (`906856d`), and `TODO-QUEUE.md` has no remaining `clear` /
  unnamed-name reference. The older tickets (020, 050, 100, 130) that still
  describe nullable names or `/chest-edit clear` are historical records, not
  docs to keep current. `manual-test.md` MT-007/MT-009/MT-012 are consistent with
  the removed sentinel.

## Round 2
### Verdict
Ship. My round-1 finding is fixed in `507ac95`: `docs/data-format.md` and
`docs/design.md` now attribute the name gate to the save path / `validateForExport`
and state that `JsonExporter` only orders and strips blank slot names, matching
`docs/architecture.md`. The `InvalidNames`/`NamedPosition` refactor keeps the
seams sound and leaves the architecture docs accurate. No new findings.

### Findings
None.

### Non-findings
- **`validateForExport` seam unchanged by the refactor.**
  `ExportValidation.kt` remains in the pure `export` package (only
  `dev.rooster.region.BlockPos` imported), still returns
  `ExportValidation(unnamed, duplicates)`, and is still invoked by
  `UiDesignerCommand.save` after naming/grouping and before `configProvider()`
  (`commands/UiDesignerCommand.kt:104-107`). The gate and its position relative
  to the output path is untouched.
- **`DuplicateNameGroup(entries)` / `NamedPosition` are the right shape.**
  Replacing `name + positions` with `entries: List<NamedPosition>` removes the
  "one representative spelling" lie that `group.first().name` carried and lets
  the message print each colliding spelling with its own position
  (`UiDesignerCommand.kt:232-240`). A one-field wrapper plus a name+position pair
  is a small, justified type: it is consumed by both the outcome and the message
  builder and is not a premature generalisation. The docs describe exactly this
  (`docs/architecture.md:143-152`).
- **`ExportValidation.isValid` now earns its keep.** My round-1 note that it was
  only read by tests no longer applies: `save` now branches on
  `if (!validation.isValid)` (`UiDesignerCommand.kt:105`), and the single
  `InvalidNames(validation.unnamed, validation.duplicates)` outcome carries both
  lists. The old two-outcome precedence is gone, so the combined-message fix and
  the abstraction reinforce each other rather than duplicating a decision.
- **No doc staleness from the rename.** `architecture.md` no longer names
  `UnnamedChests`/`DuplicateNames` anywhere (package listing, seam bullet,
  integration bullet, and data-flow diagram all say `InvalidNames`), and the
  validator bullet names `NamedPosition`. `data-format.md:75-78` and
  `design.md:125-131` are consistent with the code; the only surviving
  `UnnamedChests`/`DuplicateNames` strings live in historical ticket text
  (`docs/tasks/150-...md`) and round-1 review reports, not living docs.
- **The other round-1 fixes are architecture-neutral.** The added
  config-throwing tests (tester), the `saveUnnamed`/`saveDuplicate` message keys
  and `JsonExporterTest` rename (readability), and the combined
  `invalidNamesMessage` with the case/whitespace explanation and per-spelling
  list (ux) touch tests and command message bodies only; no package, seam, or
  extendability change beyond what is covered above. Extendability to 160/170 is
  unaffected: the outcome stays internal to `UiDesignerCommand`, and the import
  path still never needs `validateForExport`.
