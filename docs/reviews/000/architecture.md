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

## Round 2

### Verdict
Ship with two low-severity follow-ups. All three Round-1 architecture issues are
resolved: `plugin.yml` no longer declares commands, the design/architecture/status
docs now match the build, and FAWE compile and run are driven from one
`faweVersion` constant. The package root, single-module shape, `compileOnly`
boundaries and extendability to 010–070 are intact, and nothing is
over-generalised. The remaining concern is that the retained IntellectualSites
BOM is still a latent second FAWE version (`2.15.0`) and ticket 030's note still
names the BOM as the API authority; `AGENTS.md:25` also still says JUnit 5.

### Issues

#### 1. BOM still pins FAWE `2.15.0`; ticket 030's note points at the BOM, not the pin (severity: low) — fix now
- Location: `build.gradle.kts:17,38-40`, `docs/tasks/030-selection-capture.md:41`,
  `docs/design.md:52-56`
- Problem: The single `faweVersion = "2.15.3"` constant (`:17`) feeds both the
  `compileOnly` Core/Bukkit pins (`:39-40`) and the GitHub download (`:91-92`),
  which does close the Round-1 compile/run drift. But the BOM platform
  (`:38`) still declares `FastAsyncWorldEdit-{Core,Bukkit}` at `2.15.0` in its
  `dependencyManagement`, and the explicit pin only wins through Gradle's
  highest-version conflict resolution. That is a non-obvious dual mechanism: a
  reader who removes the explicit version "to trust the BOM" silently drops to
  `2.15.0`, and ticket 030's note (`030-selection-capture.md:41`) still says
  "Verify exact API against the FAWE version resolved by the BOM" — which is now
  the *wrong* authority (2.15.0) for the classpath it compiles against (2.15.3).
  This is the one place the fix could bite 030, and it is a wording/comment fix,
  not a resolution bug (the correctness reviewer confirmed only `2_15_3`
  artifacts are resolved).
- Suggested fix: update the 030 note to "against the shared `faweVersion`
  (`2.15.3`)" and add one why-comment above the BOM line stating that it
  supplies transitive versions only and the explicit pin overrides its FAWE
  `2.15.0`. No dependency rewrite is needed; dropping the BOM entirely is a
  larger, unnecessary change.

#### 2. `AGENTS.md:25` still says "JUnit 5 + MockBukkit" (severity: low) — fix now
- Location: `AGENTS.md:25` vs `build.gradle.kts:42`, `docs/design.md:57`,
  `docs/architecture.md:62`
- Problem: This is the same doc-drift class as Round-1 issue 2. Round 1 updated
  `docs/design.md` and `docs/architecture.md` and the `AGENTS.md` "Current
  status" block, but the Stack line was missed. `AGENTS.md` is the first file
  every agent reads, and it now contradicts the build and the other two docs.
  (The correctness reviewer flagged the same line in its Round 2; recording it
  here as an architecture-contract issue because Round 1 owned the doc-consistency
  finding.)
- Suggested fix: change `AGENTS.md:25` to "JUnit 6 + MockBukkit".

### Non-issues (Round-1 verification and re-scan)

- **Round-1 #1 (`plugin.yml` command declarations) genuinely fixed.** The
  `bukkit { }` block (`build.gradle.kts:53-57`) no longer has `commands`, and the
  generated `build/resources/main/plugin.yml` and
  `build/generated/plugin-yml/Bukkit/plugin.yml` contain only
  name/version/main/api-version. No source or doc references the removed
  declarations. CommandAPI remains the single owner for tickets 050/060, and the
  deviation is recorded at `docs/tasks/000-project-setup.md:64-66`.
- **Round-1 #2 (stale design/status docs) fixed except the one line above.**
  `docs/design.md:46-57` now names run-paper `3.1.0`, JUnit 6, the GitHub FAWE
  download and the serialization/ktlint plugins; `docs/architecture.md:62` says
  JUnit 6; `AGENTS.md:85-90` describes the shipped setup. `docs/design.md:59-61`
  still accurately describes the `uiDesigner.defaultOutput` filtering, and the
  `config.yml` key matches `gradle.properties`.
- **Round-1 #3 (FAWE compile/run drift) fixed at the seam that matters.** The
  constant is the right shape: one edit changes both the classpath and the dev
  server. `compileOnly` is still correct — FAWE is provided by the server
  (run-paper drops the Paper jar into `plugins/`), and shading it would duplicate
  classes already on the server classpath. Keeping the BOM for transitives is
  acceptable; only the comment/030-note clarity above is missing.
- **`ReplaceTokens` filtering is the right amount of machinery, not an
  over-abstraction.** It replaces the old whole-file `expand`/`SimpleTemplateEngine`
  hazard with explicit `${defaultOutput}` token matching (`build.gradle.kts:59-67`),
  and unknown `$`/`${...}` pass through. It handles exactly the one build-time
  default the design specifies; ticket 010's option to move to a generated
  `BuildConfig` remains available if config grows, but nothing here pre-empts or
  over-builds it. No typed-config object is warranted yet.
- **`writeDevServerFiles` always-run is a deliberate dev-server behaviour, not a
  hidden abstraction.** `outputs.upToDateWhen { false }` (`:75`) re-applies the
  port/offline/EULA files on every `runServer`, which is what the ticket's
  "boots on 25000" criterion needs; the task is now named for what it does and
  is only wired into `runServer` (`:84-95`). The declared `outputs.files(...)` is
  redundant given the always-run flag but harmless.
- **Removed command declarations vs. ticket scope wording is resolved by the
  deviations section.** `docs/tasks/000-project-setup.md:21` still lists
  "command declarations" in the original Scope and lines 15/18-19/23/28/47 still
  carry the pre-ticket versions, but the "Deviations from this ticket" section
  (`:52-66`) explicitly supersedes them. That is the standard place to record
  departures; not worth rewriting the historical scope.
- **Extendability to 010–070 is intact.** *010* needs `saveDefaultConfig()` and
  `UiDesignerConfig` — no build change, filtering already present. *020* needs
  the serialization plugin/runtime — present (`:7,35`). *030* needs FAWE
  `compileOnly` and the `SelectionSource` seam — present. *040* uses Bukkit
  `DoubleChest` inside `ChestScanner`/grouper — no build change. *050* needs
  CommandAPI shaded — present (`:36`). *060/070* orchestrate existing pieces.
  The only cross-cutting edit any of these needs is the comment/note in issue 1.
- **No over-generalisation.** No empty `config/`, `model/`, `capture/`,
  `commands/` packages; no speculative interfaces beyond the one seam
  (`SelectionSource`) the architecture explicitly reserves; the extra `format-check`
  and `clean` just recipes are small and self-explanatory.
- **Package root, single module, clean boundaries still hold.** Root package
  `dev.cypdashuhn.uidesigner`, `UiDesignerPlugin` as the only main class, test
  package mirrored under `src/test/kotlin`; no Bukkit import outside the plugin
  entry point; FAWE stays `compileOnly`; CommandAPI/serialization are
  `implementation` and shaded (`:97-99`). The `open` modifier on
  `UiDesignerPlugin` with its why-comment is a MockBukkit subclassing
  requirement, not a production extension point, and is correctly documented.
- **`paper-api` build 123/111 split still deliberate and now commented**
  (`build.gradle.kts:45-46`), matching Round-1's carry-over note.
