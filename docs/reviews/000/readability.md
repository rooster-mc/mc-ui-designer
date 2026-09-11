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

## Round 2

### Verdict
Ship with fixes. All five Round-1 readability findings are resolved, and the
fixes themselves are clean: `just build` is single-sourced, `writeDevServerFiles`
is named for what it does and always runs, and the three new why-comments each
explain a non-obvious reason. What remains is one missed doc line
(`AGENTS.md:25`) plus two low-severity spots where the code's *why* is still
invisible to a newcomer.

### Issues

#### 1. `AGENTS.md` Stack line still says "JUnit 5" (severity: low)
- Location: `AGENTS.md:25` vs `build.gradle.kts:42`, `docs/design.md:57`,
  `docs/architecture.md:62`
- Problem: This is the last surviving copy of the Round-1 doc drift. Round 1
  fixed `docs/design.md`, `docs/architecture.md` and the `AGENTS.md` "Current
  status" block, but the Stack paragraph was missed. `AGENTS.md` is the first
  file every agent opens, and it now contradicts the build and the two docs it
  points at. (Correctness and architecture flagged the same line in their Round
  2; noting it here because Round 1 owned the doc-consistency finding.)
- Suggested fix: change `AGENTS.md:25` to "JUnit 6 + MockBukkit". One word.

#### 2. `ReplaceTokens` block hides why it replaced `expand` (severity: low)
- Location: `build.gradle.kts:59-67`
- Problem: The old `expand(mapOf(...))` was one self-explanatory line; the new
  form passes three stringly-typed Ant keys (`"tokens"`, `"beginToken"`,
  `"endToken"`) and escapes the dollar as `"\${"`. A reader who does not already
  know Gradle's Ant-filter convention cannot tell what is being replaced or why
  the begin/end tokens are overridden — and the reason `expand` was abandoned
  (it runs the whole file through Groovy's template engine, so stray `$`
  sequences are hazards; `ReplaceTokens` matches only `${...}`) is not visible
  anywhere. The behaviour is correct; only the intent is opaque.
- Suggested fix: add one why-comment above `filter<ReplaceTokens>` stating that
  only `${...}` is substituted and everything else passes through, or move that
  rationale into `docs/design.md:59-61` if a comment is unwanted. Do not revert
  to `expand`.

#### 3. BOM plus explicit FAWE pin looks redundant with no stated reason (severity: low)
- Location: `build.gradle.kts:38-40`
- Problem: The fix added `$faweVersion` to the two `compileOnly` FAWE
  coordinates while keeping `platform("com.intellectualsites.bom:bom-newest")`
  directly above them. A reader naturally asks "why pin versions when a BOM is
  right there?" and has to infer that the BOM only supplies transitive deps
  while the explicit pin overrides its own FAWE `2.15.0`. This is the same
  latent dual-version mechanism architecture raises in its Round 2 issue 1;
  from a readability standpoint the code does not say which of the two is
  authoritative.
- Suggested fix: one why-comment on `:38` (e.g. the BOM supplies transitives
  only; the explicit `$faweVersion` pins FAWE itself), matching architecture's
  suggested wording.

### Non-issues (Round-1 verification and new-fix re-scan)

- **Round-1 #1 (stale contract docs) fixed except issue 1 above.**
  `docs/design.md:46-57` now names run-paper `3.1.0`, JUnit 6, the `2.15.3` FAWE
  pin and the GitHub download; `docs/architecture.md:62` says JUnit 6; the
  `AGENTS.md:36` parenthetical is gone and `AGENTS.md:85-90` describes the
  shipped setup accurately.
- **Round-1 #2 (`prepareRunServer`) fixed and the fix reads well.**
  `writeDevServerFiles` (`build.gradle.kts:69-82`) says what it does, and
  `outputs.upToDateWhen { false }` (`:75`) makes the always-run behaviour
  explicit rather than a Gradle-semantics trap. The declared `outputs.files(...)`
  is now redundant with that flag but harmless (it still registers the task's
  products), so it is not dead code. The EULA comment (`:79`) is a genuine
  *why* — it records that auto-accepting is scoped to the local dev server —
  and earns its line.
- **Round-1 #3 (double `shadowJar` wiring) fixed.** `justfile:7-8` is now
  `./gradlew build`, so `build.gradle.kts:101-103` is the single source of truth
  for the shaded jar, and the `justfile` comment still matches the outcome.
- **Round-1 #4 (`paper-api` split) fixed.** The comment at `build.gradle.kts:45`
  sits directly above the `26.2.build.111-stable` line it explains and states
  both the reason and the fact that main stays on the server build. Placement
  and phrasing are right.
- **Round-1 #5 (`open` plugin) resolution is readable and acceptable.** The
  comment at `UiDesignerPlugin.kt:5` ("MockBukkit loads plugins by subclassing;
  Kotlin classes are final by default") turns a puzzling modifier into a stated
  constraint, and it is the one place the `open` keyword appears, so the
  documented reason is discoverable. Keeping `open` with the explanation is
  better than a `final` class that breaks the harness.
- **`faweVersion` placement is good.** `build.gradle.kts:17` is the only
  definition, feeding both the `compileOnly` pins (`:39-40`) and the GitHub
  download (`:91-92`); one edit now moves the compile and run versions together,
  and the name cannot be confused with `version` in context.
- **Ticket "Deviations" section is clear and correctly scoped.**
  `docs/tasks/000-project-setup.md:52-66` records each departure (run-paper
  `3.1.0`, JUnit 6, GitHub-vs-Hangar FAWE, foojay `1.0.0`, removed command
  declarations) with a one-line reason, so the still-historical Scope bullets
  (`:15,19,23,28`) do not mislead — the reader is told the body is superseded
  rather than being made to rewrite history.
- **New test line reads fine.** `assertEquals("UiDesigner", plugin.name)` plus
  the alphabetised `assertEquals`/`assertTrue` imports add a concrete assertion
  without obscuring the mock/load/assert/unmock flow.
- **Formatting.** No `.kt`/`.kts` line exceeds the `.editorconfig` 100-column
  limit; the longest (`build.gradle.kts:62`) is 95 columns. Indentation,
  trailing commas and the new comment lines all match ktlint, and no gratuitous
  reformatting or dead code was introduced by the fixes.
