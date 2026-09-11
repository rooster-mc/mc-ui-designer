# Architecture

Single Gradle module. Packages under `dev.cypdashuhn.uidesigner`:

```
uidesigner/
  UiDesignerPlugin.kt        plugin entry point; wires config, commands, services
  config/
    UiDesignerConfig.kt      typed view over config.yml (output path, ...)
  model/
    BlockPos.kt              pure position value type + canonical ordering
    UiChest.kt               UiChest / UiRow / UiSlot + DesignJson config
  capture/
    Region.kt                capture-side min/max corners + Bukkit world
    ChestContent.kt          capture-side intermediate: chest position + inventory
    SelectionSource.kt       interface: player -> region (seam for tests)
    FaweSelectionSource.kt   FAWE-backed implementation
    ChestCapture.kt          selection source + player -> CapturedSelection? (region + contents)
    ChestScanner.kt          region -> list<ChestContent>, filters chest blocks
    DoubleChestGrouper.kt    merges double-chest halves into one design
  naming/
    ChestNamer.kt            read/write the name of a chest block
  export/
    JsonExporter.kt          model -> JSON string/file (atomic write; single ordering authority)
  commands/
    UiDesignerCommand.kt     /uidesigner save | reload | help
    ChestEditCommand.kt      /chest-edit <name> | clear
  util/
    Messages.kt              Adventure components / prefixes
```

## Seams

- **`SelectionSource`** isolates FAWE. `ChestScanner` takes a `Region` (which
  carries its `World`), never touching WorldEdit directly, so `SelectionSource`
  and `ChestScanner` are testable with fakes. `ChestCapture.capture(source,
  player)` is the production entry point that glues the two: `null` means the
  player has no usable selection; otherwise it returns a `CapturedSelection`
  carrying the `Region` and its `List<ChestContent>` (empty when the selection
  contained no chests). The plugin wires a lazy delegating `SelectionSource`
  rather than `FaweSelectionSource` directly: FAWE is `compileOnly` and absent
  from MockBukkit's classpath, and the delegation keeps `FaweSelectionSource`
  from class-loading during `onEnable`.
- **`Region` is cuboid-only.** `FaweSelectionSource` reduces any FAWE selection
  (including non-cuboid `//hcyl`/`//poly`) to its min/max bounding box, so a
  chest inside the box but outside the actual selection is captured. Shape
  fidelity is out of scope for the MVP. `Region.blockAt(BlockPos)` is the
  shared `BlockPos` -> `World.getBlockAt` helper for the grouper and the
  command.
- **`model`** and **`export`** are pure Kotlin: no Bukkit imports. They are the
  easiest place to get coverage and the place where format correctness lives.
- **`ChestContent` lives in `capture/`, not `model/`** (decided in 030): it holds
  Bukkit `ItemStack`s, so putting it in `model` would break that package's
  purity. `BlockPos` moved from `UiChest.kt` to `model/BlockPos.kt` in the same
  ticket, once `Region`/`ChestContent` became its second consumer.
- **`ChestScanner`** is the only place that *enumerates* blocks and reads
  inventories, turning Bukkit `Block`/`Inventory` into the capture-side model
  (`ChestContent`); grouping may do targeted block lookups through the injected
  `Region` (Bukkit-coupled by design, testable with MockBukkit), and only
  `model`/`export` are fully Bukkit-free. It returns one entry per chest block,
  ordered by `x`, then `y`, then `z`, and skips blocks in unloaded chunks rather
  than forcing a chunk load. Each entry reads the block's own 27-slot inventory
  (`Chest.getBlockInventory`), not the shared double-chest inventory, so a
  double chest's halves stay independent until the grouper merges them.
  MockBukkit cannot form a real double chest and `ChestStateMock.getBlockInventory()`
  returns `getInventory()`, so tests only pin that adjacent chest blocks yield
  separate 27-slot entries; the `blockInventory` vs shared-`inventory`
  distinction still needs the dev server (040's holder-route tests fake the
  shared inventory).
- **Grouper input contract (040/060).** `ChestContent` stays `position + items`
  only. `DoubleChestGrouper` receives the `Region` alongside the
  `List<ChestContent>`, so it can reach each block via
  `region.world.getBlockAt(position.x, position.y, position.z)`; 060 reads chest
  names from the same blocks with `ChestNamer.nameOf(block)`. `items` is
  expected to be a positive multiple of 9 (the scanner captures 27, a merged
  double 54); the grouper fails fast otherwise rather than emitting a malformed
  `rows`.
- **Double detection (040).** The holder route is primary:
  `(block.state as? Chest)?.inventory?.holder as? DoubleChest`, then
  `DoubleChest.leftSide`/`rightSide` give the two half positions and the merged
  content is read once from the shared 54-slot `holder.inventory`. Block-data
  geometry (`org.bukkit.block.data.type.Chest` `LEFT`/`RIGHT` + `getFacing()`;
  partner = the facing rotated clockwise for `LEFT`, counter-clockwise for
  `RIGHT`) is the fallback for unlinked/mismatched halves, because MockBukkit
  4.116.1 cannot form a real `DoubleChest` (adjacent chests stay single and
  `ChestStateMock`'s inventory holder is the state itself). The fallback only
  merges when the partner block is the complementary half (same facing, opposite
  `LEFT`/`RIGHT`) and has not already been emitted; it then concatenates the two
  halves' captured 27-slot lists (the `RIGHT` half first, then `LEFT`, matching
  the holder route's shared-inventory order) rather than trusting
  `Chest.inventory`, which is the 27-slot block inventory when the pair is
  unlinked. `rows` is derived from the item list size (`size / 9`, required to be
  a positive multiple of 9) for both routes, so a 27-item list can never be
  stamped as 6 rows. Consumed positions are checked before merging and extended
  on emit, so no chest is emitted twice. Both routes are unit tested: the holder
  route with a test-injected fake `DoubleChest`, the fallback with
  `ChestDataMock` orientations in both input orders. The fallback's
  clockwise/counter-clockwise rule is hand-checked against vanilla
  `ChestBlock.getConnectedDirection`, but MockBukkit cannot verify it against
  real chest geometry; that needs the dev server.
- **Only one half selected (040).** The selection is authoritative, so a chest
  whose partner half is absent from `contents` stays a single 3-row entry built
  from that half's own 27 captured slots; the grouper never reads the
  unselected half. Consumed positions keep a merged double's halves from being
  emitted twice. The grouper returns `UiChest` with `name = null` and does not
  order; `UiDesignerCommand` populates names via `ChestNamer.nameOf` at each
  canonical position, and `JsonExporter` remains the ordering authority.
- **Chest predicate duplication.** `ChestScanner` and `ChestNamer` each define
  their own chest-material check; this is a deliberate carry-over until a third
  consumer appears, then extract one shared `isChest`.
- **`UiDesignerCommand`** is the integration point for `/uidesigner save |
  reload | help`. It composes `ChestCapture`, `DoubleChestGrouper`,
  `ChestNamer`, and `JsonExporter` without owning their logic, and injects
  `SelectionSource`, a `() -> UiDesignerConfig` provider, a reload action, and
  an exporter function so the pipeline is unit-testable without CommandAPI
  dispatch. `save` runs on the CommandAPI player executor (main thread); file
  IO stays synchronous for the MVP. `save` requires `uidesigner.save` and
  `reload` requires `uidesigner.reload`, both defaulting to op; `help` and the
  bare root need no permission. `reload`, `help`, and the root accept any
  sender (console included); only `save` is player-only.
- **`JsonExporter`** is the single ordering authority: it sorts chests by
  canonical position and rows/slots by index. The grouper (040) merges double
  chests and must not re-sort; the exporter normalises order.

## Data flow

```
player + FAWE selection
        │  SelectionSource.selectionOf
        ▼
   Region? (min/max corners + world; null = no selection)
        │  ChestCapture.capture -> ChestScanner.scan
        ▼
  CapturedSelection?          (region + List<ChestContent>, one per chest *block*;
        │                      empty contents = selection had no chests)
        │  DoubleChestGrouper(selection.region, selection.contents)
        │  -> region.world.getBlockAt(position.x, position.y, position.z)
        ▼
  List<UiChest>               (double chests merged; names still null)
        │  UiDesignerCommand: ChestNamer.nameOf at each canonical position
        ▼
  List<UiChest>               (names populated)
        │  JsonExporter
        ▼
        JSON file (config.outputFile)
```

## Conventions

- Kotlin, `kotlinx-serialization`, CommandAPI, Adventure components.
- No comments in code unless they explain non-obvious *why*.
- Ktlint formatting via `.editorconfig` (max line 100).
- Tests use backticked sentence names and JUnit 6.
