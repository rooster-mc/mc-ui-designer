# Architecture

Single Gradle module. Packages under `dev.cypdashuhn.uidesigner`:

```
uidesigner/
  UiDesignerPlugin.kt        plugin entry point; wires config, commands, services
  config/
    UiDesignerConfig.kt      typed view over config.yml (output path, ...)
  model/
    UiChest.kt               UiChest / UiRow / UiSlot + Json config
    ChestContent.kt          capture-side intermediate: chest position + inventory
  capture/
    SelectionSource.kt       interface: player -> region (seam for tests)
    FaweSelectionSource.kt   FAWE-backed implementation
    ChestScanner.kt          region -> list<ChestContent>, filters chest blocks
    DoubleChestGrouper.kt    merges double-chest halves into one design
  naming/
    ChestNamer.kt            read/write the name of a chest block
  export/
    JsonExporter.kt          model -> JSON string/file (atomic write)
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
- **`ChestScanner`** turns Bukkit `Block`/`Inventory` into the capture-side
  model (`ChestContent`), keeping Bukkit types out of grouping and export.

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
 List<UiChest>               (double chests merged, ordered)
        │  JsonExporter
        ▼
        JSON file (config.outputFile)
```

## Conventions

- Kotlin, `kotlinx-serialization`, CommandAPI, Adventure components.
- No comments in code unless they explain non-obvious *why*.
- Ktlint formatting via `.editorconfig` (max line 100).
- Tests use backticked sentence names and JUnit 5.
