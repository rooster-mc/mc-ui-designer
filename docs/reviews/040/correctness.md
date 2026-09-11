# Correctness review — 040 (Double-chest grouping)

## Round 1
### Verdict
Ship with fixes. The primary holder route is sound in production and the
row/slot math is correct, but the geometry fallback is not safe against
unlinked/mismatched halves: it can emit `rows = 6` from a 27-slot inventory and,
when a half was already emitted as a single, it can emit that chest twice,
violating an explicit acceptance criterion.

### Issues
#### 1. Geometry fallback hardcodes 6 rows while reading a 27-slot inventory (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:62-73`
  (`geometryMatch` returns `chest.inventory.contents`) and `:31` (`rows = DOUBLE_ROWS`).
- Problem: the fallback returns `chest.inventory.contents` for the *current*
  half. `Chest.getInventory()` is only the shared 54-slot
  `DoubleChestInventory` when the two halves are actually linked (holder is a
  `DoubleChest`); for an unlinked chest it is the block's own 27-slot
  inventory (`CraftChest.getInventory()` returns `getBlockInventory()` unless
  `ChestBlock.getMenuProvider` yields a double provider — verified against
  `run/versions/26.2/paper-26.2.jar`). So the fallback can return a 27-item
  list while `group` stamps `rows = 6`, producing a `UiChest` whose `rows` (6)
  contradicts its `content` (only rows 1..3), and any item in the *partner*
  half is silently lost. This is exactly the `docs/data-format.md:45-46` rule
  ("double chests have 54 [slots]") being violated.
  Reproduction: adjacent blocks where one has `ChestData.Type.LEFT` (or RIGHT)
  pointing at the other but the pair is not linked, both inside the selection
  and both in `contents` (holder is `null`). The scanner reads 27 per half, the
  fallback merges, and the result is a 6-row chest with 27 items. This is
  reachable after a FAWE paste / `/setblock` of inconsistent chest block data,
  and is the only way the fallback's double branch runs in production (a
  genuinely linked pair takes the holder route).
  The `geometryChest` test helper (`DoubleChestGrouperTest.kt:241-258`) injects
  a 54-slot `state.provided` for both halves, so the test can never observe
  this divergence — it pins the fallback's plumbing, not its inventory
  contract.
- Suggested fix: don't hardcode the row count from the detection branch. Derive
  `rows` from the list actually being serialized (e.g. `items.size / 9`, with a
  guard that the size is a multiple of 9), so a 27-item fallback yields 3 rows.
  Optionally, in `geometryMatch` also require the current half's inventory to be
  the 54-slot shared one before treating it as a double, otherwise fall through
  to the single route. Add a test that drives the fallback with a 27-slot
  `provided` and asserts a 3-row result (or, if you keep 6 rows, that both
  halves' items are combined).

#### 2. A partner already emitted as a single can be merged again, emitting it twice (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:24-43`
  (`consumed` handling) and `:62-73` (`geometryMatch` only checks
  `partner in byPosition`).
- Problem: `consumed` is only consulted for the chest currently being iterated
  (`:25`); a later chest may still claim a partner that was already emitted.
  With the geometry route, `partner in byPosition` is the *only* check — the
  partner's own block data is never validated, and `consumed` is not consulted.
  Reproduction (inconsistent block data, e.g. a pasted structure): chest `B`
  at `(0,0)` is `SINGLE`; chest `A` at `(1,0)` is `LEFT` facing `SOUTH` so
  `partnerOffset` points at `B`. Scanner order is `x`-major, so `B` is processed
  first: holder is `null`, `partnerOffset(SINGLE, …)` is `null`, `B` is emitted
  as a 3-row single. Then `A` is processed: `partner = (0,0) in byPosition`, so
  the fallback emits a double containing `B`. `B` now appears both as its own
  `UiChest` and inside `A`'s — the "No chest is emitted twice" acceptance
  criterion (`docs/tasks/040-double-chest-grouping.md:28`) fails. The holder
  route is symmetric and does not have this asymmetry, but the fallback does.
  Note this repro also triggers issue 1 (A's inventory is 27, rows = 6).
- Suggested fix: in the fallback, require the partner to be the complementary
  half — same `facing`, `partner`'s `blockData.type` the opposite of the current
  one — before merging; and/or pass `consumed` into `match`/`geometryMatch` and
  reject a partner already consumed. A cleaner alternative is a first pass that
  resolves all doubles before emitting any single, so an emitted single can
  never be re-claimed.

### Non-issues
- **Holder route in production.** `CraftChest.getInventory()` builds a
  `CraftInventoryDoubleChest` when the pair is linked, whose `getHolder()` is a
  `DoubleChest`; `DoubleChest.getLeftSide()/getRightSide()` resolve to `Chest`
  block states (`BlockEntity.getOwner` → `CraftBlock.at(...).getState()`), so
  `halvesIn` works as written. Reading `holder.inventory.contents` yields the
  combined 54 slots.
- **`partnerOffset` matches vanilla.** LEFT = clockwise (`N→E`, `E→S`, `S→W`,
  `W→N`) and RIGHT = counter-clockwise, checked against the four table entries
  at `:81-100`; the orientation tests' hand-written `(dx, dz)` agree with the
  table (their lack of an independent vanilla anchor is the tester's issue 4).
- **One-half selection.** `halvesIn` (`:75-79`) filters to `byPosition` and
  `takeIf { it.size == 2 }` falls through when the partner is unselected; the
  geometry route then finds the partner absent and returns `null`, so the
  single route uses the selected half's own `content.items` (27) and never
  reads the unselected half's inventory. Matches the documented decision.
- **No double emission with consistent block data.** The `consumed.add` at
  `:25` plus `consumed += match.positions` at `:28` make the reversed-input
  case idempotent (both halves are consumed on the first encounter).
- **Row/slot math.** `index / 9 + 1` and `index % 9 + 1` (`:107`, `:114`) give
  1-based row/slot correctly for 27- and 54-slot lists; `groupBy` preserves
  ascending row order and `mapNotNull` preserves ascending slot order, and the
  exporter re-sorts anyway.
- **Empty/air slots.** `stack?.takeUnless { it.isEmpty }` (`:106`) drops nulls,
  air and zero-amount stacks, and `rows()` never emits a row with no slots.
- **Slot names.** `plainDisplayName` (`:122-126`) uses the custom display name,
  strips it to plain text and maps blank/whitespace to `null`; the exporter
  also omits blanks. No material name leaks in.
- **`name = null` on the chest.** Correct for 040; naming is 060
  (`docs/tasks/040-double-chest-grouping.md:30-31`).
- **Canonical position.** `match.positions.min()` uses `BlockPos`'s
  x→y→z ordering, matching `docs/data-format.md:41-43`; the single route keeps
  its own position. Grouper output order is not the schema's concern — the
  exporter is the ordering authority.
- **Empty input / unloaded chunks.** `group(region, emptyList())` returns an
  empty list; a chest in a chunk that unloads between scan and grouping yields
  a non-`Chest` state and degrades to the single route instead of throwing.
- **Docs nit (not filed as an issue).** `docs/architecture.md:77-79` says both
  routes read the shared 54-slot inventory; the fallback only does so when the
  halves are linked. Worth a wording tweak alongside issue 1's fix.

## Round 2
### Verdict
Ship with fixes. Both round 1 issues are genuinely fixed (the fallback no longer
reads a 27-slot inventory as 6 rows, and a partner already emitted cannot be
re-merged), and the holder route is unchanged. One medium issue remains: the
fallback's item order is position-sorted, which contradicts the slot order the
production holder route (and the in-game GUI) produces, so the fallback test
pins a mapping production never emits.

### Issues
#### 1. Fallback orders halves by position, holder route orders them RIGHT-first (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:85-89`
  (`orderedItems`), used at `:68`; holder route at `:50`; test
  `src/test/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouperTest.kt:200-225`.
- Problem: the holder route returns `holder.inventory.contents`, and for a
  linked double that array is the shared `CompoundContainer`, i.e. the
  `Chest.Type.RIGHT` half's 27 slots first, then the `LEFT` half's. Verified
  against `run/versions/26.2/paper-26.2.jar`: `ChestBlock.getBlockType` maps
  `RIGHT -> DoubleBlockCombiner.BlockType.FIRST` (LEFT -> SECOND), and
  `DoubleBlockCombiner.combineWithNeigbour` builds `CompoundContainer(first,
  second)` with `first = isFirst ? current : neighbor`, so `container1` is the
  RIGHT half regardless of which half's `Chest.getInventory()` is queried
  (`CraftInventoryDoubleChest` sets `left = container1`; `CraftInventory`'s
  `getContents`/`getItem` read `container1` first). Note the naming is inverted
  from intuition: `DoubleChest.getLeftSide()` is
  `inventory.getLeftSide().getHolder()` = `container1.getOwner()`, i.e. the
  `Chest.Type.RIGHT` block. The fallback instead does
  `listOf(here, partner).sorted()`, so it puts whichever half has the lower
  `x`/`y`/`z` first. For `NORTH`- and `EAST`-facing pairs the LEFT half has the
  lower coordinate, so the fallback writes the LEFT half into rows 1..3 while
  production writes the RIGHT half there; `SOUTH`/`WEST` happen to agree. The
  fallback test asserts the divergent (LEFT-first) mapping, so it validates
  behaviour the primary route never produces — and the fallback is the only
  route MockBukkit can exercise.
- Reproduction: place a `NORTH`-facing double chest with LEFT at `(0,0)` and
  RIGHT at `(1,0)`. Put gold in RIGHT slot 0 and stone in LEFT slot 0. Linked
  (production): export has `row 1, slot 1 = minecraft:gold_ingot`. Drive the
  same two blocks through the geometry fallback (unlinked data, as MockBukkit
  forces): `orderedItems` sorts `(0,0)` before `(1,0)` and the export has
  `row 1, slot 1 = minecraft:stone`. Same physical chest, different design.
- Suggested fix: order by half type, not position: emit the RIGHT half's
  captured items first, then the LEFT half's. `geometryMatch` already has
  `data.type` and can pass it down (e.g. `orderedItems(data.type, here,
  partner, byPosition)` picking `[right, left]`), or derive the order from
  `isComplementaryHalf`'s partner data. Update
  `DoubleChestGrouperTest.kt:200-225`'s expected rows accordingly. If
  position-first is genuinely wanted, the holder route would have to be changed
  too — but that would reorder what the designer saw in the GUI, so it is the
  wrong direction.

#### 2. `rowCount` hard-fails the whole group on an unexpected list size (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/DoubleChestGrouper.kt:121-126`,
  called from `:37`.
- Problem: `require(items.isNotEmpty() && items.size % 9 == 0)` throws
  `IllegalArgumentException`, aborting the entire `group`/export for any
  `ChestContent` whose list is empty or not a multiple of 9. No current
  production path reaches it (`ChestScanner.kt:22` always captures 27, the
  holder route always 54, the fallback 27+27), so this is robustness rather
  than a live bug, but the grouper is a public entry point and a single
  malformed entry turns a partial-data situation into a hard crash instead of
  degrading to the single route. No test covers the guard.
- Suggested fix: make the contract explicit. Either keep fail-fast and add a
  test plus a doc line, or treat an empty/non-multiple list as the single route
  (e.g. only derive `rows` from `items.size / 9` when `size % 9 == 0` and fall
  back to the block inventory size otherwise) so one bad chest cannot kill the
  whole capture.

### Non-issues
- **Round 1 issue 1 fixed.** `geometryMatch` now returns
  `orderedItems(here, partner, byPosition)` (concatenated captured 27-lists,
  `:68`), not `chest.inventory.contents`, and `uiChest` derives `rows` from the
  serialized list via `rowCount` (`:36-37`). A 27-item fallback can no longer be
  stamped as 6 rows. The `geometryChest` tests feed 27-item lists and get a
  6-row/54-slot merge, so the fix is exercised.
- **Round 1 issue 2 fixed.** `geometryMatch` rejects `partner in consumed`
  (`:66`) and requires `isComplementaryHalf` (`:67`, `:71-76`), and `halvesIn`
  filters `it !in consumed` (`:97`). Traced the round 1 repro (SINGLE at
  `(0,0)`, `LEFT`/`SOUTH` at `(1,0)`) in both input orders: the SINGLE is
  emitted once and the LEFT is emitted once; neither claims the other. The
  `mismatched adjacent half` test (`:88-103`) pins it.
- **Consumed bookkeeping.** For a merge, `consumed += match.positions` (`:26`)
  adds both halves, so reversed input and the holder route are idempotent; the
  loop's `if (content.position in consumed) continue` (`:23`) skips the second
  half. Traced `[A,B]`, `[B,A]`, and `[A,C,B]` with A/B a pair: one entry.
- **Holder route unchanged.** `match` still reads
  `chest.inventory.holder as? DoubleChest` and, when `halvesIn` returns two
  in-selection, unconsumed positions, returns the shared 54-slot
  `holder.inventory.contents` (`:47-50`). The fallback is only reached when the
  holder is absent or a half is out of selection/already consumed, and in the
  latter case the geometry route re-checks `consumed`, so the shared-inventory
  read is not silently replaced for a linked, fully selected pair.
- **One-half selection.** `halvesIn`'s `takeIf { it.size == 2 }` (`:98`) falls
  through, then `byPosition[partner] == null` (`:66`) returns null, so the single
  route uses the selected half's own captured 27 items. Matches the documented
  decision (`docs/tasks/040-double-chest-grouping.md:40-44`,
  `docs/design.md`).
- **`isComplementaryHalf` reading block data through the region.** The partner
  is already in `byPosition` (scanned loaded), the read is a main-thread
  `getBlockAt().blockData`, and a non-`ChestData` partner degrades to no merge
  (`:73-74`). No correctness or threading problem for the current call path.
- **A fallback merge cannot re-claim an emitted position.** `here` is never
  consumed (loop skip) and `partner in consumed` is checked before building the
  match (`:66`), so no position is emitted twice across iterations.
- **Row/slot math, air slots, names.** `index / 9 + 1` and `index % 9 + 1`
  (`:133`, `:140`) are correct for 27/54; `rows` never emits an empty row;
  `plainDisplayName` (`:148-152`) drops blank custom names and no material name
  leaks. `name = null` on the chest is correct for 040 (naming is 060).
- **Canonical position.** `match.positions.min()` matches
  `docs/data-format.md:41-43`; `JsonExporter` remains the ordering authority.
