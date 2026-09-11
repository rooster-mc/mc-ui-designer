# MC UI Designer — Design

A Paper plugin that turns chests placed in a world into a JSON description of a
UI. You design the UI in-game (place chests, fill them, name them), select the
region with Fast Async WorldEdit, and the plugin exports the design to JSON.
That file is later handed to an agent to implement the real UI.

## Use case

As a developer I want to:

1. Join `localhost:25000` and get a Paper server with Fast Async WorldEdit and
   this plugin loaded.
2. Place chests and modify their contents freely.
3. Select the region containing the chests with FAWE.
4. Run a save command; the selection is written to JSON.
5. Have double chests saved as **one** entity, not two.
6. Use `/chest-edit` to name the chest block I am looking at.

## Scope

In scope (MVP):

- Bootable dev server with FAWE + plugin.
- Read FAWE selection, find chests, read their contents and names.
- Group double chests into a single entry.
- Export to JSON at a configurable path.
- Rename chests via `/chest-edit`.

Out of scope (backlog):

- Importing JSON back into a world.
- Editing arbitrary block types (barrels, shulkers, ...).
- Rendering the designed UI as a live inventory.
- Databases, localization frameworks, custom UI libraries.

## Decisions (taken without asking)

- **Region via `rooster-region`.** The duplicated region/WorldEdit-selection
  code was replaced by the sibling `rooster-region` library, consumed as a
  Gradle composite build (`:core` + `:worldedit`); this requires
  `../rooster-region` checked out beside this repository. The library targets
  Paper 1.21.4 / Java 21, but the `Region`/`Location`/`World` surface it exposes
  is stable on 26.2, so the version delta is accepted. Its `api` joml is
  excluded from the shaded jar because Paper supplies joml at runtime. The rest
  of the `rooster-*` stack is still not required: `rooster-core` (config
  helpers) and `rooster-commands` (CommandAPI wrapper) remain candidates to
  revisit before writing more commands.
- **Excluded:** `rooster-ui` (we render nothing), `rooster-sql` (no database),
  `rooster-localization` (plain Adventure components are enough).
- **Target:** Paper `26.2`, Java `25`, Kotlin `2.4.10`, matching the newest
  sibling (`extended-inventory`). Build plugins: `run-paper` `3.1.0`,
  `shadow` `8.3.6`, `plugin-yml` `0.6.0`, plus `kotlin("plugin.serialization")`
  and `org.jlleitschuh.gradle.ktlint`.
- **Serialization:** `kotlinx-serialization-json`, no Gson.
- **Commands:** `dev.jorel:commandapi-paper-shade:11.2.0`, shaded.
- **FAWE:** `com.fastasyncworldedit:FastAsyncWorldEdit-Core` +
  `-Bukkit` (`compileOnly`, pinned to `2.15.3`, with the IntellectualSites BOM
  for transitive deps) and the matching `FastAsyncWorldEdit-Paper-2.15.3.jar`
  auto-downloaded from GitHub releases by `run-paper` (Hangar's FAWE entries
  have no download URLs). FAWE is a hard `depend` in `plugin.yml`, so a server
  refuses to enable UiDesigner without it; the lazy delegating `SelectionSource`
  only exists to keep `FaweSelectionSource` from class-loading under MockBukkit.
- **Cuboid selections only.** A `Region` is a min/max bounding box, so a
  non-cuboid FAWE selection (`//hcyl`, `//poly`) captures every chest inside its
  bounding box, not only those inside the shape. Shape fidelity is backlog.
- **Testing:** JUnit 6 + MockBukkit (`org.mockbukkit.mockbukkit:mockbukkit-v26.2`).
  Pure logic (model, JSON, grouping math) stays Bukkit-free where possible.
- **Config:** `config.yml` read through Bukkit's `FileConfiguration`; the
  default output path is a Gradle property (`uiDesigner.defaultOutput`) filtered
  into the packaged `config.yml`.
- **Command root:** `/uidesigner` (alias `/uid`); the chest naming command is
  the separate top-level `/chest-edit`.
- **Permissions:** `uidesigner.save`, `uidesigner.reload`, and
  `uidesigner.chest-edit` default to op and are declared in `build.gradle.kts`
  (the command classes hold the node strings as constants). Each privileged
  executor checks its node before acting and replies with a prefixed denial
  message, so a missing permission is a clear chat line rather than a Brigadier
  parse failure. `help` and the bare `/uidesigner` root are permission-free.
- **Casing:** the JSON keys are normalised to `camelCase` and made valid JSON.
  See `docs/data-format.md` for the corrected schema and the delta from the
  original draft.
- **Chest names:** stored in the chest block's custom display name
  (`Nameable`), not a PersistentDataContainer. It is visible in the chest GUI,
  persists in block-entity NBT across restarts, and the exporter reads it as
  plain text. Both halves of a double chest are named/cleared together, and
  `nameOf` reads the first named half. If one half is in an unloaded chunk,
  Bukkit does not report a `DoubleChest`, so `/chest-edit` only names or clears
  the loaded half; 040 and the exporter likewise only see loaded halves.
  `/chest-edit clear` (case-insensitive, surrounding whitespace trimmed) is
  reserved for removal and blank input also clears, so a literal name "clear"
  is unreachable. Clearing an already-unnamed chest reports that there is
  nothing to clear. Bare `/chest-edit` prints a usage line instead of touching
  the targeted block. For an unlinked geometry-merged double (no `DoubleChest`
  holder), `nameOf` sees only the canonical half, so a name on the other half
  is not exported; carrying both merged positions out of the grouper is
  deferred.
- **One half selected:** a chest whose partner half is outside the selection is
  exported as a single 3-row chest from the selected half only. The selection
  is authoritative, so the grouper never reads an unselected half. A merged
  double reads the shared 54-slot inventory once and uses the lower half
  position as canonical.

## Naming

- Repository: `mc-ui-designer`
- Plugin name: `UiDesigner`
- Package / group: `dev.cypdashuhn.uidesigner` / `dev.cypdashuhn`
- Main class: `dev.cypdashuhn.uidesigner.UiDesignerPlugin`
