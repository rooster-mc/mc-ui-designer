# Architecture

One Gradle module in this repository (`UiDesigner`); the region and
WorldEdit-selection types come from the `rooster-region` composite build
(`:core`, `:worldedit`), and the command DSL comes from the `rooster-commands`
composite build (`:`, `:command-api`). `settings.gradle.kts` maps
`dev.rooster.region:rooster-region` to `:core`,
`dev.rooster.region:rooster-region-worldedit` to `:worldedit`,
`dev.rooster:rooster-commands` to the `rooster-commands` root project and
`dev.rooster:command-api` to its `:command-api` module; all three siblings
(`rooster-region`, `rooster-commands`, and the transitively required
`rooster-core`) must be checked out beside this repository, which
`settings.gradle.kts` guards with `check(...)`. Packages under
`dev.cypdashuhn.uidesigner`:

```
uidesigner/
  UiDesignerPlugin.kt        plugin entry point; wires config, commands, services
  config/
    UiDesignerConfig.kt      typed view over config.yml (output path, ...)
    ReloadResult.kt          reloadConfiguration outcome (reloaded/defaults/invalid)
  capture/
    ChestContent.kt          capture-side intermediate: chest position + inventory
    ChestCapture.kt          selection lookup + player -> CapturedSelection? (region + contents)
    ChestScanner.kt          region -> list<ChestContent>, filters chest blocks
    DoubleChestGrouper.kt    merges double-chest halves into one design
  naming/
    ChestNamer.kt            read/write the name of a chest block
  export/
    UiChest.kt               UiChest / UiRow / UiSlot + DesignJson config
    JsonExporter.kt          UiChest -> JSON string/file (atomic write; single ordering authority)
  commands/
    UiDesignerCommand.kt     /uidesigner save | reload | help (+ its message bodies)
    ChestEditCommand.kt      /chest-edit <name> | clear (+ its message bodies)
  util/
    Messages.kt              shared chat styling primitives (prefix, palette, styled)
```

## Seams

- **FAWE is isolated by laziness, not by an interface.** `ChestScanner` takes a
  `dev.rooster.region.Region` (which carries its `World`), never touching
  WorldEdit directly, so it is testable with fakes. `ChestCapture.capture(selectionOf, player)`
  is the production entry point that glues the two: `null` means the player has
  no usable selection; otherwise it returns a `CapturedSelection` carrying the
  `Region` and its `List<ChestContent>` (empty when the selection contained no
  chests). The selection lookup is a plain `(Player) -> Region?` lambda wired in
  `UiDesignerPlugin.onEnable`: FAWE is `compileOnly` and absent from MockBukkit's
  classpath, and the lambda body only touches the rooster-region worldedit
  extension types when invoked, so nothing FAWE-adjacent class-loads during
  `onEnable` or command registration. FAWE is also a hard `depend` in
  `plugin.yml`, so a real server refuses to enable without it.
- **`Region` is cuboid-only.** The library adapter reduces any FAWE selection
  (including non-cuboid `//hcyl`/`//poly`) to its min/max bounding box, so a
  chest inside the box but outside the actual selection is captured. World
  scoping lives in the library's `worldEditSelection()`, which returns null when
  the session's selection world is not the player's current world (the selection
  world survives a world change); the plugin's lambda only converts the returned
  selection to a `Region` via `toRegion(player.world)`. Shape fidelity is out of
  scope for the MVP. The library's
  `Region.blockAt(BlockPos)` backs the grouper's and the command's `BlockPos`-keyed
  lookups and the scanner's per-position block read.
- **`export`** is pure Kotlin: no Bukkit imports, and `BlockPos` comes from the
  library. It is the easiest place to get coverage and the place where format
  correctness lives.
- **`ChestContent` lives in `capture/`, not `export/`** (decided in 030): it holds
  Bukkit `ItemStack`s, so putting it in the pure `export` package would break
  that package's purity.
- **`ChestScanner`** is the only place that *scans* blocks (consuming the
  library's `Region.loadedBlockPositions`) and reads inventories, turning Bukkit
  `Block`/`Inventory` into the capture-side model
  (`ChestContent`); grouping may do targeted block lookups through the injected
  `Region` (Bukkit-coupled by design, testable with MockBukkit), and only
  `export` is fully Bukkit-free. It iterates `Region.loadedBlockPositions`
  (chunk-major, skipping unloaded chunks rather than forcing a chunk load) and
  resolves each position through `region.blockAt`, returning one entry per chest
  block in the library's enumeration order; `JsonExporter` normalises order. Each
  entry reads the block's own 27-slot inventory
  (`Chest.getBlockInventory`), not the shared double-chest inventory, so a
  double chest's halves stay independent until the grouper merges them.
  MockBukkit cannot form a real double chest and `ChestStateMock.getBlockInventory()`
  returns `getInventory()`, so tests only pin that adjacent chest blocks yield
  separate 27-slot entries; the `blockInventory` vs shared-`inventory`
  distinction still needs the dev server (040's holder-route tests fake the
  shared inventory).
- **Grouper input contract (040/060).** `ChestContent` stays `position + items`
  only. `DoubleChestGrouper` receives the `Region` alongside the
  `List<ChestContent>`, so it can reach each block via `region.blockAt(position)`
  (the library's member); 060 reads chest names from the same blocks with
  `ChestNamer.nameOf(block)`.
  `items` is expected to be a positive multiple of 9 (the scanner captures 27, a
  merged double 54); the grouper fails fast otherwise rather than emitting a
  malformed `rows`.
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
  `ChestNamer`, and `JsonExporter` without owning their logic, and injects a
  `(Player) -> Region?` selection provider, a `() -> UiDesignerConfig`
  provider, a reload action, and an exporter function so the pipeline is
  unit-testable without CommandAPI dispatch. `save` runs on the main thread;
  file IO stays synchronous for the MVP. This is a local tool, so there are no
  permission checks: `save` is player-only (a non-player sender is silently
  ignored), while `reload`, `help`, and the bare root accept any sender
  (console included). Both commands are built with the `rooster-commands` DSL
  (`literal`/`greedyString` nodes compiled to CommandAPI `CommandTree`s by the
  library's `command-api` backend); `Messages` owns the shared styling
  primitives (prefix, colour palette, `styled`), while each command file owns
  its own message bodies as internal top-level functions, kept testable from
  the same module. The bare-command behaviour of both roots is a root
  `onExecute { ... }` on the `command(...)` scope, so no direct
  `CommandTree.executes` remains: `/uidesigner` prints help for any sender,
  `/chest-edit` prints usage for a player and stays a silent no-op for console.
  CommandAPI suggests the registered subcommand literals automatically; the
  optional greedy `name` node also suggests `clear`, and the compiler dedupes
  that value against the sibling `clear` literal (`excludingLiterals`) so it is
  offered once (a `CommandTree` branches at the root, so the literal does not
  need to be a reserved sentinel name; any-casing, blank, and whitespace-padded
  "clear" still route through the greedy branch and `apply`'s
  trim/case-insensitive handling). `reloadConfiguration()` returns a
  `ReloadResult`, which `UiDesignerCommand` maps to its `ReloadOutcome`
  (reloaded, defaults, invalid output, or failed).
- **`JsonExporter`** is the single ordering authority: it sorts chests by
  canonical position and rows/slots by index. The grouper (040) merges double
  chests and must not re-sort; the exporter normalises order.

## Data flow

```
player + FAWE selection
        │  selection lookup lambda (UiDesignerPlugin)
        ▼
   Region? (min/max corners + world; null = no selection)
        │  ChestCapture.capture -> ChestScanner.scan
        ▼
  CapturedSelection?          (region + List<ChestContent>, one per chest *block*;
        │                      empty contents = selection had no chests)
        │  DoubleChestGrouper(selection.region, selection.contents)
        │  -> region.blockAt(position)
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
