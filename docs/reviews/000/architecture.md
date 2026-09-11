# Architecture review — 000 (Project setup and dev server)

## Round 1

### Verdict
Ship with fixes. The scaffold matches the target architecture (single Kotlin/JVM
module, package root `dev.cypdashuhn.uidesigner`, `compileOnly` Paper/FAWE,
`implementation` for shaded deps, MockBukkit test source set) and every later
ticket can be added without touching this shape. Two things should be fixed now:
the `plugin.yml` command declarations pre-empt CommandAPI ownership, and the
design/status docs were not updated with the build. One version-coherence issue
should be resolved before ticket 030 compiles against FAWE.

### Issues

#### 1. `plugin.yml` command declarations pre-empt CommandAPI command ownership (severity: medium) — fix now
- Location: `build.gradle.kts:54-62` (generated `build/resources/main/plugin.yml`), `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:5-13`
- Problem: The build registers `/uidesigner` (+ alias `/uid`) and `/chest-edit`
  with Paper via plugin-yml, but no code owns them yet. `docs/design.md:50`
  states commands are `commandapi-paper-shade` and `docs/architecture.md:23-24`
  puts them in `commands/UiDesignerCommand.kt` / `ChestEditCommand.kt`. When a
  later ticket calls `CommandAPICommand("uidesigner").register(plugin)`, Bukkit
  already owns that literal from `plugin.yml`; CommandAPI explicitly detects
  this and logs `"Plugin command /%s is registered by Bukkit (%s). Did you
  forget to remove this from your plugin.yml file?"`
  (`commandapi-paper-shade-11.2.0-sources.jar`,
  `dev/jorel/commandapi/CommandAPIBukkit.java:296-318`). CommandAPI's own
  conflict tracking only sees commands registered *through* CommandAPI
  (`CommandAPIHandler.java:595-600`), so the two registration paths do not
  conflict-detect each other — the result is a shadowed/merged Brigadier node
  that is hard to diagnose. It also creates a second source of truth for command
  name/alias/description (here vs. the command classes) that will drift. The
  sibling `extended-inventory/build.gradle.kts:75` deliberately leaves
  `commands { }` empty and lets CommandAPI own registration.
- Suggested fix: delete the `commands { }` block (leave it empty) and let
  tickets 050/060 register both commands through CommandAPI, keeping
  descriptions in the command classes. Do not re-add them in those tickets. If
  the declarations are kept for some reason, ticket 050/060 must implement
  `onCommand` instead and document why CommandAPI is not used — but that
  contradicts `docs/design.md:50`.

#### 2. Design/status docs were not updated with the build (severity: low) — fix now
- Location: `docs/design.md:47-48,52-54`, `AGENTS.md:87-90` vs `build.gradle.kts:7,10,32,39,88-93`
- Problem: `AGENTS.md` and `docs/fcp.md:42-46` require docs to be updated in the
  same commit that invalidates them. The build now uses run-paper `3.1.0` (docs
  pin `3.0.2`), JUnit `6.1.3` (docs say "JUnit 5"), and a GitHub FAWE download
  (docs only say "auto-downloaded ... by run-paper"); it also adds the
  `kotlin("plugin.serialization")` and ktlint plugins, which the "Build plugins"
  decision does not list. `AGENTS.md:89` still says "No code has been written
  yet", which is now false. These are the architecture contract and the first
  thing the next ticket's implementor reads, so stale versions send them to the
  wrong dependency coordinates.
- Suggested fix: update `docs/design.md` decisions (run-paper 3.1.0, JUnit 6,
  GitHub FAWE, serialization/ktlint plugins) and `AGENTS.md` "Current status" in
  this ticket. One line each is enough; the rationale for each deviation already
  exists in the handoff.

#### 3. FAWE has two sources of truth: compile 2.15.0 vs run 2.15.3 (severity: low) — fix now or carry into 030
- Location: `build.gradle.kts:35-37` vs `build.gradle.kts:88-93`
- Problem: `bom-newest:1.56` resolves `FastAsyncWorldEdit-Core/Bukkit` `2.15.0`
  (confirmed in the Gradle cache and the BOM POM), while `runServer` downloads
  `2.15.3`. Ticket 030 is the first to actually call FAWE APIs
  (`WorldEdit.getInstance().sessionManager`, etc.) and explicitly notes "Verify
  exact API against the FAWE version resolved by the BOM" — so we would compile
  against one patch and run another. Any 2.15.1–2.15.3 API addition used in 030
  would pass the dev server and fail the build, and bumping one side without the
  other is easy to miss.
- Suggested fix: drive both from one version — either pin the BOM to a release
  resolving 2.15.3, or download `2.15.0` from GitHub (or a shared
  `faweVersion` constant). Cheap now; painful to discover in 030.

### Non-issues
- **Package layout / naming.** `dev.cypdashuhn.uidesigner` root,
  `UiDesignerPlugin.kt`, mirrored `src/test/kotlin`, and `config.yml` in
  `src/main/resources` match `docs/architecture.md:3-27` and
  `docs/design.md:67-70`. No `config/`, `model/`, `capture/`, `commands/`, ...
  packages exist yet, correctly.
- **Boundaries are clean for a setup ticket.** Paper and FAWE are `compileOnly`
  (`build.gradle.kts:30,35-37`), CommandAPI/serialization are `implementation`
  and shaded (`:32-33`, `:97-99`). Nothing forces Bukkit into the future
  `model`/`export`, and FAWE stays behind the planned `SelectionSource`. Adding
  the serialization compiler plugin at `:6` is not over-generalisation: it is a
  build-time prerequisite for ticket 020's `@Serializable` types, not a
  speculative abstraction, and it avoids a build edit there.
- **`UiDesignerPlugin` is the right thin entry point.** Only
  `onEnable`/`onDisable` logging (`UiDesignerPlugin.kt:5-13`) is correct for
  000; config wiring is ticket 010 and command registration is 050/060. The
  `docs/architecture.md:7` description ("wires config, commands, services")
  describes the eventual state, not this ticket. No `saveDefaultConfig()` yet is
  fine.
- **Extendable to the next tickets without a rewrite.** Walk-through:
  *model/export* (020) needs no build change — serialization plugin/runtime are
  already present and the module is pure Kotlin; *capture* (030) has FAWE
  `compileOnly` and the `SelectionSource` seam reserved; *commands* (050/060)
  have CommandAPI shaded; *config* (010) has `processResources` filtering
  (`:65-69`) feeding `config.yml` from `uiDesigner.defaultOutput`; *MockBukkit*
  tests already run under `useJUnitPlatform()` (`:105-107`). Adding container
  types (barrels/shulkers) later is localized to `ChestScanner`; adding a JSON
  reader or another output format is a new class next to `JsonExporter`, not a
  build change. Nothing here over-generalises for those.
- **Run/dev-server wiring.** `prepareRunServer` (`:71-82`) plus `/run` in
  `.gitignore` keeps generated `server.properties`/`eula.txt` out of git, as the
  ticket notes require. `mergeServiceFiles()` (`:97-99`) is needed for the
  shaded CommandAPI/Adventure service loaders.
- **`paper-api` main `build.123` / test `build.111`.** A deliberate, low-risk
  mismatch that mirrors the sibling (`extended-inventory` uses 112/111) and is
  driven by MockBukkit 4.116.1's compile target. Carry over: if a test ever
  compiles against a Paper API removed in 111, revisit; not worth churn now.
- **`just build` = `./gradlew build shadowJar`.** Redundant since `build`
  depends on `shadowJar` (`:101-103`), but harmless.
