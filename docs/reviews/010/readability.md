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
