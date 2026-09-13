# tester review — 150 (Require unique chest names for export)

## Round 1

### Verdict
The acceptance criteria are covered end-to-end: the pure validator, the
`save` outcomes, the removal of `clear`, and the non-null JSON contract all
have targeted tests at the right layer. One stated ordering requirement from the
ticket's Scope is not pinned by any test. Ship with that added.

### Findings

#### 1. Validation-before-output-path-resolution is not pinned
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommandTest.kt:122-178` (unnamed/duplicate `save` tests) and `:400-433` (config-throwing tests)
- Problem: The ticket's Scope says validation must run "after grouping and
  before resolving or writing the output path." The unnamed/duplicate tests all
  supply a *valid* config (`unregisteredCommand` at `:753-764`), so they prove
  validation runs before `exporter`/writing, but not before
  `configProvider().outputFile` (`UiDesignerCommand.kt:114-119`). The
  config-throwing tests all use a single named chest, so they never combine a
  name failure with an unusable config. A regression that moved config
  resolution ahead of `validateForExport` (surfacing `InvalidOutputFile` instead
  of `UnnamedChests`/`DuplicateNames`) would pass the whole suite.
- Suggested fix: Add one `save` test with an unnamed chest and
  `configProvider = { throw IllegalStateException("config should not be read") }`,
  asserting `SaveOutcome.UnnamedChests(listOf(BlockPos(2, 0, 0)))`; mirror it
  with two case-insensitively equal names asserting `DuplicateNames`. Using a
  throwing provider is stronger than a counter because it proves resolution is
  never attempted.

### Non-findings
- **Pure validator is genuinely pure and well covered.**
  `ExportValidationTest.kt:11-106` exercises blank/whitespace unnamed detection,
  case-only and whitespace-only duplicates, distinct names, and multiple
  duplicate groups. It constructs `UiChest` directly with no Bukkit import, so
  it tests the right layer for the `export` seam.
- **Acceptance criteria are all directly asserted.** Unnamed → no file
  (`UiDesignerCommandTest.kt:122-145`), case/whitespace duplicate → no file and
  both positions (`:147-178`), all-named unique → every name (`:88-120`),
  `/chest-edit clear` stored as an ordinary name (`ChestEditCommandTest.kt:46-55`,
  `:112-123`), blank input shows usage and leaves the name (`:72-88`), and the
  exported file re-parses with a non-null name (`:351-382`, `UiChestTest.kt:47-52`).
- **JSON contract is tested on both sides.** `UiChestTest.kt:37-45` proves the
  empty name is still serialized, `:47-52` proves a missing `name` fails to
  decode, and `JsonExporterTest.kt:94-108` proves the exporter no longer strips
  blank chest names while `:110-131` proves blank *slot* names are still
  stripped — the two behaviours that could be conflated.
- **The double-chest name-reading harness gap is already covered.**
  `save counts a double chest as a single design`
  (`UiDesignerCommandTest.kt:180-214`) names only the canonical half because
  MockBukkit does not form a real `DoubleChest`. The holder contract itself is
  exercised by `ChestNamerTest.kt:119-143` with a reflective
  `DoubleChest`/`DoubleChestInventory` proxy, and MT-003/MT-011
  (`docs/manual-test.md:14,22`) gate the real server. No new manual entry is
  needed on this path.
- **Manual gate is present for the live-selection paths.** MT-012
  (`docs/manual-test.md:23`) covers unnamed and duplicate failures on a live
  FAWE selection plus `clear`-as-name and blank-argument usage, and MT-009
  (`:20`) covers "clear is no longer suggested" — matching the MockBukkit
  limits. The commit updated both, so the non-automatable criteria are tracked.
- **No excessive or brittle tests found worth removing.** The dispatch-level
  message tests (`UiDesignerCommandTest.kt:522-563`) overlap `MessagesTest`
  wording assertions but pin the outcome→message wiring, which the wording tests
  cannot; the `ExportValidationTest` group-order assertions pin deterministic
  message output rather than incidental structure. I deliberately did not ask
  for a test of unnamed-vs-duplicate *precedence* when both are present
  (`UiDesignerCommand.kt:107-113`): the ticket does not specify which error
  wins, so a test would pin an unspecified product choice.
