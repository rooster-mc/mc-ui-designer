# Readability review — 000 (Project setup and dev server)

## Round 1

### Verdict
Ship with fixes. The Kotlin, the test, the wrapper and the overall build-file
shape are clean and quick to read. Four small things slow a reader down: the
docs that describe the build are now stale, `prepareRunServer` hides what it
really does (and when it will be skipped), `just build` and `build.gradle.kts`
both wire `shadowJar`, and two version choices have no visible reason.

### Issues

#### 1. Stale contract docs still describe the pre-ticket state (severity: medium)
- Location: `docs/design.md:47,54`, `docs/architecture.md:62`,
  `AGENTS.md:36,87-90` vs `build.gradle.kts:7,39,88-93`
- Problem: The design/architecture docs are the first thing the next ticket's
  reader opens, and they no longer match the build. `docs/design.md:47` pins
  run-paper `3.0.2` but the build uses `3.1.0` (`build.gradle.kts:7`);
  `docs/design.md:54` and `docs/architecture.md:62` say "JUnit 5" but the build
  uses JUnit `6.1.3` (`build.gradle.kts:39`); `AGENTS.md:89` still says "No code
  has been written yet" and `AGENTS.md:36` still says the `justfile` is created
  by this ticket, both now false. The `AGENTS.md` conventions require docs to be
  updated in the same commit that invalidates them. A reader comparing doc to
  build will conclude the build is wrong.
- Suggested fix: one line each in `docs/design.md` (run-paper 3.1.0, JUnit 6),
  fix `docs/architecture.md:62` to "JUnit 6", and rewrite `AGENTS.md`
  "Current status" to say setup is done and point at the next ticket; drop the
  now-resolved parenthetical at `AGENTS.md:36`.

#### 2. `prepareRunServer` reads like a helper but silently writes config and is skipped once files exist (severity: medium)
- Location: `build.gradle.kts:71-82`
- Problem: The name says "prepare", but the task overwrites `run/server.properties`
  and accepts the Minecraft EULA by writing `run/eula.txt`; neither side effect
  is visible from the name or from `runServer`. Worse, `outputs.files(...)` with
  no declared inputs means Gradle treats the task as up-to-date after the first
  run, so editing `server-port=25000` in the build file does not update an
  existing `run/server.properties` — a reader has to know Gradle's up-to-date
  semantics to predict that, and the file gives no hint. The auto-accepted EULA
  is likewise surprising.
- Suggested fix: rename the task to what it does (e.g. `writeDevServerConfig`)
  and make the skip behaviour explicit: either always run it
  (`outputs.upToDateWhen { false }`, since the payload is tiny) or keep the
  outputs and add a one-line comment saying the files are only created when
  missing and `run/` must be deleted to regenerate them. Either way the EULA
  write should be obvious from the task name.

#### 3. `just build` and `build.gradle.kts` both wire `shadowJar` (severity: low)
- Location: `justfile:7-8`, `build.gradle.kts:101-103`
- Problem: `tasks.build { dependsOn("shadowJar") }` already makes
  `./gradlew build` produce the shaded jar, yet `just build` runs
  `./gradlew build shadowJar`. A reader sees two mechanisms for one outcome and
  cannot tell which is load-bearing; if the `dependsOn` were later removed the
  justfile would quietly mask it. The `build` recipe comment also promises a
  shaded jar, which is really the `dependsOn`'s job.
- Suggested fix: drop the redundant target from `justfile:8` (`./gradlew build`)
  and let `build.gradle.kts:101-103` be the single source of truth.

#### 4. Two `paper-api` versions with no stated reason (severity: low)
- Location: `build.gradle.kts:30,42`
- Problem: `compileOnly` uses `26.2.build.123-stable` while
  `testImplementation` uses `26.2.build.111-stable`. A reader cannot tell
  whether that is deliberate (MockBukkit `4.116.1` compiles against 111) or a
  copy-paste slip, so the next person either "fixes" it and breaks tests or
  copies the pattern blindly.
- Suggested fix: add a one-line comment on `build.gradle.kts:42` explaining that
  MockBukkit 4.116.1 targets build 111, or bind the test dependency to the same
  property if that turns out to be safe.

#### 5. `UiDesignerPlugin` is `open` for no visible reason (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:5`
- Problem: Nothing extends the plugin: `UiDesignerPluginTest` calls
  `MockBukkit.load(UiDesignerPlugin::class.java)` (reflection, works on a final
  class) and the sibling `ExtendedInventoryPlugin` is a plain `class`. `open`
  advertises an extension point that does not exist and makes a reader pause to
  look for the subclass.
- Suggested fix: drop `open`.

### Non-issues
- **Kotlin control flow.** `UiDesignerPlugin.onEnable`/`onDisable` are two
  direct log lines; the test is a straight mock/load/assert/unmock with a
  backticked sentence name and a `try/finally` that is easy to follow. No clever
  one-liners, no comments to explain away.
- **File hygiene.** `gradlew` is committed with mode `100755`; wrapper jar and
  `gradle-wrapper.properties` are standard; `.gitignore` covers `/run`, `build/`
  and `.gradle`; `.editorconfig` covers `kt,kts` with max line 100.
- **Formatting.** Every changed line is within the 100-column limit; the
  `maven(...) { name = ... }` blocks, the `github(...)` trailing comma and the
  indentation all match ktlint per `.editorconfig`. Nothing here would be
  reformatted.
- **Build-file grouping.** `plugins` → coordinates → `repositories` →
  `dependencies` → `kotlin`/`bukkit` → `processResources` → run-server wiring →
  `shadowJar`/`build`/`test` reads top-to-bottom in the order a newcomer asks
  about them. The `prepareRunServer` provider is used by `runServer`, so it is
  not dead indirection.
- **`justfile`.** Recipes are one-liners with a useful `#` description each and
  a `default` that lists them. The extra `format-check` and `clean` targets go
  beyond the ticket but are self-explanatory and consistent with `format`.
- **`config.yml` filtering.** The single `${defaultOutput}` line and the
  `processResources` expand at `build.gradle.kts:65-69` are easy to correlate
  with `gradle.properties:5`; the property name matches the doc.
- **`settings.gradle.kts` / `gradle.properties`.** Small and single-purpose
  (toolchain resolver, `rootProject.name`, JVM args, one project property); no
  unused entries.
