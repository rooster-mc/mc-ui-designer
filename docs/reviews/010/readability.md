# Readability review — 010 (Config loading and default output path)

## Round 1

### Verdict
Ship. The change is small, consistently named, and easy to follow; the only
findings are low-severity readability nits. No structural or formatting issues.

### Issues

#### 1. `if (loaded.applyDefaults()) saveConfig()` hides a write-through (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:20`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:13-17`
- Problem: `applyDefaults()` both mutates the `FileConfiguration` the
  `UiDesignerConfig` was constructed with and returns whether it did. The call
  site reads as a predicate, but its purpose is the mutation, and the mutation
  target is the plugin's live `config`, not the `loaded` wrapper. A reader has to
  reconstruct that chain to see why `saveConfig()` is conditional. The `loaded`
  local then makes it look like the plugin is building a fresh object rather than
  editing the shared one.
- Suggested fix: make the intent explicit at the call site, e.g. name the method
  for its effect (`writeDefaultOutputIfMissing(): Boolean`) or capture the result
  (`val wroteDefaults = loaded.applyDefaults()`), so the boolean is clearly about
  "did we change the file" rather than a query.

#### 2. Plugin test re-derives the key as a string literal (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:81`
- Problem: `getString("output-file")!!` hardcodes the key even though
  `UiDesignerConfig.OUTPUT_FILE_KEY` exists for exactly this purpose and is used
  consistently by `UiDesignerConfigTest`. The `!!` also makes the helper denser
  than it needs to be. If the key is ever renamed, this helper silently breaks
  the test's expected value rather than failing to compile.
- Suggested fix: import `dev.cypdashuhn.uidesigner.config.UiDesignerConfig` and
  use `OUTPUT_FILE_KEY`; the `!!` is acceptable for a test fixture, but the
  constant removes the duplicated knowledge.

#### 3. Repeated MockBukkit setup/teardown boilerplate (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:11-73`
- Problem: four tests repeat `MockBukkit.mock()` / `try` / `finally { unmock() }`.
  It is visual noise that pushes the actual assertions down, and each new test
  must remember to copy the teardown. (The tester already flagged this for
  correctness/leak reasons; from a readability standpoint it is the same fix.)
- Suggested fix: move `MockBukkit.mock()` and `MockBukkit.unmock()` into
  `@BeforeEach` / `@AfterEach` and drop the `try/finally` blocks.

### Non-issues
- **Comments.** No new comments were introduced; the only comment in the changed
  files is the pre-existing MockBukkit note at `UiDesignerPlugin.kt:6`, which
  explains a non-obvious *why*. Complies with the no-comments convention.
- **Naming.** `uiConfig` sensibly avoids clashing with `JavaPlugin.config`
  (`getConfig()`), and `outputFile` / `reloadConfiguration` / `applyDefaults`
  describe their behaviour. `packagedConfigText` / `packagedOutputFile` are
  clear test helper names.
- **File hygiene.** `UiDesignerConfig.kt` (31 lines) has one responsibility and
  no dead code or indirection; `UiDesignerPlugin.kt` (27 lines) stays a thin
  lifecycle/wiring class. `OUTPUT_FILE_KEY` is a single source of truth for the
  key in production code.
- **Formatting / ktlint.** All changed lines are under the 100-column limit,
  indentation is four spaces, no tabs or trailing whitespace. Import order
  (`org.*` before `java.nio.file.Path`) matches ktlint's default layout, and the
  trailing commas in multiline declarations/calls are permitted because the
  `.editorconfig` disables both trailing-comma rules.
- **Control flow.** `resolve` and the `outputFile` getter are direct; the
  `(if (path.isAbsolute) ... else ...).normalize()` expression is idiomatic and
  does not need a temporary.
- **Docs.** `docs/design.md` (Gradle property + filtered `config.yml`) and
  `docs/architecture.md` (config package, data flow) remain accurate; the
  `gradle.properties` value change is an internal default and is not documented
  by literal value anywhere, so no doc update is required. `/uidesigner reload`
  is correctly deferred to 060 and does not invalidate anything here.
- **Deferred command.** Exposing `reloadConfiguration()` as the seam and leaving
  the literal command out is consistent with the ticket note and does not hurt
  readability.

## Round 2

### Verdict
Ship. All three round-1 findings are resolved: `writeDefaultOutputIfMissing()`
plus the `wroteDefault` local makes the write-through explicit, the plugin test
uses `UiDesignerConfig.OUTPUT_FILE_KEY`, and MockBukkit setup/teardown now lives
in `@BeforeEach`/`@AfterEach`. The malformed-file guard added for correctness
makes `reloadConfiguration()` denser, and two small naming/flow nits in it are
worth a final polish, but nothing blocks the ticket.

### Issues

#### 1. `readable` names the wrong thing and hides a double negative (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:20-22,27-31`
- Problem: `readable` is `!configFile.isFile || load(...).isSuccess`, so it is
  `true` when the file is **absent** — precisely the case where nothing was read.
  A reader following the name expects "the file parsed", then has to invert
  `!configFile.isFile` to understand why a missing file counts as readable, and
  only then sees that the flag really gates "safe to overwrite the file". The
  name and the negated term point away from the actual meaning.
- Suggested fix: name the concept, not the mechanism, e.g.
  `val fileIsAbsentOrParses = !configFile.isFile || ...` or
  `val canOverwriteFile = ...`, and use it in the branch below. Keeping the
  pre-parse is fine; the name should carry the *why* so no comment is needed.

#### 2. `writeDefaultOutputIfMissing()` also rewrites a blank value (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:16-21`
- Problem: the method returns `true` (and writes the default) when the key is
  present but blank/whitespace (`isNullOrBlank()`), yet the name says only
  "if missing". A reader trusting the name would expect a present-but-blank key
  to be left alone; the blank case is only discoverable by reading the condition
  or the `blank key is written back` test. The round-1 rename fixed the old
  `applyDefaults()` problem but did not track the behaviour added with it.
- Suggested fix: rename to cover both cases, e.g.
  `writeDefaultOutputIfBlank()` or `writeDefaultOutputIfUnset()` (blank is the
  superset here), or keep the name and document the blank rule in the ticket —
  the former is one word and removes the mismatch.

#### 3. Repeated `wroteDefault` in the `if`/`else if` (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:27-31`
- Problem: `if (wroteDefault && readable) ... else if (wroteDefault) ...` repeats
  the guard, so the two mutually exclusive outcomes of `wroteDefault` are split
  across an `&&` and a fall-through. Nesting makes the "wrote a default, now
  save or warn" story linear and lets the reader see that the branch is only
  about `readable`. (Correctness round 2 suggests the same shape for its own
  reason; the readability win is the flat, non-duplicated condition.)
- Suggested fix:
  ```kotlin
  if (wroteDefault) {
      if (readable) saveConfig() else logger.warning("config.yml could not be read; leaving it unchanged")
  }
  ```
  or keep the flat form with an explicit `!readable` in the `else if`.

### Non-issues
- **Round-1 #1 resolved.** The call site now reads
  `val wroteDefault = loaded.writeDefaultOutputIfMissing()` and branches on the
  captured boolean, so the mutation/predicate confusion is gone. The method name
  mismatch in issue 2 is a new, smaller nit, not a regression of the old one.
- **Round-1 #2 resolved.** `UiDesignerPluginTest.packagedOutputFile()` imports
  `UiDesignerConfig` and uses `OUTPUT_FILE_KEY`; no key string literal remains.
- **Round-1 #3 resolved.** `@BeforeEach setUp()` / `@AfterEach tearDown()` hold
  the MockBukkit calls; every test body now starts at its own assertion and no
  `try/finally` remains.
- **Formatting / ktlint.** By eye: all changed lines are under the 100-column
  limit, four-space indentation throughout, no tabs or trailing whitespace, and
  no trailing commas that `.editorconfig` would reject (both trailing-comma
  rules are disabled). The wrapped boolean at `UiDesignerPlugin.kt:20-22` follows
  the expected continuation indent. No formatter changes required.
- **Comments.** No new comments; only the pre-existing MockBukkit note remains.
  The pre-parse in `reloadConfiguration()` is a genuine non-obvious *why*, but
  issue 1's rename can carry that meaning without a comment, which fits the
  repo's no-comments default.
- **Test readability.** `packagedConfigText()` / `packagedOutputFile()` are
  unchanged in shape and still clear; `contains("\${")` correctly escapes the
  dollar to match a literal `${` token and is idiomatic for the assertion. The
  `config(...)` helper in `UiDesignerConfigTest` keeps each test to one arrange
  line. `reload leaves a malformed config file unchanged` reads as a direct
  regression test for the guard.
- **File hygiene.** `UiDesignerConfig.kt` (35 lines) still has one
  responsibility and no dead code; `UiDesignerPlugin.kt` (38 lines) stays a thin
  lifecycle/wiring class. The `loaded` / `wroteDefault` locals earn their names.
- **Docs.** `docs/design.md` (Gradle property `uiDesigner.defaultOutput` filtered
  into `config.yml`) and `docs/architecture.md` (`config/UiDesignerConfig.kt`
  typed view; data-flow sink `config.outputFile`) remain accurate and neither
  states the literal default, so the `gradle.properties` change invalidates
  nothing. Relative-to-data-folder resolution is not documented, but that is
  pre-existing and not contradicted. `/uidesigner reload` stays a 060
  deliverable; the seam is the only thing 010 exposes, as the ticket note
  intends.
