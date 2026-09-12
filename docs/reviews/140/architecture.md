# architecture review — 140 (Abort export on a double chest clipped by the selection)

## Round 1

### Verdict

Ship with fixes. The seam split is correct: `GroupResult`/`ClippedHalf` are
capture-side types carried out of `DoubleChestGrouper`, and the fail-closed
decision and wording live in `UiDesignerCommand` behind a new `SaveOutcome`
case, consistent with `NoSelection`/`NoChests`. The living docs were updated in
the same change. Two items: a third copy of the holder-half position
extraction, and one `docs/design.md` sentence the change left half-updated.

### Findings

#### 1. Holder-half position extraction now has a third copy

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:95-97`
  and `:156-157`; pre-existing copy at
  `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:47-48`.
- Problem: `partnerOf` and `halvesIn` both turn a `DoubleChest` holder into half
  positions with the identical expression
  `listOfNotNull(leftSide as? Chest, rightSide as? Chest).map { BlockPos(it.x, it.y, it.z) }`.
  `ChestNamer.chestsOf` already resolves the same pair a third time. Any change
  to how halves are read (a different null-side policy, a `holder.location`
  fallback, a position helper on the library) must land in three places; the two
  grouper copies are in the same file and can silently diverge while all tests
  stay green. This is the third consumer, which is the repo's own stated
  threshold for extracting (see the "Chest predicate duplication" seam note).
- Suggested fix: add a private `DoubleChest.halfPositions(): List<BlockPos>` in
  `DoubleChestGrouper.kt` and use it from both `halvesIn` and `partnerOf`.
  Leave `ChestNamer.chestsOf` as is for now rather than introduce a
  `naming -> capture` dependency for three lines; if a fourth copy appears, move
  the helper into `capture/` and make the direction change deliberately.

#### 2. `design.md` chest-names decision still reads as if the exporter keeps a lone loaded half

- Location: `docs/design.md:100`.
- Problem: the chest-names decision ends the unloaded-half sentence with
  "`/chest-edit` only names or clears the loaded half; 040 and the exporter
  likewise only see loaded halves." After 140 the two no longer behave
  "likewise": the exporter now aborts when a double's partner half is not
  captured. This is the last living-doc statement that ties the exporter's
  lone-half handling to 040's silent behavior, and read on its own it says the
  exporter tolerates a lone half — which the very next decision bullet
  contradicts. The change updated the bullet the ticket named but left this
  linked clause.
- Suggested fix: keep the `/chest-edit` clause, and reword the exporter half to
  defer to the clipped-double decision, e.g. "…`/chest-edit` only names or
  clears the loaded half; the exporter likewise only sees loaded halves and,
  since 140, fails closed when a double chest's other half is missing (see
  below)." One sentence, no behavior change.

### Non-findings

- **`GroupResult`/`ClippedHalf` placement.** Both live in `capture/`, carry
  only `BlockPos` (library type) and primitives, and name the captured
  position, the partner, and a bounds fact — no extra Bukkit surface. Keeping
  the human wording out of the model (`partnerInsideSelection` is a fact, not a
  message key) is the right side of the capture/command seam.
- **Fail-closed location.** `UiDesignerCommand.save` returns
  `SaveOutcome.ClippedChests` immediately after grouping, before config
  resolution and the exporter (`UiDesignerCommand.kt:89-90`). That matches the
  existing `NoSelection`/`NoChests` short-circuits and keeps the command the
  composition point, per `docs/architecture.md`; `SaveOutcome.ClippedChests` is
  purely additive and `saveMessage`'s `when` stays exhaustive. Message bodies
  remain internal top-level functions in the command file.
- **Export purity.** No file under `export/` changed; the new types are in
  `capture/` and the command only imports capture types. `JsonExporter`'s role
  as the single ordering authority is untouched.
- **Docs updated by the change.** `docs/architecture.md` replaces the old
  `Only one half selected (040)` seam bullet with the 140 bullet, updates the
  package-tree line (`DoubleChestGrouper.kt … reports clipped halves`) and the
  data-flow diagram (`GroupResult` + `ClippedChests` short-circuit). The
  `docs/manual-test.md` MT-011 entry names 140 and the unverifiable path. All
  three match the code; no other living doc (grep over `docs/` excluding
  `reviews/` and historical `tasks/`) still states the single-half-exports-a-3-row
  behavior. `docs/data-format.md` is unaffected — the schema and row rules are
  unchanged, and the new path writes no file at all.
- **`Region.containsBlock` shim.** The ticket said bounds are available, and
  they are, but `rooster-region` exposes only `Region.contains(Location)` and
  `contains(Region/Entity)` (`Region.kt:74-94`), no `BlockPos` overload, so the
  private inclusive-bounds extension at `DoubleChestGrouper.kt:210-211` is a
  reasonable local adaptation rather than a duplicate of an existing API. It
  reads bounds only, so it never forces a chunk load, which is what the seam
  bullet claims. No library change is warranted (explicitly out of scope).
- **Extendability.** The next features (more container types, import, other
  output formats) do not require reworking `GroupResult`: a new grouper can
  return its own result or a widened `GroupResult`, and the existing
  `chests`/`clipped` distinction maps cleanly onto additional result states.
  Nothing here is over-generalised for a need that does not exist.

## Round 2

### Verdict

Ship. Both round-1 findings are resolved as asked, and the copper extension the
implementor added (in response to correctness) is a clean widening of the same
predicate: it stays behind the `capture/`/`naming/` boundary, reuses Bukkit's
`Tag.COPPER_CHESTS` rather than hand-listing variants, and is reflected in the
living docs. No new architecture or doc-staleness findings.

### Findings

No new findings.

### Non-findings

- **Round-1 finding 1 (half-position duplication) — concur, resolved.**
  `DoubleChest.halfPositions()` is defined once at
  `DoubleChestGrouper.kt:158-160` and used by `partnerOf` (`:95`) and `halvesIn`
  (`:154`). The pre-existing third copy in `ChestNamer.chestsOf`
  (`ChestNamer.kt:47-48`) is left in place, which is what I recommended to avoid
  a `naming -> capture` dependency for three lines.
- **Round-1 finding 2 (`design.md` lone-half implication) — concur, resolved.**
  `docs/design.md:98-101` now reads "the exporter likewise only sees loaded
  halves and, since 140, fails closed when a double chest's other half is
  missing (see below)", and the "One half selected" decision at `:110-115`
  states the abort. The sentence no longer reads as tolerance of a lone half.
- **Copper predicate is documented and stays in its layer.** The scanner check
  (`ChestScanner.kt:23-24`) and `ChestNamer.isChest`
  (`ChestNamer.kt:13-16`) use `Tag.COPPER_CHESTS`; both are in
  `capture/`/`naming/`, so no Bukkit leaks toward `export/`. `docs/design.md:116-118`
  (new chest-materials decision) and `docs/architecture.md:135-137,141-145` match
  the code, and the "eight variants" count is right (four oxidation levels,
  waxed and unwaxed).
- **Chest-predicate duplication remains a documented deferral, not new drift.**
  `docs/architecture.md:141-145` still records it as a deliberate carry-over
  "until a third consumer appears". Copper changed both copies in the same
  commit and they are semantically identical, so the deferral still holds; the
  change did not quietly add a third consumer. (When it is extracted, the tag
  membership, not the two-material set, is the part worth centralising.)
- **Doc sweep after the copper and wording changes.** No living doc (outside
  `docs/reviews/` and historical `docs/tasks/`) still claims the scanner accepts
  only `CHEST`/`TRAPPED_CHEST`, or that a clipped half exports as a 3-row chest.
  `docs/data-format.md` is unaffected: the schema and the 3-/6-row rule are
  material-independent, and the clipped path still writes no file.
  `docs/manual-test.md:22` MT-011 stays correct for copper because a copper
  double exercises the same block-state paths; it needs no copper-specific
  entry.
- **Seams unchanged and intact.** `GroupResult`/`ClippedHalf` stay in
  `capture/`; the fail-closed decision stays in `UiDesignerCommand` behind
  `SaveOutcome.ClippedChests`; `JsonExporter` remains the ordering authority. The
  data-flow diagram (`docs/architecture.md:188-194`) still matches
  `DoubleChestGrouper.group` returning `GroupResult`.
