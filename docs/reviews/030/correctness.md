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
