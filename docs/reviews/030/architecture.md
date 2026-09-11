# Architecture review — 030 (FAWE selection capture and chest scan)

## Round 1

### Verdict
Ship with fixes. Package placement is exactly right: `Region`, `ChestContent`,
`SelectionSource`, `FaweSelectionSource` and `ChestScanner` all land in
`capture/`, `BlockPos` moves to `model/BlockPos.kt` with an unchanged import
path, and the `model`/`export` purity seam holds. It also finally resolves the
020 round-1 §4 carry-over (ChestContent out of `model`). The one substantive gap
is the scanner→grouper seam: `ChestContent` carries position + `ItemStack`s but
no way to reach the block, so 040's documented `DoubleChest`-holder route (and
060's `ChestNamer.nameOf`) cannot be implemented against the shape 030
produces. Since 040 lists no architecture reviewer, that contract should be
settled now.

### Issues

#### 1. `ChestContent` gives 040's grouper no way to reach the block (severity: medium) — fix now
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestContent.kt:6-9`,
  `docs/architecture.md:42-43,59-65`,
  `docs/tasks/040-double-chest-grouping.md:14,32`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:15`
- Problem: The pipeline is `Region → ChestScanner → List<ChestContent> →
  DoubleChestGrouper`. `ChestContent` exposes only `position: BlockPos` and
  `items: List<ItemStack?>`: no `World`/`Block`, no double-chest side/facing, and
  no chest name. 040's preferred detection is
  `Chest.getInventory().holder as? DoubleChest`, and it must be correct for all
  four orientations, which needs block data (a `BlockPos` alone cannot say which
  half is `LEFT`/`RIGHT`). Likewise 060 must eventually call
  `ChestNamer.nameOf(block)` to fill `UiChest.name`, which also needs a `Block`.
  As written, the grouper cannot reach a `Block`, so 040 must either change this
  interface or abandon the preferred route for pure geometry. On top of that,
  `architecture.md:42-43` asserts the scanner keeps "Bukkit types out of
  grouping", but `ChestContent.items` is already Bukkit `ItemStack` — so the
  "testable with fakes" grouping seam at `architecture.md:33-35` is not actually
  specified by the data shape.
- Suggested fix: Decide the grouper's input contract before 040 (it has no
  architecture reviewer). The option most consistent with the doc's "grouping is
  testable with fakes" is to have `ChestScanner` capture the extra facts 040
  needs — the chest's `Chest.Type`/facing (or side) and its plain name — so
  `ChestContent` is self-contained and the grouper stays pure and world-free.
  The smaller alternative is to pass the `Region` (or its `World`) into
  `DoubleChestGrouper` and into 060's mapping step, and update the data-flow
  diagram (`architecture.md:59-65`) to show it. Either is acceptable; leaving
  the seam unspecified means 040 reworks 030's output type, which is precisely
  what 030 exists to prevent.

#### 2. `architecture.md` leaves two inaccurate Seams statements (severity: low) — fix now
- Location: `docs/architecture.md:33`, `docs/architecture.md:42-43`
- Problem: (a) `architecture.md:42-43` "keeping Bukkit types out of grouping and
  export" is false for grouping, because `ChestContent.items: List<ItemStack?>`
  (`capture/ChestContent.kt:8`) is a Bukkit type; only export is truly
  Bukkit-free. (b) `architecture.md:33` says "`ChestScanner` takes a region and a
  `World`", but `ChestScanner.scan` takes only `Region`
  (`capture/ChestScanner.kt:10`), with the world nested inside it. Both read as
  if written before the final shape.
- Suggested fix: reword (a) to "keeping Bukkit block/inventory access out of
  grouping and all Bukkit types out of export", and (b) to "`ChestScanner` takes
  a `Region` (which carries its `World`)". The rest of the 030 edits
  (`architecture.md:38-41,44-48,59-65`) accurately describe the code.

#### 3. Two definitions of "chest" will drift (severity: low) — carry over
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:8`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:12-13`
- Problem: `CHEST_MATERIALS = setOf(CHEST, TRAPPED_CHEST)` duplicates
  `ChestNamer.isChest`. The stated reason (avoid a capture→naming dependency) is
  defensible and there is no cycle today, but it is a second source of truth for
  the same rule; when barrel/shulker support or a third consumer lands, one copy
  will be updated and the other missed.
- Suggested fix: Carry over until a third consumer appears, then extract one
  shared predicate (e.g. a small capture-level `ChestMaterials`/`isChest`) and
  have both `ChestScanner` and `ChestNamer` use it. Not worth doing in 030.

### Non-issues
- **`ChestContent` in `capture/` is correct and closes a prior carry-over.** It
  holds Bukkit `ItemStack`s (`ChestContent.kt:8`), so `model` stays pure
  (`docs/architecture.md:36-41`). This resolves 020 round-1 §4 rather than
  deferring it again.
- **`BlockPos` split to `model/BlockPos.kt` is justified, not over-generalised.**
  It has three real consumers (`UiChest.position`, `Region` corners,
  `ChestContent.position`), it is a pure value type, and the import path is
  unchanged, so nothing else moves. `Region`/`ChestContent` live in `capture/`
  and depend on `model`, so the direction is `capture → model`, no cycle.
- **`Region`'s private constructor + `Region.of` factory is the right shape.**
  Per-axis normalisation is a real invariant (tested in `RegionTest.kt`), and a
  non-data class with no `copy`/`componentN` prevents constructing a
  non-normalised region. No extra geometry API was invented; `min`/`max`/`world`
  is all 040 and 060 need.
- **No outcome/orchestration type is the right MVP call.** `selectionOf` returns
  `Region?` and `null` means "no usable selection"; 060 needs exactly three
  states (no selection, empty selection, IO failure) and gets them from
  `null` / empty list / exception. A sealed outcome hierarchy now would be
  speculative and would leak a command concern into `capture`.
- **`FaweSelectionSource` as an `object` does not hurt 060 testability.** The
  injectable seam is the `SelectionSource` interface, and 060's tests use fakes
  (as `ChestScannerTest` already does). The adapter itself is untestable without
  a live WorldEdit regardless, so a class instance would buy nothing. 060 should
  accept a `SelectionSource` parameter defaulting to `FaweSelectionSource`, not
  call the object from inside the command body.
- **Skipping unloaded chunks is consistent with the design doc.** `design.md:71-73`
  says a half in an unloaded chunk is invisible and "040 and the exporter
  likewise only see loaded halves"; `architecture.md:44-45` and
  `ChestScanner.kt:15` implement exactly that (no forced chunk load). This is a
  deliberate limitation, not a bug, and it is documented.
- **Reading `blockInventory` (27 slots) and cloning is the correct choice for
  040.** `ChestScanner.kt:22` snapshots each half independently via
  `chest.blockInventory.contents.map { it?.clone() }`, so a double chest's halves
  stay separate until the grouper merges them (`architecture.md:46-48`) and later
  inventory mutation cannot alter an already-captured result. The shared
  `chest.inventory` would have pre-merged them and made 040's job impossible.
- **Iteration order matches the canonical ordering.** The `x → y → z` nested
  loops (`ChestScanner.kt:12-14`) produce the same order as `BlockPos.compareTo`,
  so the "deterministic by x, y, z" acceptance criterion is met by construction
  and the scanner does not need its own sort.
- **No over-generalisation for the backlog.** `ChestContent` stays a two-field
  capture intermediate; `SelectionSource` is a one-method interface; no
  container/reader/format abstraction was added for barrels, shulkers or import.
  `Region` wraps `World` rather than inventing a world handle. This is
  appropriately small for the MVP.

## Round 2

### Verdict

Ship with fixes. The round-1 medium issue is *substantively* resolved: the
grouper input contract is now written down (`architecture.md:60-64`) and the
`Region`-reaches-blocks route is the one round 1 explicitly accepted, so 040 no
longer has to rework 030's output type. The duplicate-predicate carry-over
(round 1 §3) is documented (`architecture.md:65-67`), and the MockBukkit caveat
checks out against `ChestStateMock.getBlockInventory()` (verified in the 4.116.1
sources: it returns `getInventory()`). Two things are left: `ChestCapture` — the
newly declared "production entry point" — throws away the very `Region` the
grouper now needs, and one reworded Seams sentence is contradicted by the same
fix's grouper contract.

### Issues

#### 1. `ChestCapture` discards the `Region` that 060's grouper step needs (severity: medium) — fix now
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestCapture.kt:6-7`,
  `docs/architecture.md:36-38`, `docs/architecture.md:79-84`
- Problem: Round 1 settled the grouper contract as "`DoubleChestGrouper` receives
  the `Region` alongside the `List<ChestContent>`" (`architecture.md:60-64`), but
  `ChestCapture.capture` returns only `List<ChestContent>?` and has already
  consumed the `Region` internally. 060 therefore has two bad options: call
  `source.selectionOf(player)` a second time just to recover the region (two
  session lookups, two chances to disagree), or bypass `ChestCapture` entirely
  and compose `selectionOf` + `ChestScanner.scan` itself — in which case the
  thing the doc calls "the production entry point that glues the two"
  (`architecture.md:36-38`) is production-dead and exists only for
  `ChestScannerTest` (`ChestScannerTest.kt:43,53,147`). The data-flow diagram
  makes the gap visible: the `Region?` at `architecture.md:78` flows into
  `ChestCapture.capture` (`:79`) but the arrow into `DoubleChestGrouper` (`:82`)
  carries only `List<ChestContent>?`, so the region the grouper signature names
  is never shown reaching it.
- Suggested fix: pick one and make the doc/diagram match. Either (a) drop
  `ChestCapture` and let 060 do `val region = source.selectionOf(player) ?: …;
  val contents = ChestScanner.scan(region); DoubleChestGrouper(region, contents)`
  (the null/empty distinction is then 060's, which is where the user-facing error
  lives anyway), or (b) keep it but return both, e.g.
  `data class Capture(val region: Region, val contents: List<ChestContent>)` with
  `capture(source, player): Capture?`, so 060 gets the region for the grouper in
  one selection read. In both cases redraw `architecture.md:75-88` so `Region`
  visibly feeds `DoubleChestGrouper`, and update the 060 pipeline in
  `docs/tasks/060-export-command.md:15`.

#### 2. `architecture.md` still asserts grouping is free of Bukkit block/inventory access (severity: low) — fix now
- Location: `docs/architecture.md:49-51` vs `docs/architecture.md:60-64`
- Problem: The reworded Seams sentence says `ChestScanner` keeps "Bukkit
  block/inventory access out of grouping", but the grouper contract added in the
  same fix says `DoubleChestGrouper` "can reach each block via
  `region.world.getBlockAt(position)`" and detect the `DoubleChest` holder — that
  *is* Bukkit block/inventory access inside grouping. The two bullets cannot both
  be true. Round 1's point was that grouping cannot be Bukkit-free while
  `ChestContent.items` is `List<ItemStack?>`; choosing the `Region`-in-grouper
  route makes grouping more Bukkit-coupled, not less. The companion claim at
  `architecture.md:34-36` that "scanning and grouping are testable with fakes" is
  likewise now loose: grouping needs a real/`WorldMock`-backed `Region`, not a
  fake.
- Suggested fix: state the seam honestly — `ChestScanner` is the only place that
  *enumerates* blocks and reads inventories, grouping may do targeted block
  lookups through the `Region` (Bukkit-coupled by design, testable with
  MockBukkit), and only `export` is fully Bukkit-free. Drop the "fakes" wording
  for grouping or scope it to `SelectionSource`/`ChestScanner`.

#### 3. 040/060 tickets not updated to the new grouper contract (severity: low) — fix now
- Location: `docs/tasks/040-double-chest-grouping.md:14`,
  `docs/tasks/060-export-command.md:15`
- Problem: 040's scope still reads "`DoubleChestGrouper`: given `List<ChestContent>`
  (one entry per chest block)", with no `Region`, and 060's pipeline still reads
  "`SelectionSource` → `ChestScanner` → `DoubleChestGrouper` → `JsonExporter`",
  with no `ChestCapture`/`Region`. Architecture.md is now the authority for a
  different signature than the ticket that implements it. 040 has no architecture
  reviewer, so the ticket text is the only thing its implementor sees; leaving it
  stale invites the exact rework 030 is meant to prevent.
- Suggested fix: update 040's Scope/Notes to name the `Region` parameter and the
  `region.world.getBlockAt(position)` route; update 060's pipeline line once issue
  1's `ChestCapture` shape is decided. Docs should move in the same commit that
  invalidates them.

### Non-issues

- **The grouper contract itself is settled and adequate.** `Region` + contents is
  the option round 1 named as acceptable, it gives 040 what its preferred
  `Chest.getInventory().holder as? DoubleChest` route needs, and 060 can read
  names with `ChestNamer.nameOf(block)`. No further contract change is needed.
- **`ChestCapture` in `capture/` and as a stateless `object` is the right
  placement and kind.** The problem is its return shape (issue 1), not its
  location; a value-returning glue belongs in `capture/` beside the pieces it
  composes.
- **The `Region` cuboid-only note is accurate** (`architecture.md:39-42`) and
  matches `docs/design.md:57-59`; it is a deliberate MVP limitation, not drift.
- **The MockBukkit limitation note is accurate.** Verified `ChestStateMock`
  (MockBukkit 4.116.1) `getBlockInventory()` returns `getInventory()`, so
  `ChestScannerTest`'s "adjacent chests stay separate" test cannot exercise the
  real `blockInventory` vs shared-`inventory` distinction — exactly as documented
  (`architecture.md:56-59`).
- **The duplicate chest predicate is correctly recorded as a carry-over**
  (`architecture.md:65-67`), matching round-1 §3. Still not worth doing in 030.
- **No new over-generalisation.** The fix pass added no container/reader/format
  abstractions; `ChestContent` remains two fields and `Region` remains a
  min/max + world holder. The `ChestCapture` issue is about correctness of the
  seam, not scope creep.
