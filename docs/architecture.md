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
  contained no chests).
- **`Region` is cuboid-only.** `FaweSelectionSource` reduces any FAWE selection
  (including non-cuboid `//hcyl`/`//poly`) to its min/max bounding box, so a
  chest inside the box but outside the actual selection is captured. Shape
  fidelity is out of scope for the MVP.
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
  distinction is verified on the dev server and by 040.
- **Grouper input contract (040/060).** `ChestContent` stays `position + items`
  only. `DoubleChestGrouper` receives the `Region` alongside the
  `List<ChestContent>`, so it can reach each block via
  `region.world.getBlockAt(position.x, position.y, position.z)` for
  `DoubleChest`-holder detection; 060 reads chest names from the same blocks
  with `ChestNamer.nameOf(block)`.
- **Chest predicate duplication.** `ChestScanner` and `ChestNamer` each define
  their own chest-material check; this is a deliberate carry-over until a third
  consumer appears, then extract one shared `isChest`.
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
  List<UiChest>               (double chests merged; JsonExporter owns ordering)
        │  JsonExporter
        ▼
        JSON file (config.outputFile)
```

## Conventions

- Kotlin, `kotlinx-serialization`, CommandAPI, Adventure components.
- No comments in code unless they explain non-obvious *why*.
- Ktlint formatting via `.editorconfig` (max line 100).
- Tests use backticked sentence names and JUnit 6.
