# MC UI Designer — Design

A Paper plugin that turns chests placed in a world into a JSON description of a
UI. You design the UI in-game (place chests, fill them, name them), select the
region with Fast Async WorldEdit, and the plugin exports the design to JSON.
That file is later handed to an agent to implement the real UI. The reverse loop
is also supported: a design file is scaffolded back into named chests, and world
copies are reconciled against it by name.

## Use case

As a developer I want to:

1. Join `localhost:25000` and get a Paper server with Fast Async WorldEdit and
   this plugin loaded.
2. Place chests and modify their contents freely.
3. Select the region containing the chests with FAWE.
4. Run a save command; the selection is written to JSON.
5. Have double chests saved as **one** entity, not two.
6. Use `/chest-edit` to name the chest block I am looking at.
7. Scaffold a saved JSON back into named, empty chests so I can inspect the
   layout in-world.
8. Run `/uidesigner status` to see which chests drift from the file, and
   `/uidesigner sync` to apply the file's contents to a placed copy.

## Scope

In scope (MVP):

- Bootable dev server with FAWE + plugin.
- Read FAWE selection, find chests, read their contents and names.
- Group double chests into a single entry.
- Export to JSON at a configurable path.
- Rename chests via `/chest-edit`.
- Scaffold a design JSON into named, empty chests (structure only), using names
  as identity.
- Reconcile a placed copy's contents against the JSON with `/uidesigner status`
  and `/uidesigner sync`, reporting drift.

Out of scope (backlog):

- Editing arbitrary block types (barrels, shulkers, ...).
- Rendering the designed UI as a live inventory.
- Databases, localization frameworks, custom UI libraries.
- Storing world positions/rotation in the JSON (the world owns layout).

## Decisions (taken without asking)

- **Region via `rooster-region`.** The duplicated region/WorldEdit-selection
  code was replaced by the sibling `rooster-region` library, consumed as a
  Gradle composite build (`:core` + `:worldedit`); this requires
  `../rooster-region` checked out beside this repository. The library targets
  Paper 1.21.4 / Java 21, but the `Region`/`Location`/`World` surface it exposes
  is stable on 26.2, so the version delta is accepted. Its `api` joml is
  excluded from the shaded jar because Paper supplies joml at runtime. The
  command layer uses the sibling `rooster-commands` library, also consumed as a
  Gradle composite build; this requires `../rooster-commands` checked out beside
  this repository, which in turn builds against `../rooster-core` (a transitive
  checkout requirement, guarded by a `check(...)` in `settings.gradle.kts`).
  `rooster-commands`' `command-api` backend compiles the DSL trees to CommandAPI
  `CommandTree`s (CommandAPI `commandapi-paper-shade` `11.2.0`, shaded beneath).
  `rooster-core` itself is only pulled in transitively; this plugin does not use
  its service hooks.
- **Excluded:** `rooster-ui` (we render nothing), `rooster-sql` (no database),
  `rooster-localization` (plain Adventure components are enough).
- **Target:** Paper `26.2`, Java `25`, Kotlin `2.4.10`, matching the newest
  sibling (`extended-inventory`). Build plugins: `run-paper` `3.1.0`,
  `shadow` `8.3.6`, `plugin-yml` `0.6.0`, plus `kotlin("plugin.serialization")`
  and `org.jlleitschuh.gradle.ktlint`.
- **Serialization:** `kotlinx-serialization-json`, no Gson.
- **Commands:** the command DSL comes from the sibling `rooster-commands`
  library (`literal`/`greedyString` trees compiled by its `command-api` backend
  into CommandAPI `CommandTree`s); `dev.jorel:commandapi-paper-shade:11.2.0`
  is shaded beneath it.
- **FAWE:** `com.fastasyncworldedit:FastAsyncWorldEdit-Core` +
  `-Bukkit` (`compileOnly`, pinned to `2.15.3`, with the IntellectualSites BOM
  for transitive deps) and the matching `FastAsyncWorldEdit-Paper-2.15.3.jar`
  auto-downloaded from GitHub releases by `run-paper` (Hangar's FAWE entries
  have no download URLs). FAWE is a hard `depend` in `plugin.yml`, so a server
  refuses to enable UiDesigner without it; the selection lookup is a plain
  `(Player) -> Region?` lambda whose body only touches the rooster-region
  worldedit extension types when invoked (`UiDesignerPlugin.worldEditSelectionOf`),
  so nothing FAWE-adjacent class-loads under MockBukkit (see
  `docs/architecture.md`, Seams).
- **Cuboid selections only.** A `Region` is a min/max bounding box, so a
  non-cuboid FAWE selection (`//hcyl`, `//poly`) captures every chest inside its
  bounding box, not only those inside the shape. Shape fidelity is backlog.
- **Testing:** JUnit 6 + MockBukkit (`org.mockbukkit.mockbukkit:mockbukkit-v26.2`).
  Pure logic (JSON export/ordering) stays Bukkit-free where possible.
- **Config:** `config.yml` read through Bukkit's `FileConfiguration`; the
  default output path is a Gradle property (`uiDesigner.defaultOutput`) filtered
  into the packaged `config.yml`.
- **Command root:** `/uidesigner` (alias `/uid`); the chest naming command is
  the separate top-level `/chest-edit`.
- **No permissions.** This is a local tool, so the permission concept was
  dropped entirely: no permission nodes are declared or checked. `save` and
  both `/chest-edit` executors are player-only; a non-player sender (console)
  is silently ignored rather than answered, uniformly across bare and
  argument-bearing invocations. `reload`, `help`, and the bare `/uidesigner`
  root accept any sender.
- **Casing:** the JSON keys are normalised to `camelCase` and made valid JSON.
  See `docs/data-format.md` for the corrected schema and the delta from the
  original draft.
- **Chest names:** stored in the chest block's custom display name
  (`Nameable`), not a PersistentDataContainer. It is visible in the chest GUI,
  persists in block-entity NBT across restarts, and the exporter reads it as
  plain text. Both halves of a double chest are named together, and `nameOf`
  reads the first named half. If one half is in an unloaded chunk, Bukkit does
  not report a `DoubleChest`, so `/chest-edit` only names the loaded half; the
  exporter likewise only sees loaded halves and, since 140, fails closed when a
  double chest's other half is missing (see below). Bare `/chest-edit` prints a
  usage line instead of touching the targeted block; blank input is rejected
  rather than clearing. For an unlinked geometry-merged double (no `DoubleChest`
  holder), `nameOf` sees only the canonical half, so a name on the other half is
  not exported; carrying both merged positions out of the grouper is deferred.
- **One half selected:** a chest whose partner half is missing from the
  selection is a clipped double, so the export fails closed: it reports the
  captured and partner blocks and writes no file. The selection is
  authoritative, so the grouper never reads an unselected half. A merged double
  reads the shared 54-slot inventory once and uses the lower half position as
  canonical.
- **Chest materials:** `CHEST`, `TRAPPED_CHEST`, and the copper chest variants
  (`Tag.COPPER_CHESTS`) are all treated as chests by the scanner and
  `/chest-edit`; every one maps to `Chest`/`ChestData` and can form a double.
- **Names are the identity.** Every exported chest must have a non-blank name,
  unique across the file (surrounding whitespace ignored, comparison
  case-insensitive, original casing preserved). The save path validates names
  with `validateForExport` and fails closed before invoking the exporter,
  writing no file and listing unnamed chest positions and duplicate
  spellings+positions. `UiChest.name` is non-null. This makes each entry
  referenceable by the importer, which joins world chests to file entries by
  name.
- **`/chest-edit clear` is removed.** Names are mandatory, so clearing only
  produced an unsavable chest. `clear` is now an ordinary name and blank input
  is rejected; this also drops the reserved-word sentinel from the command tree.
- **The importer is declarative reconcile.** The file is desired state and the
  world is observed state; `name` is the join key. `scaffold` materializes
  structure (named, empty chests with correct `rows`), and because the format
  carries no positions the world owns layout. `status` reports drift
  (in-sync/updated/missing/orphan/unjoinable) and `sync` applies file contents
  to matched chests. Orphans are reported, never deleted; structure drift is
  reported, not auto-repaired.
- **Reconcile joins by normalised name and sync never repairs structure (170).**
  `export/Reconcile` is pure (no Bukkit). It joins the file's `List<UiChest>` to
  the world's by `trim().lowercase()` name and yields one classification per
  chest: `InSync`, `Updated` (with a `ChestDrift` of `rowsDiffer`, `nameDiffer`,
  and per-row/slot differences), `Missing` (file-only), `Orphan` (world-only),
  or `Unjoinable` (an unnamed world chest, or one whose normalised name is
  shared with another world chest — both duplicates are unjoinable and any
  matching file entry is `Missing`). Content comparison normalises to
  `(row, slot) -> (material id, item custom name)` with blank names treated as
  absent, so slot order and empty slots never register as drift. Because drift
  is measured against the file's exact name, a chest matched only
  case-insensitively is `Updated` (`nameDiffer`), not `InSync`.
  `status` renders the report and writes nothing. `sync` writes the file's name
  to every matched chest and replaces its contents (a full-slot write, so
  removed slots are cleared). On a `rowsDiffer` mismatch `sync` writes the name
  but **skips that chest's contents** and reports the structure drift: a single
  27-slot chest cannot hold a 6-row design, and writing a 6-row design through
  an unlinked double's 27-slot block inventory would drop half the file, so the
  safe move is to leave the shape alone and flag it. The chest stays drifted
  until the human fixes the world. `sync` never resizes, creates, or moves a
  chest, and orphans are never written. Both commands are player-only and reuse
  `scaffold`'s file resolution and IO/parse/invalid-design reporting.
- **Scaffold reads purely and places thinly.** `JsonImporter` lives in the pure
  `export` package: it deserializes the file and validates `rows` 1..6, row/slot
  ranges, item ids, and non-blank unique names (trimmed, case-insensitive,
  original casing preserved), failing closed with the file and offending entry.
  An empty design is rejected the same way, so `scaffold` can never report a
  green no-op. Material resolution is a Bukkit concern, so the reader takes a
  `(String) -> Boolean` matcher and the command passes `place/MaterialResolver`
  (`Material.matchMaterial`). `place/ScaffoldPlacer` owns the physical layout: a
  single row across the player's view at the anchor's Y, every chest facing the
  player, `rows == 6` as a linked `RIGHT`/`LEFT` double and every other row
  count as a single (a world chest only has 3 or 6 rows, so 1/2/4/5 are a
  structural approximation). Before placing anything it pre-checks every target
  (air or `Block.isReplaceable`, and not inside the player) and aborts
  atomically, reporting the count and whether the first obstruction is the
  player or a solid block so the message can say how to recover. `scaffold` is
  not idempotent yet; re-running places a fresh row (repair is deferred).

## Naming

- Repository: `mc-ui-designer`
- Plugin name: `UiDesigner`
- Package / group: `dev.cypdashuhn.uidesigner` / `dev.cypdashuhn`
- Main class: `dev.cypdashuhn.uidesigner.UiDesignerPlugin`
