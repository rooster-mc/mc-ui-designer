# correctness review — 140 (Abort export on a double chest clipped by the selection)

## Round 1
### Verdict
Ship with one fix. The clipped-half detection is sound on every path I traced:
holder and geometry partner positions match vanilla
`ChestBlock.getConnectedDirection` for all four facings and LEFT/RIGHT, the
bounds check is coordinates-only (it never reads the absent partner), `save`
returns `ClippedChests` before the config read and the exporter, and single
chests, fully-captured doubles and mismatched neighbours keep their existing
behaviour. The single gap is material coverage: the fail-closed guarantee does
not reach the eight copper chest variants, which are real double-chest blocks on
this Paper version but are filtered out before the grouper ever sees them, so a
clipped copper double is still silently dropped (or hidden behind a "no chests"
message).

### Findings
#### 1. Copper chests bypass the scan, so a clipped copper double is never reported
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:8,14`
  (`CHEST_MATERIALS`), mirrored in
  `src/main/kotlin/dev/cypdashuhn/uidesigner/naming/ChestNamer.kt:13`.
- Problem: `CHEST_MATERIALS` contains only `Material.CHEST` and
  `Material.TRAPPED_CHEST`. Paper 26.2 adds eight copper chest materials
  (`COPPER_CHEST`, `EXPOSED_COPPER_CHEST`, `WEATHERED_COPPER_CHEST`,
  `OXIDIZED_COPPER_CHEST` and the four `WAXED_*` variants); in
  `paper-api-26.2.build.111-stable` every one maps to
  `org.bukkit.block.Chest` / `org.bukkit.block.data.type.Chest`
  (`Material.java`), and NMS `CopperChestBlock extends ChestBlock`, so copper
  chests form doubles with exactly the LEFT/RIGHT geometry this ticket detects.
  Because `ChestScanner.scan` drops copper blocks before the grouper runs, the
  new `clipped` list can never contain a copper half. This is a miss the ticket's
  acceptance criterion does not exclude: "A selection containing exactly one half
  of a double chest writes no output file and reports the captured and partner
  blocks with a fix hint."
  Reproduction: `/uidesigner save` with a selection that clips one half of a
  copper double chest. If the selection holds only that pair, `contents` is
  empty, `save` returns `NoChests`, and the player is told "The selection
  contains no chests" instead of which half/partner is clipped; if the selection
  also holds a normal chest, the export succeeds and silently omits the copper
  pair — the exact silent truncation the ticket exists to prevent. Both outcomes
  are reachable in vanilla play, not only after `/setblock`.
- Suggested fix: add the eight copper chest materials to
  `ChestScanner.CHEST_MATERIALS` (and, for consistency, `ChestNamer.isChest`,
  which gates `/chest-edit`). If that is intentionally out of scope for 140, defer
  it to a named follow-up ticket with the limitation recorded, since the
  acceptance criterion currently reads as "all double chests". A shared chest
  predicate would stop the scanner/namer sets from drifting.

### Non-findings
- **Holder-route partner positions.** `partnerOf` (`DoubleChestGrouper.kt:90-103`)
  takes `holder.leftSide`/`rightSide`, maps each `Chest` to `BlockPos(it.x, it.y,
  it.z)` and returns the side that is not `position`. The 040 finding that
  `DoubleChest.getLeftSide()` is really the RIGHT-type half does not matter here
  because both sides are treated symmetrically. When one side is null or
  `position` is not among the halves, it falls through to geometry. Correct.
- **Geometry-route partner positions (all four facings, LEFT/RIGHT).**
  `partnerOffset` (`:161-169`) was checked against the decompiled vanilla
  `ChestBlock.getConnectedDirection` in `run/cache/mojang_26.2.jar`:
  `TYPE == LEFT ? FACING.getClockWise() : FACING.getCounterClockWise()`, and
  `Direction.getClockWise` maps NORTH→EAST, SOUTH→WEST, WEST→NORTH, EAST→SOUTH.
  The code's `LEFT = (-modZ, +modX)` / `RIGHT = (+modZ, -modX)` reproduces that
  exactly for N/S/E/W; the orientation tests' hand-written `(dx, dz)` agree.
- **Bounds check does not touch the absent block.** `clippedHalf` (`:80-88`)
  computes the partner via `partnerOf`, checks `partner in byPosition`, and
  classifies with `Region.containsBlock` (`:210-211`), which reads only
  `minX..maxX`/`minY..maxY`/`minZ..maxZ`. It never calls `region.blockAt` on the
  partner. `geometryMatch`'s only world read of the partner
  (`isComplementaryHalf`, `:127-132`) is guarded by `byPosition[partner] == null`
  at `:114`, i.e. the partner is captured/loaded. No chunk is forced to load.
- **Outside-selection vs inside-but-uncaptured is accurate.** `Region` is the
  selection's cuboid bounding box and `ChestScanner` scans that same box via
  `loadedBlockPositions` (`ChestScanner.kt:11`), so for a loaded partner inside
  the bounds the scanner would have captured it; inside-bounds-and-absent
  therefore means the chunk was unloaded (or, in a corrupt world, the block is
  not a chest). Non-cuboid selections are reduced to the bbox by construction and
  the scanner intersects the same bbox, so the conclusion still holds.
- **Short-circuit ordering.** `UiDesignerCommand.save` (`:89-90`) computes
  `grouped`, returns `ClippedChests` before `named`/`configProvider()`/
  `exporter`, and the only `exporter(...)` call is `:104`. `ChestNamer.nameOf` is
  also not reached. No file is written on the clipped path.
- **Single chests preserved.** A `SINGLE` chest yields `partnerOffset == null`
  and its inventory holder is the state (not a `DoubleChest`), so `clippedHalf`
  returns null and the 3-row single is emitted as before.
- **Fully-captured doubles preserved.** Both halves in `byPosition` are merged by
  `halvesIn` (two positions) or by the complementary geometry branch; `clipped`
  stays empty. Covered by `save counts a double chest as a single design` and the
  geometry/`assertOrientationMerges` tests.
- **Mismatched neighbour preserved.** `clippedHalf` only classifies when the
  *computed* partner is absent from `byPosition`; a present-but-mismatched or
  present-but-consumed computed partner leaves the chest a single, matching
  `docs/architecture.md`. The `a single chest is not re-merged by a mismatched
  adjacent half` test pins the case where the mismatched half points at the
  single. A half-type chest whose computed partner position is absent but which
  sits next to an unrelated chest is still clipped — that is fail-closed and
  consistent with "LEFT/RIGHT means half", so it is not a false positive.
- **No clipped half is missed through `consumed`.** `consumed` is only added on
  merge/single/clipped emit; a genuine complementary pair is merged and consumes
  both halves on the first encounter, so the second is skipped before
  `clippedHalf` is reached. The only way a half's computed partner is
  present-but-consumed is a corrupt world where the pair did not match, which the
  documented contract keeps as a single.
- **No position emitted twice.** `consumed += content.position` on the clipped
  branch (`:50`) and `consumed += match.positions` on the merge branch (`:44`),
  with `if (content.position in consumed) continue` at the top (`:41`).
- **Item ordering and canonical position unchanged.** The diff does not touch
  `orderedItems` (`:141-150`) or `uiChest(match.items, match.positions.min())`
  (`:45`); `JsonExporter` remains the ordering authority.
- **`clipped.first()` is safe.** `ClippedChests` is only constructed after
  `grouped.clipped.isNotEmpty()` (`:90`), so `clippedChestsMessage` cannot throw
  on an empty list from the production path, and the "N more ... also clipped"
  count/grammar (`:180-185`) is correct for 0/1/2+.
- **Tests exercise the code.** The holder clipped tests inject a fake
  `DoubleChest` whose chests carry default `SINGLE` block data, so the partner
  can only come from `partnerOf`'s holder branch; the geometry clipped tests use
  `ChestDataMock` orientations. Both partner-location cases are covered for each
  route, and `UiDesignerCommandTest` asserts zero exporter calls plus the exact
  message branches. The tests are not vacuous with respect to the new paths.
- **Threading.** `save` and the grouper run from the command executor on the main
  thread; the new code adds no async file IO and no cross-thread Bukkit access.
