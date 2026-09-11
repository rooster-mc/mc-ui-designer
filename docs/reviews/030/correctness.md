# Correctness review — 030 (FAWE selection capture and chest scan)

## Round 1

### Verdict
Ship. I traced the scan bounds, negative-coordinate chunk math, the
unloaded-chunk guard, the `blockInventory`/clone path, the chest filter,
`Region.of`, and every FAWE 2.15.3 call against the cached jars, and found no
functional defect: the change meets every acceptance criterion. The one
correctness-adjacent finding is that non-cuboid FAWE selections are reduced to
their bounding box, so a chest outside the actual selection but inside the box
can be captured; the ticket defines `Region` as min/max, so this is a design
limitation to document rather than a bug.

### Issues

#### 1. Non-cuboid FAWE selections over-capture through the bounding box (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/FaweSelectionSource.kt:25-37`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:12-26`
- Problem: `session.getSelection(world)` can return any FAWE `Region`
  (polygonal, cylindrical, convex, ...), but `FaweSelectionSource` keeps only
  `minimumPoint`/`maximumPoint` and `ChestScanner` scans the whole cuboid.
  A chest inside the bounding box but outside the actual selection is therefore
  captured and exported. The ticket explicitly defines `Region` as "min/max
  corners + world" (`docs/tasks/030-selection-capture.md:14-16`), so cuboid
  semantics are intended, but the assumption is invisible to the user and to
  later tickets.
- Reproduction: build a `//hcyl` or `//poly` selection whose bounding box
  contains a chest that the selection itself excludes, then run the capture;
  the chest appears in the result. Same for a selection that only includes one
  corner region of a large bbox.
- Suggested fix: document the cuboid-only assumption where the user sees it
  (command feedback in 060 and/or `docs/design.md`), or, if shape fidelity is
  wanted, keep the bbox as a cheap pre-filter and additionally test
  `selection.contains(BlockVector3.at(x, y, z))` before capturing. If the
  cuboid choice is final, a one-line note in `Region`/`FaweSelectionSource` is
  enough.

### Non-issues
- **Scan bounds are inclusive and correct.** `region.min.x..region.max.x`
  (and y/z) matches FAWE's inclusive `minimumPoint`/`maximumPoint`
  (`ChestScanner.kt:12-14`); no off-by-one. `Region.of` normalises per axis
  (`Region.kt:12-26`) and the private constructor makes a non-normalised
  `Region` unrepresentable.
- **Negative-coordinate chunk math is correct.** `x shr 4` / `z shr 4` are
  arithmetic shifts, i.e. floor division by 16: `-1 shr 4 == -1`,
  `-16 shr 4 == -1`, `-17 shr 4 == -2`, which is exactly Minecraft chunk
  indexing. `World.isChunkLoaded(int, int)` takes chunk coordinates (confirmed
  in the Paper 26.2 `World` sources and `CraftWorld` bytecode), and the guard
  is evaluated before `getBlockAt`, so unloaded chunks are never touched and no
  synchronous chunk load is forced.
- **`chest.blockInventory` is the right inventory.** Paper's `Chest` javadoc
  ("If the chest is a double chest, it returns just the portion of the
  inventory linked to the half of the chest corresponding to this block
  state") and `CraftChest.getBlockInventory()` (wraps `getBlockEntity()`, the
  half's own 27-slot `Container`) confirm each double-chest half is captured
  separately; `chest.inventory` would have pre-merged them and defeated 040.
- **No live-inventory aliasing.** `Inventory.getContents()` returns null for
  empty slots (verified in `CraftInventory.asCraftMirror(List)` bytecode) and
  mirrors otherwise; `ItemStack.clone()` deep-copies via
  `CraftItemStack.clone() -> handle.copy()`, so mutating the captured list
  cannot reach the chest. Indices are preserved, including the null holes
  (`ChestScanner.kt:22`).
- **Only chests slip through.** The `Material.CHEST`/`TRAPPED_CHEST` filter
  plus `block.state as? Chest` excludes barrels, shulkers and non-chest blocks;
  `TRAPPED_CHEST` maps to `CraftChest` in Paper 26.2 (verified in
  `CraftBlockStates`), so trapped chests are read through the same path. No
  third material maps to `CraftChest`.
- **Order is deterministic by construction.** The `x → y → z` nesting matches
  `BlockPos.compareTo` and the canonical order in `docs/data-format.md:41-43`;
  no sort is needed and the exporter remains the single ordering authority.
- **FAWE 2.15.3 API usage is correct** (verified with `javap` against
  `FastAsyncWorldEdit-Core/Bukkit-2.15.3.jar`): `WorldEdit.getInstance()`
  exists; `getSessionManager()` returns `SessionManager`; `getIfPresent` takes a
  `SessionOwner`, which `BukkitAdapter.adapt(Player) -> BukkitPlayer` is;
  `isSelectionDefined(World)` guards a world-scoped `getSelection(World)` that
  throws `IncompleteRegionException`; `Region.getMinimumPoint()/getMaximumPoint()`
  return `BlockVector3` with the non-deprecated `x()/y()/z()` accessors; and
  `BukkitAdapter.adapt(com.sk89q.worldedit.world.World)` resolves both
  `BukkitWorld` and FAWE's `WorldWrapper` (falls back to lookup by name).
- **No exception can escape `selectionOf` for the "friendly failure" cases.**
  No session → `null`; selection in another world → `isSelectionDefined`
  returns false → `null`; incomplete/defined-elsewhere selection →
  `IncompleteRegionException` caught → `null`. `World.equals` is value-based
  (`BukkitWorld` compares the underlying Bukkit world, `WorldWrapper` delegates
  to its parent), so the world-scoped check is reliable. The user-facing message
  is correctly deferred to 060 (`docs/tasks/060-export-command.md:17-18`); 030
  only owns the nullable contract.
- **`BlockPos` move is clean.** Only one `data class BlockPos` exists
  (`model/BlockPos.kt:3`), all consumers import
  `dev.cypdashuhn.uidesigner.model.BlockPos`, and `UiChest.kt` still compiles
  because it is in the same package. No duplicate or stale import; no second
  declaration remains in `UiChest.kt`.
- **No JSON is produced here, so `docs/data-format.md` cannot be mismatched.**
  `ChestContent` is an internal capture type; item→`UiSlot` mapping and
  `rows`/`name` derivation belong to 040/060.
- **`ChestContent` losing block context (world/holder/block-data/name) is
  already covered** by the architecture review's issue 1 (medium). From a
  correctness angle it is not a 030 acceptance failure — the ticket defines
  `ChestContent` as "position + `ItemStack` list" — but the implementor should
  resolve that contract before 040, as the architecture report asks.
- **Threading.** The new code has no async path and no file IO; all Bukkit
  access is synchronous and main-thread-only, which is what 060's main-thread
  pipeline requires. No concern to fix in 030.
- **Unloaded-chunk skipping is intentional and documented.** `design.md:71-73`
  and `architecture.md:44-45` state that unloaded halves are invisible and
  never force-loaded, matching `ChestScanner.kt:15`; it is a deliberate MVP
  limitation, not silent data loss introduced here.

## Round 2

### Verdict
Ship. The fix pass resolves round 1's only finding (the cuboid-only
assumption is now documented in `design.md` and `architecture.md`) and the
tester's round-1 issue 1 (the new `ChestCapture.capture` is a real production
null/empty seam that the "no selection" test now exercises). The new tests
genuinely pin the negative-coordinate floor math and the unloaded-chunk skip.
The only findings are in the newly written grouper-contract prose, which is
factually inconsistent with the code and with the 040 ticket; they are
forward-looking documentation issues, not 030 behaviour bugs.

### Issues

#### 1. Grouper-contract prose contradicts the scanner seam it sits under (severity: low)
- Location: `docs/architecture.md:49-55` vs `docs/architecture.md:60-64`
- Problem: the scanner bullet still says `ChestScanner` keeps "Bukkit
  block/inventory access out of grouping", but the very next bullet gives
  `DoubleChestGrouper` the `Region` so it can "reach each block via
  `region.world.getBlockAt(...)`". That is direct Bukkit block access inside
  grouping, so the two adjacent sentences cannot both be true. An implementor
  reading the seam contract gets contradictory guidance on whether grouping may
  touch the world.
- Reproduction: read `architecture.md:50-51` ("out of grouping") then
  `architecture.md:62-64` (`getBlockAt` inside `DoubleChestGrouper`).
- Suggested fix: pick one contract and word the scanner bullet to match, e.g.
  "keeping the scanner→grouper data shape free of block/inventory handles; the
  grouper reaches blocks only through the injected `Region`", or drop the
  "out of grouping" claim entirely.

#### 2. `region.world.getBlockAt(position)` is not a Bukkit overload (severity: low)
- Location: `docs/architecture.md:63`, `docs/architecture.md:82`
- Problem: `position` is `dev.cypdashuhn.uidesigner.model.BlockPos`. Bukkit's
  `World` only exposes `getBlockAt(int, int, int)` and
  `getBlockAt(Location)` (verified in
  `paper-api-26.2...-sources.jar:org/bukkit/World.java:168-178`), and the repo
  defines no `BlockPos` extension. A 040 implementor following the doc verbatim
  hits a compile error.
- Reproduction: write `region.world.getBlockAt(BlockPos(0, 0, 0))` — no such
  method.
- Suggested fix: document `region.world.getBlockAt(position.x, position.y,
  position.z)` (or construct a `Location`).

#### 3. The documented grouper input cannot be produced by the documented entry point (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestCapture.kt:6-7`,
  `docs/architecture.md:36-38,60-64,74-88`, `docs/tasks/040-double-chest-grouping.md:14`
- Problem: `architecture.md:36-38` calls `ChestCapture.capture(source, player)`
  "the production entry point", but it returns only `List<ChestContent>?` and
  discards the `Region`. The data flow (`:82`) then feeds
  `DoubleChestGrouper(region, contents)`, and the grouper contract (`:60-64`)
  needs that `Region` to reach blocks. There is no documented path from
  `ChestCapture`'s return value to the grouper's first argument, and 040's scope
  still says the grouper is "given `List<ChestContent>`". So the 030 output
  type and the 040/060 contract remain unaligned, which is the exact seam the
  round-1 architecture review asked to settle.
- Reproduction: trace the diagram — `ChestCapture.capture` yields
  `List<ChestContent>?`; `DoubleChestGrouper(region, contents)` needs a `Region`
  that the entry point threw away.
- Suggested fix: either have `ChestCapture` return a small result carrying the
  `Region` alongside the contents (or return the `Region` and let 060 call
  `ChestScanner.scan`), or state explicitly in `architecture.md` and 040 that
  060 calls `SelectionSource.selectionOf` + `ChestScanner.scan` directly and
  that `ChestCapture` is only the null/empty convenience.

### Non-issues
- **Round-1 finding 1 is resolved.** `docs/design.md:57-59` and
  `docs/architecture.md:39-42` now state the cuboid-only bounding-box
  behaviour for `//hcyl`/`//poly`, which matches `FaweSelectionSource.kt:25-37`
  (it keeps only `minimumPoint`/`maximumPoint`). Accurate.
- **`ChestCapture` preserves the null/empty semantics and the FAWE path.**
  `source.selectionOf(player)?.let(ChestScanner::scan)`
  (`ChestCapture.kt:6-7`) returns `null` exactly when `selectionOf` is `null`
  and an empty list for a valid region with no chests; it adds no WorldEdit call
  and does not alter `FaweSelectionSource`. The `no selection` test now goes
  through this production object (`ChestScannerTest.kt:42-44`), not just the
  fake.
- **The negative-coordinate test really pins floor semantics.** `-17` with
  `x / 16` would be `-1`, and only chunks `(0,0)` and `(-2,0)` are loaded, so a
  truncating refactor would return an empty list and fail
  (`ChestScannerTest.kt:128-136`). `x shr 4` maps `-17` to `-2` as intended.
- **The unloaded-chunk test asserts its premise.** `assertFalse(world
  .isChunkLoaded(1, 0))` runs before the scan (`ChestScannerTest.kt:142-143`),
  and `WorldMock.getBlockAt` stores blocks in a world map independent of
  `loadedChunks` (verified in `mockbukkit-v26.2-4.116.1-sources.jar`), so the
  chest exists but the scanner's guard is what skips it. The test exercises the
  production branch, not a missing block.
- **The adjacent-chest test's limits are stated, not hidden.**
  `ChestStateMock.getBlockInventory()` returns `getInventory()` (verified,
  `ChestStateMock.java:93-95`), so the test cannot distinguish the two; but
  `architecture.md:56-59` says exactly that and scopes the test to "adjacent
  chest blocks yield separate 27-slot entries". Not a false claim, and no
  production behaviour changed.
- **`Chest.getBlockInventory()` is still the right call.** Paper's javadoc
  (verified in `paper-api-26.2...-sources.jar:org/bukkit/block/Chest.java:14-29`)
  confirms a double chest's half returns only its 27-slot portion, so 040 still
  receives independent halves.
- **No regression from the fix pass.** The only production addition is
  `ChestCapture.kt`; `ChestScanner`, `Region`, `FaweSelectionSource`,
  `ChestContent`, `BlockPos` and `UiChest` are byte-for-byte the round-1 code.
  Ordering (`x→y→z`), per-axis `Region` normalisation, the inclusive scan
  bounds, cloning, and the main-thread-only path are unchanged.
- **Still no JSON here**, so `docs/data-format.md` cannot be mismatched by 030.
