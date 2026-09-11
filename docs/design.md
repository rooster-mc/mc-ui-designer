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

- **Standalone plugin.** We do not depend on the `rooster-*` libraries. They are
  a style reference, not a required base; pulling them in couples us to older
  Paper/JDK toolchains. If the house stack is wanted later, `rooster-core`
  (config/region helpers) and `rooster-commands` (CommandAPI wrapper) are the
  two candidates — revisit before writing more commands.
- **Excluded:** `rooster-ui` (we render nothing), `rooster-sql` (no database),
  `rooster-localization` (plain Adventure components are enough).
- **Target:** Paper `26.2`, Java `25`, Kotlin `2.4.10`, matching the newest
  sibling (`extended-inventory`). Build plugins: `run-paper` `3.0.2`,
  `shadow` `8.3.6`, `plugin-yml` `0.6.0`.
- **Serialization:** `kotlinx-serialization-json`, no Gson.
- **Commands:** `dev.jorel:commandapi-paper-shade:11.2.0`, shaded.
- **FAWE:** `com.fastasyncworldedit:FastAsyncWorldEdit-Core` +
  `-Bukkit` (`compileOnly`, via the IntellectualSites BOM) and auto-downloaded
  into the dev server by `run-paper`.
- **Testing:** JUnit 5 + MockBukkit (`org.mockbukkit.mockbukkit:mockbukkit-v26.2`).
  Pure logic (model, JSON, grouping math) stays Bukkit-free where possible.
- **Config:** `config.yml` read through Bukkit's `FileConfiguration`; the
  default output path is a Gradle property (`uiDesigner.defaultOutput`) filtered
  into the packaged `config.yml`.
- **Command root:** `/uidesigner` (alias `/uid`); the chest naming command is
  the separate top-level `/chest-edit`.
- **Casing:** the JSON keys are normalised to `camelCase` and made valid JSON.
  See `docs/data-format.md` for the corrected schema and the delta from the
  original draft.

## Naming

- Repository: `mc-ui-designer`
- Plugin name: `UiDesigner`
- Package / group: `dev.cypdashuhn.uidesigner` / `dev.cypdashuhn`
- Main class: `dev.cypdashuhn.uidesigner.UiDesignerPlugin`
