# Correctness review — 000 (Project setup and dev server)

## Round 1

### Verdict
Ship with fixes. All four acceptance criteria are actually met: the shaded jar
exists and is correct, the committed `run/logs/latest.log` shows the server on
`*:25000` with `UiDesigner enabled` and FAWE 2.15.3, and the smoke test is green.
The issues below are latent conflicts and consistency drift, not ticket-000
blockers.

### Issues

#### 1. plugin.yml declares `/uidesigner` (+`/uid`) and `/chest-edit` with no owner, colliding with the CommandAPI plan (severity: medium)
- Location: `build.gradle.kts:54-62` (generated `build/resources/main/plugin.yml:4-11`), `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:5-13`
- Problem: The commands are registered by Paper from `plugin.yml`, but nothing
  implements them: `UiDesignerPlugin` overrides only `onEnable`/`onDisable`, so
  the inherited `JavaPlugin.onCommand` returns `false`. This does **not** cause a
  load error (the existing log loads/enables cleanly at
  `run/logs/latest.log:26,47-48`, and MockBukkit `load` also succeeds), so
  acceptance criterion 4 is satisfied. The real problem is forward-looking:
  `docs/design.md:50` says commands are registered with CommandAPI. CommandAPI's
  duplicate handling (`CommandAPIHandler.register`) only tracks commands
  registered *through CommandAPI*; a `plugin.yml` command of the same name is
  invisible to it, and `PaperCommandRegistration.registerCommandNode` adds the
  literal straight into the Brigadier dispatcher. So when a later ticket calls
  `CommandAPICommand("uidesigner").register(...)`, the two registration paths
  target the same literal with no conflict detection — the result is a merged /
  shadowed node (or the plugin.yml `PluginCommand` wins and the CommandAPI
  handler never runs), which is painful to diagnose. The sibling
  `extended-inventory/build.gradle.kts` deliberately leaves `commands { }` empty
  and registers everything through CommandAPI for exactly this reason.
- Repro: `just run`, then execute `/uidesigner` or `/chest-edit` in-game/console
  → the command is listed but silently does nothing. Then add
  `CommandAPICommand("uidesigner").executes { ... }.register(plugin)` in
  `onEnable` → duplicate literal registration against the existing plugin.yml
  node.
- Suggested fix: decide command ownership now. If CommandAPI owns them, remove
  the `commands { ... }` block (or leave it empty) and keep descriptions in the
  command classes; if plugin.yml owns them, implement `onCommand` and document
  why CommandAPI is not used. Either way add a permission node before the
  commands become player-facing.

#### 2. Design/ticket docs were not updated for the justified dependency deviations (severity: low)
- Location: `docs/design.md:47,54`, `docs/tasks/000-project-setup.md:15,19,23,47` vs `build.gradle.kts:7,39,88-93`
- Problem: The build uses run-paper `3.1.0` (not `3.0.2`), JUnit `6.1.3` (not
  JUnit 5), and downloads FAWE from GitHub (not Hangar). Each deviation is
  justified (Gradle 9.7 incompatibility, MockBukkit 4.116.1's POM pins
  junit-jupiter-api 6.1.3, Hangar `downloadUrl`s are null), but the rationale
  lives only in the implementor handoff. `AGENTS.md` requires docs to be updated
  in the same commit as the change that invalidates them, and a future reader
  comparing the design doc to the build will think the build is wrong.
- Repro: read `docs/design.md:47` ("run-paper `3.0.2`") and `:54` ("JUnit 5")
  next to `build.gradle.kts:7` (`3.1.0`) and `:39` (`6.1.3`).
- Suggested fix: update `docs/design.md` to run-paper 3.1.0 / JUnit 6 / GitHub
  FAWE download (one line each is enough), and update ticket 000's scope/notes
  or add a short "deviations" note so the reason survives.

#### 3. `expand` on `config.yml` is brittle to any literal `$` (severity: low)
- Location: `build.gradle.kts:65-69`
- Problem: `expand` runs the resource through Groovy's `SimpleTemplateEngine`,
  so any literal `$` or `${...}` added to `config.yml` later (a message, a path,
  a regex) either fails the build or is silently substituted. Today the file is
  only `${defaultOutput}` and the packaged result is correct
  (`build/resources/main/config.yml` → `output-file: plugins/UiDesigner/design.json`),
  so there is no current bug, only a trap.
- Repro: add `comment: "costs $5"` to `src/main/resources/config.yml`, run
  `just build` → template evaluation error / mangled value.
- Suggested fix: use explicit token replacement with `@...@` delimiters
  (`filter<ReplaceTokens>("tokens" to mapOf(...))`) or document the `$` hazard
  in the file.

#### 4. FAWE compile-time version (2.15.0) and runtime version (2.15.3) have separate sources of truth (severity: low)
- Location: `build.gradle.kts:35-37` vs `build.gradle.kts:88-93`
- Problem: `bom-newest:1.56` resolves `FastAsyncWorldEdit-Core/-Bukkit` to
  **2.15.0** (verified in the Gradle cache), while `runServer` downloads
  **2.15.3**. Compiling against the older API and running against the newer is
  safe, but the two versions drift independently: a future ticket that needs a
  2.15.3 API won't compile, and bumping one without the other is easy to miss.
- Repro: `just build`, inspect the resolved `compileClasspath` FAWE version
  (2.15.0) vs `run/logs/latest.log:24` (2.15.3+1704422).
- Suggested fix: drive both from one version (e.g. pin the BOM to a release that
  matches the download, or define the FAWE version once and use it in both
  places), or note the deliberate split.

### Non-issues
- **paper-api main `26.2.build.123-stable` vs test `26.2.build.111-stable`.**
  Deliberate and correct: MockBukkit 4.116.1 is built against 111, and the
  sibling `extended-inventory` uses the same split. `compileOnly` does not leak
  into the test classpath, so tests compile/run against 111 only. No API
  divergence is used yet; revisit if `model`/`export` start using newer API.
- **JUnit 6.1.3 instead of JUnit 5.** Justified: MockBukkit 4.116.1's POM
  declares `junit-jupiter-api:6.1.3`; the versionless
  `junit-platform-launcher` resolves to 6.1.3 through that constraint.
- **plugin.yml commands causing a load error/warning.** They do not. Paper and
  MockBukkit both load the plugin with no command error (`run/logs/latest.log:26,47-48`).
  The problem is ownership/registration (issue 1), not loading.
- **Shading contents.** `build/libs/UiDesigner-1.0-SNAPSHOT-all.jar` contains
  `dev/jorel/commandapi`, `kotlinx/serialization`, Kotlin stdlib, the plugin
  class, `plugin.yml` and the filtered `config.yml`; it contains no
  `io/papermc`, `com/sk89q` or `com/fastasyncworldedit` classes. `compileOnly`
  for paper-api/FAWE and `implementation` for CommandAPI/serialization is
  correct, and `mergeServiceFiles()` is harmless (the CommandAPI jar ships no
  `META-INF/services`).
- **config resource filtering output.** `build/resources/main/config.yml` is
  exactly `output-file: plugins/UiDesigner/design.json`; `project.property`
  fails fast if `uiDesigner.defaultOutput` is missing, which is the desired
  behaviour.
- **`prepareRunServer` / port / EULA.** It writes `server-port=25000`,
  `online-mode=false` and `eula=true`. Paper rewrites `server.properties` into
  the full default set, which makes the task's declared outputs change and
  re-run on the next invocation, re-applying the port — intended, not a bug.
  The log confirms `Starting Minecraft server on *:25000`.
- **FAWE GitHub coordinates.** The `IntellectualSites/FastAsyncWorldEdit`
  `2.15.3` asset `FastAsyncWorldEdit-Paper-2.15.3.jar` resolves and loads
  (`run/logs/latest.log:24`); the Hangar→GitHub switch is justified.
- **run-paper 3.1.0 / foojay 1.0.0 / ktlint 14.2.0.** All resolve and were
  exercised by the implementor's green `just build`/`just test`/`just run`; the
  3.0.2 bump is needed for Gradle 9.7.
- **Wrapper and hygiene.** `gradlew` is committed executable, the wrapper points
  at Gradle 9.7.0, and `/run` plus `build/` are gitignored, so no server output
  or build artifacts leak into the commit.
- **JSON/data-format conformance.** No export code exists yet, so there is
  nothing to compare against `docs/data-format.md`; the ticket correctly scopes
  that out.
- **`just build` running `build shadowJar`.** Redundant (the explicit
  `tasks.build { dependsOn("shadowJar") }` at `build.gradle.kts:101-103` already
  wires it) but harmless.
