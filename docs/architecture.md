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
    DoubleChestGrouper.kt    merges double-chest halves; reports clipped halves
  naming/
    ChestNamer.kt            read/write the name of a chest block
  export/
    UiChest.kt               UiChest / UiRow / UiSlot + DesignJson config
    JsonExporter.kt          UiChest -> JSON string/file (atomic write; single ordering authority)
    JsonImporter.kt          JSON file -> List<UiChest> + schema validation (pure, no Bukkit)
    ExportValidation.kt      pure name validation: unnamed positions, duplicate groups
  place/
    MaterialResolver.kt      item id -> Boolean (reader's injected matcher; production = Material.matchMaterial != null)
    ScaffoldPlacer.kt        UiChest list + anchor -> placed chest blocks (atomic pre-check)
  commands/
    UiDesignerCommand.kt     /uidesigner save | scaffold | reload | help (+ its message bodies)
    ChestEditCommand.kt      /chest-edit <name> (+ its message bodies)
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
- **Clipped double chest is fatal (140).** A chest is knowably half of a double
  when its `inventory.holder` is a `DoubleChest` (partner positions from
  `leftSide`/`rightSide`) or its block data `type` is `LEFT`/`RIGHT` (a lone
  chest is `SINGLE`). If that partner is absent from `contents`,
  `DoubleChestGrouper.group` returns a `GroupResult` carrying `chests` plus
  `clipped: List<ClippedHalf>` (the captured `position`, the `partner`, and
  whether the partner lies inside the selection bounds) instead of emitting a
  truncated 3-row single. Consumed positions still keep a merged double's halves
  from being emitted twice. The grouper only classifies a half as clipped when
  the partner is genuinely absent from `byPosition`: a neighbour that is present
  but mismatched or already consumed stays a single, as before. `clipped` is
  non-empty only when the selection is unusable, so `UiDesignerCommand` maps it
  to `SaveOutcome.ClippedChests` and returns before the exporter, and no file is
  written. `ClippedHalf.partnerInsideSelection` is false when the partner lies
  outside the region's min/max bounds, and true when it lies inside them but was
  skipped (`ChestScanner` only visits `loadedBlockPositions`, so the chunk was
  unloaded); the command words those two cases distinctly for the player. Nothing
  reads the world at the absent partner position, so classification never forces
  an unloaded chunk to load. Chests are `CHEST`, `TRAPPED_CHEST`, and the copper
  chest variants (`Tag.COPPER_CHESTS`), so a clipped copper double is detected
  the same way.
- **Grouper returns `UiChest` with a blank placeholder name and does not order.**
  `UiChest.name` is non-null, so the grouper emits `name = ""`; `UiDesignerCommand`
  populates names via `ChestNamer.nameOf` at each canonical position (mapping a
  missing name back to `""`), and `JsonExporter` remains the ordering authority.
- **`validateForExport(chests)` is the name gate (150).** It lives in the pure
  `export` package and returns an `ExportValidation` carrying the positions of
  unnamed chests and the `DuplicateNameGroup`s, where each group lists every
  `NamedPosition` (original spelling + position) that normalised to the same key.
  A name is blank when it trims to empty; names are grouped by
  `trim().lowercase()`. `UiDesignerCommand` runs it on the named, grouped chests
  before resolving the output path and returns a single `InvalidNames` outcome
  (no export) carrying both lists, so an unnamed-and-duplicate selection is
  reported in one message. `JsonExporter` no longer strips blank chest names;
  slot-name blank-stripping is unchanged.
- **`JsonImporter` keeps `export` Bukkit-free behind a matcher seam (160).**
  `read(file, materialMatcher: (String) -> Boolean)` reads the file, decodes it
  with `DesignJson`, and validates order-independently: each name is non-blank
  and unique under `trim().lowercase()` (original spelling preserved), `rows` is
  in 1..6, each row is in `1..rows`, each slot is in 1..9, and each item id
  satisfies the injected matcher; the first violation throws
  `InvalidDesignException` naming the file and the entry (index, name, row,
  slot). An empty design is rejected up front ("the design contains no
  chests"), so a zero-entry file can never reach the placer and report a green
  no-op. `InvalidDesignException` is not a `Path`-only concern, so the reader
  never names a Bukkit type. The production matcher is
  `place/MaterialResolver.isKnown` (`Material.matchMaterial(id) != null`); the
  command injects it as the reader default, and tests pass a set predicate. IO
  failures surface as the underlying `IOException` (a missing file is
  `NoSuchFileException`), so the command can separate them from parse failures.
  `InvalidDesignException` carries `file` and `detail` separately; the command
  renders the resolved file once with the detail, and translates a kotlinx
  `SerializationException` to a fixed "not valid JSON" phrase rather than
  echoing parser offsets.
- **`place/ScaffoldPlacer` owns layout and the atomic pre-check (160).** It takes
  a `World`, `List<UiChest>`, anchor `BlockPos`, view `BlockFace`, and an
  `occupied: (Block) -> Boolean` predicate (default `{ false }`). The row runs
  across the view along `view.rotateYClockwise()`, every chest faces
  `view.oppositeFace`, and a 6-row entry becomes a `RIGHT` block at the lower
  offset plus a `LEFT` block one step along the row; the earlier position is
  `RIGHT` so the pair links under vanilla's clockwise/counter-clockwise
  `getConnectedDirection` rule (the same rule the grouper's fallback uses). Any
  other row count is a single. Replaceability is `target.type.isAir ||
  target.isReplaceable` (`Block.isReplaceable` = Bukkit `Tag.REPLACEABLE`), and
  a target inside the player's bounding box is also blocked; the first blocked
  entry carries whether it was the player or a solid block so the command can
  word the recovery differently. The whole plan is checked before any write; on
  obstruction nothing is placed. Blocks are placed empty first (type plus
  `Chest` data), then named with `ChestNamer.setName`, which names both halves
  of a linked double. The `Player` entry point resolves the anchor as the
  targeted block within reach, falling back to the block in front of the
  player's feet, and derives view from `Player.facing`; MockBukkit cannot form a
  real `DoubleChest` and its `getTargetBlockExact`/`getFacing` throw, so the
  layout and pairing are unit-tested through the explicit `World`/anchor/view
  overload and the real double is a manual-test entry.
- **`UiDesignerConfig` owns scaffold path resolution.** `resolvePath(raw)` is the
  single data-folder-normalising rule (relative paths join the data folder,
  absolute paths pass through and are normalised); `outputFile` uses it, and the
  command delegates to it rather than re-branching. `jsonFiles()` lists the
  `.json` files there for tab completion (empty when the folder is missing or
  unreadable). `scaffold`'s optional greedy `file` argument uses the configured
  `output-file` when omitted or blank, and `resolvePath(raw)` otherwise, so a
  suggested name round-trips.
- **Chest predicate duplication.** `ChestScanner` and `ChestNamer` each define
  their own chest-material check (`CHEST`/`TRAPPED_CHEST` plus
  `Tag.COPPER_CHESTS`, whose eight variants are also `Chest`/`ChestData` on this
  Paper version); this is a deliberate carry-over until a third consumer appears,
  then extract one shared `isChest`.
- **`UiDesignerCommand`** is the integration point for `/uidesigner save |
  scaffold | reload | help`. It composes `ChestCapture`, `DoubleChestGrouper`,
  `ChestNamer`, and `JsonExporter` without owning their logic, and injects a
  `(Player) -> Region?` selection provider, a `() -> UiDesignerConfig`
  provider, a reload action, an exporter function, a
  `(Path) -> List<UiChest>` importer, and a
  `(Player, List<UiChest>) -> PlacementResult` placer, so the pipeline is
  unit-testable without CommandAPI dispatch. `save` runs on the main thread;
  file IO stays synchronous for the MVP. This is a local tool, so there are no
  permission checks: `save` and `scaffold` are player-only (a non-player sender
  is silently ignored), while `reload`, `help`, and the bare root accept any
  sender (console included). Both commands are built with the `rooster-commands`
  DSL (`literal`/`greedyString` nodes compiled to CommandAPI `CommandTree`s by
  the library's `command-api` backend); `Messages` owns the shared styling
  primitives (prefix, colour palette, `styled`), while each command file owns
  its own message bodies as internal top-level functions, kept testable from
  the same module. The bare-command behaviour of both roots is a root
  `onExecute { ... }` on the `command(...)` scope, so no direct
  `CommandTree.executes` remains: `/uidesigner` prints help for any sender,
  `/chest-edit` prints usage for a player and stays a silent no-op for console.
  `/chest-edit` has a single optional greedy `name` node (no `clear` literal and
  no custom suggestion list); a blank or whitespace-only argument prints the
  usage line and leaves the chest untouched, while `clear` is stored as an
  ordinary name. `save` validates the named, grouped chests with
  `validateForExport` and maps any failure to `InvalidNames` before touching the
  output path. `scaffold` has a literal `onExecute` (the no-arg default) plus an
  optional greedy `file` child whose suggestion list is the data folder's
  `.json` files; it maps `PlacementResult`/import failures onto its
  `ScaffoldOutcome` set (`Placed`, `NoTarget`, `ParseFailure`, `Obstructed`,
  `IoFailure`). `reloadConfiguration()` returns a `ReloadResult`, which
  `UiDesignerCommand` maps to its `ReloadOutcome` (reloaded, defaults, invalid
  output, or failed).
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
        │  DoubleChestGrouper.group(selection.region, selection.contents)
        │  -> region.blockAt(position)
        ▼
  GroupResult                 (chests: List<UiChest>, names blank; clipped: List<ClippedHalf>)
        │  UiDesignerCommand: clipped -> ClippedChests (no export)
        │                     else ChestNamer.nameOf at each canonical position
        ▼
  List<UiChest>               (names populated)
        │  validateForExport -> InvalidNames (no export)
        ▼
  List<UiChest>               (named and unique)
        │  JsonExporter
        ▼
        JSON file (config.outputFile)
```

Scaffold is the reverse direction:

```
  file argument (or config.outputFile) + player
        │  UiDesignerCommand.scaffold -> config.resolvePath / outputFile
        ▼
        Path (blank/absent uses config.outputFile)
        │  JsonImporter.read(path, MaterialResolver.isKnown)
        │    InvalidDesignException / SerializationException -> ParseFailure (no placement)
        │    NoSuchFileException / IOException -> IoFailure (no placement)
        ▼
  List<UiChest>               (validated: non-empty, rows 1..6, rows/slots, items, names)
        │  ScaffoldPlacer.place(player, chests)
        │    anchor = targeted block ?: feet + view direction
        │    layout = one row along view.rotateYClockwise(); 6 rows = RIGHT/LEFT
        │    pre-check every target (air/replaceable, not player-occupied)
        │    place all empty chests, then ChestNamer.setName each entry
        ▼
  PlacementResult             (Placed(count) | Obstructed(count, first, firstIsPlayer) | NoTarget)
        │  UiDesignerCommand maps import failures and PlacementResult
        ▼
        ScaffoldOutcome (Placed | NoTarget | ParseFailure | Obstructed | IoFailure)
```

## Conventions

- Kotlin, `kotlinx-serialization`, CommandAPI, Adventure components.
- No comments in code unless they explain non-obvious *why*.
- Ktlint formatting via `.editorconfig` (max line 100).
- Tests use backticked sentence names and JUnit 6.
