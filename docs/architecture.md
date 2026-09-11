# Architecture

Single Gradle module. Packages under `dev.cypdashuhn.uidesigner`:

```
uidesigner/
  UiDesignerPlugin.kt        plugin entry point; wires config, commands, services
  config/
    UiDesignerConfig.kt      typed view over config.yml (output path, ...)
  model/
    UiChest.kt               UiChest / UiRow / UiSlot / BlockPos + DesignJson config
    ChestContent.kt          capture-side intermediate: chest position + inventory
  capture/
    SelectionSource.kt       interface: player -> region (seam for tests)
    FaweSelectionSource.kt   FAWE-backed implementation
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

- **`SelectionSource`** isolates FAWE. `ChestScanner` takes a region and a
  `World`, never touching WorldEdit directly, so scanning and grouping are
  testable with fakes.
- **`model`** and **`export`** are pure Kotlin: no Bukkit imports. They are the
  easiest place to get coverage and the place where format correctness lives.
- **`model` purity vs `ChestContent` is deferred to 030.** `ChestContent`
  (capture-side intermediate) currently sits in `model`; ticket 030 must decide
  whether it moves to `capture/` so `model` stays pure. Separately, `BlockPos`
  may move from `UiChest.kt` to `model/BlockPos.kt` once 030 adds its second
  consumer. Neither change is made here.
- **`ChestScanner`** turns Bukkit `Block`/`Inventory` into the capture-side
  model (`ChestContent`), keeping Bukkit types out of grouping and export.
- **`JsonExporter`** is the single ordering authority: it sorts chests by
  canonical position and rows/slots by index. The grouper (040) merges double
  chests and must not re-sort; the exporter normalises order.

## Data flow

```
player + FAWE selection
        │  SelectionSource
        ▼
   Region (min/max corners)
        │  ChestScanner
        ▼
 List<ChestContent>          (one per chest *block*, double halves separate)
        │  DoubleChestGrouper
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
