# correctness review — 150 (Require unique chest names for export)

## Round 1

### Verdict

Ship. The non-blank / case-insensitively-unique name gate is placed and
implemented correctly, the exporter no longer drops chest names, and the
`/chest-edit clear` removal behaves as the acceptance criteria require. I found
no logic, state, data or integration defect in the change.

### Findings

None. Traced the full path (`/uidesigner save` → `ChestCapture` →
`DoubleChestGrouper.group` → name population → `validateForExport` →
`JsonExporter`) and every acceptance criterion; nothing failed.

### Non-findings

- **Validation runs before output resolution/writing and blocks the exporter.**
  `UiDesignerCommand.save` builds the named list (`commands/UiDesignerCommand.kt:101-106`),
  validates it (`:107`), and returns `UnnamedChests`/`DuplicateNames`
  (`:108-113`) *before* `configProvider().outputFile` (`:114-119`) and the
  `exporter(...)` call (`:120-125`). The output path is never resolved and no
  file is touched on failure, so no partial/overwritten export.
- **Trim/case semantics match the ticket and `docs/data-format.md`.** Unnamed is
  `name.isBlank()` and duplicates group by `name.trim().lowercase()` while the
  group keeps `first().name` (`export/ExportValidation.kt:17-31`). Kotlin's
  `lowercase()`/`isBlank()`/`trim()` are locale-independent and use the same
  whitespace notion, so there is no locale or trim-inconsistency split.
- **Double chests.** `DoubleChestGrouper` consumes both halves and emits one
  `UiChest` at the canonical (min) position (`capture/DoubleChestGrouper.kt:35-58`,
  `:60-61`); `nameAt` reads via `ChestNamer.nameOf`, which for a linked double
  checks both halves through the `DoubleChest` holder (`naming/ChestNamer.kt:32`,
  `:43-53`). A name on either half is therefore found, and clipped doubles are
  rejected before naming (`commands/UiDesignerCommand.kt:100`), so an
  unselected half is never read. The known limitation for geometry-merged
  doubles with no holder (name read from the canonical half only) is explicitly
  deferred in `docs/design.md:113-115` and is not introduced or worsened here.
- **Unnamed/duplicate messages.** `unnamedChestsMessage` pluralises correctly and
  lists every position (`commands/UiDesignerCommand.kt:222-231`);
  `duplicateNamesMessage` lists each group's name and all positions (`:233-242`).
  Both use `Messages.errorColor` (red). `SaveOutcome` is exhaustive in
  `saveMessage` (`:144-154`).
- **`UiChest.name` non-null and round-trip.** `export/UiChest.kt:14-20` declares
  `String` with no default, so with `DesignJson.encodeDefaults = false` the key
  is always emitted and a legacy entry without a name fails to decode. JSON
  field names/order and the slot-name omission rule are unchanged and still
  match `docs/data-format.md` (`export/JsonExporter.kt:36-51`).
- **`/chest-edit`.** Only the optional greedy `name` node remains
  (`commands/ChestEditCommand.kt:40-44`); `clear` is no longer a literal or a
  reserved value, so it is stored as an ordinary name. Blank input returns
  `BlankName` before `ChestNamer.setName` (`:56-63`), leaving any existing name
  untouched, and maps to usage (`:51`). Usage/help text updated
  (`commands/ChestEditCommand.kt:66-70`, `commands/UiDesignerCommand.kt:165-170`).
  `ChestNamer.setName`'s blank→clear branch is now unreachable from the command,
  so no command path can blank a name.
- **`JsonExporter` no longer enforcing the invariant itself is intentional.**
  `toJson`/`export` will serialize a `UiChest` with a blank name if called
  directly (`export/JsonExporter.kt:15-43`), but the ticket puts the gate in
  `save` and `docs/architecture.md` documents that split. The documented entry
  point cannot produce such an input. `docs/data-format.md`'s loose phrase "the
  exporter validates" overstates where the check lives; that wording belongs to
  the architecture/doc scope, not a behavioural bug here.
- **Threading / IO.** Names are read from blocks inside the command executor
  (main thread); `JsonExporter` still writes a temp file and atomically moves it,
  so the validation change adds no new IO failure or partial-write window.
- **Empty inventories / air slots / unnamed items** are untouched by this commit
  and remain handled by the existing scanner/exporter filters.
