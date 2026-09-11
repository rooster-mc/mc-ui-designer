# Tester review — 010 (Config loading and default output path)

## Round 1

### Verdict
Ship with fixes. The core path logic and plugin reload path are covered at the
right layers, but the tests never verify that the Gradle-provided default was
actually substituted into the packaged `config.yml`, so a broken
`ReplaceTokens` setup would pass unnoticed.

### Issues

#### 1. Gradle-provided default is never asserted (severity: medium)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:75-81`,
  `src/main/resources/config.yml:1`, `build.gradle.kts:60-69`
- Problem: `packagedConfigText()` and `packagedOutputFile()` read the *same*
  packaged resource the plugin writes, so the tests are self-consistent. If the
  resource filter is misconfigured (wrong token, property name typo, filter not
  applied), `config.yml` ships as `output-file: ${defaultOutput}` and every test
  still passes: the written file equals the packaged resource, and
  `dataFolder.resolve("${defaultOutput}")` equals the plugin's resolved path.
  The acceptance criterion "written to the data folder on first enable with the
  Gradle-provided default" is therefore not actually verified.
- Suggested fix: add a cheap, decoupled assertion in
  `UiDesignerPluginTest` (or a small resource test) that the packaged resource
  contains no unresolved token, e.g.
  `assertFalse(packagedConfigText().contains("\${"))`, or compare it against the
  literal `output-file: design.json`. The token assertion avoids re-coupling the
  test to `gradle.properties`.

#### 2. Missing-defaults error path is untested (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:19-21`
- Problem: `defaultOutputFile()` calls `error(...)` when the root config has no
  defaults. Nothing pins that invariant; a future change returning `null` or an
  empty string would silently alter resolution.
- Suggested fix: one `assertThrows(IllegalStateException::class.java)` test
  constructing `UiDesignerConfig(YamlConfiguration(), dataFolder)` (no defaults
  set) and reading `outputFile`.

#### 3. Repeated MockBukkit setup/teardown boilerplate (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:11-73`
- Problem: four tests repeat `MockBukkit.mock()` / `try` / `finally { unmock() }`.
  It is noise and a future test can easily forget teardown, leaking global
  MockBukkit state into the next test.
- Suggested fix: move `MockBukkit.mock()` and `MockBukkit.unmock()` into
  `@BeforeEach` / `@AfterEach`.

#### 4. Absolute-path test is Unix-shaped (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfigTest.kt:11,36`
- Problem: `Path.of("/tmp/shop.json")` is not absolute on Windows, so
  `absolute path is used unchanged` would fail there; the data-folder literal is
  also Unix-shaped. The repo's dev loop is Linux-only, so this is minor.
- Suggested fix: derive the absolute path from
  `Path.of("").toAbsolutePath().resolve("shop.json")`, or leave as-is if CI is
  guaranteed Linux.

### Non-issues
- Right layer for the pure logic: `UiDesignerConfigTest` is plain JUnit using
  `YamlConfiguration` without MockBukkit. That is correct — `YamlConfiguration`
  needs no server, and `UiDesignerConfig` wraps Bukkit's `FileConfiguration` by
  design (it is not `model`/`export`). Plugin lifecycle uses MockBukkit.
- Deferring the literal `/uidesigner reload` command to ticket 060 is fine: 060
  explicitly lists `reload` and its command tests, and
  `UiDesignerPlugin.reloadConfiguration()` is the seam the command will call.
  Reload behaviour is exercised through that method.
- The `reload writes back the default when the output key is missing` test is
  meaningful, not tautological: `MemorySection.isSet` with the default
  `copyDefaults=false` ignores defaults (checked against the paper-api 26.2
  sources), so `applyDefaults()` genuinely returns true and `saveConfig()` runs.
- Relative resolution, `..` normalisation, absolute passthrough, default
  fallback, and "present key is not overwritten" are all covered without
  duplication. `present key is not overwritten` is valuable: it protects user
  configuration from being clobbered.
- `packaged config is written to the data folder on enable` mostly exercises
  Bukkit's `saveDefaultConfig`, but it does confirm the resource is on the
  classpath and is copied, so it earns its place.
- Plugin tests derive the expected default from the packaged resource instead of
  hardcoding `design.json`, which keeps them resilient if the Gradle default
  changes.

## Round 2

### Verdict
Ship. All four round-1 findings are resolved and the three tests that guard the
round-1 fixes (Gradle token substitution, malformed-config non-clobber, blank
fallback) are genuine regression tests, not tautologies. Two low-severity
residual gaps remain in the edges of the new token/blank assertions; neither
blocks the ticket.

### Issues

#### 1. Substitution test cannot see a blank Gradle property (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:41-44`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:23-25`
- Problem: `packaged config has the Gradle default substituted` only asserts
  the packaged resource no longer contains `${`. If `uiDesigner.defaultOutput`
  were set to the empty string, `ReplaceTokens` would still substitute (no token
  left), the test passes, and the packaged default becomes `""`.
  `defaultOutputFile()` only guards against `null` (`config.defaults?.getString(...) ?: error(...)`),
  so a blank default is returned as a valid value; `resolve("")` then yields the
  data folder itself — the exact failure mode round 1 flagged for blank user
  values, now reachable from the build. The existing plugin-level tests still
  pass in that scenario because both the expected (`packagedOutputFile()`) and
  actual paths collapse to the data folder.
- Suggested fix: add one cheap assertion that the substituted value is usable,
  e.g. `assertFalse(packagedOutputFile().isBlank())` (or `assertEquals("design.json", packagedOutputFile())`).
  If the blank-default case is considered in scope, `defaultOutputFile()` should
  treat blank like missing (`?.takeIf { it.isNotBlank() } ?: error(...)`) so the
  error path is symmetric with the user-value path.

#### 2. Blank write-back test only pins the empty-string case (severity: low)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfigTest.kt:67-74`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:16-21`
- Problem: `blank key is written back from the default` uses `outputFile = ""`.
  That passes whether the guard is `isNullOrBlank()` (current) or `isEmpty()`,
  so the whitespace branch of the write-back path is not pinned. The getter
  test at `UiDesignerConfigTest.kt:21-26` does cover `"   "`, but only for
  `outputFile`, not for `writeDefaultOutputIfMissing()`.
- Suggested fix: use `"   "` in the write-back test (or add one case). With an
  `isEmpty()` regression it would return `false` and the `assertTrue` would fail,
  which is the behaviour we want to protect.

### Non-issues
- **Round-1 #1 (Gradle default never asserted) — resolved.** The token test at
  `UiDesignerPluginTest.kt:41-44` fails if the `ReplaceTokens` filter is not
  applied or the token names are wrong, which is the failure mode claimed. It is
  an intrinsic property of the packaged resource, not a re-read of the same
  expected value, so it is not self-consistent. Issue 1 above is only the
  empty-property edge beyond the original finding.
- **Round-1 #2 (missing-defaults error path) — resolved.** `UiDesignerConfigTest.kt:28-33`
  builds `UiDesignerConfig(YamlConfiguration(), dataFolder)` with no defaults and
  asserts `IllegalStateException`. The getter genuinely reaches `error(...)`:
  `config.getString` is null and `config.defaults` is null. Good.
- **Round-1 #3 (repeated MockBukkit boilerplate) — resolved.** `@BeforeEach` /
  `@AfterEach` at `UiDesignerPluginTest.kt:14-22` replace the four
  `try/finally` blocks, so teardown can no longer be forgotten.
- **Round-1 #4 (Unix-shaped absolute test) — resolved.**
  `UiDesignerConfigTest.kt:49-55` now derives the absolute path via
  `Path.of("").toAbsolutePath().resolve("shop.json")`, so it holds on any OS.
- **Malformed-config non-clobber is a real test, not a tautology.**
  `UiDesignerPluginTest.kt:74-84` writes invalid YAML, calls
  `reloadConfiguration()`, and asserts the file is byte-identical. I verified the
  mechanics against paper-api `26.2.build.111` sources: the pre-check uses the
  instance `FileConfiguration.load(File)`, which *throws*
  `InvalidConfigurationException` (`FileConfiguration.java:123-160`,
  `YamlConfiguration.java:98-123`), so `readable` is false; meanwhile
  `JavaPlugin.reloadConfig()` uses the static
  `YamlConfiguration.loadConfiguration(File)`, which swallows the parse error and
  returns an empty config with defaults (`YamlConfiguration.java:303-319`,
  `JavaPlugin.java:170-180`). That makes `writeDefaultOutputIfMissing()` return
  true, so without the `readable` gate `saveConfig()` would overwrite the file and
  the assertion would fail. The test therefore exercises the exact fixed branch.
  (It would also error rather than silently pass if `reloadConfig()` ever threw.)
- **Blank value fallback is genuinely covered.** `UiDesignerConfigTest.kt:21-26`
  uses whitespace (`"   "`) and asserts the default path; pre-fix this resolved
  to a directory under the data folder, so the assertion is discriminating. The
  write-back branch is covered by `:67-74` (see issue 2 for the residual edge).
- **No excessive or brittle new tests.** The added cases each map to a distinct
  branch (token filter, malformed reload, blank getter, blank write-back,
  missing defaults). `packaged config has the Gradle default substituted` runs
  inside the MockBukkit-mocked class unnecessarily, but the cost is trivial and
  not worth a separate resource test.
- **Deferral still sound.** The literal `/uidesigner reload` command is listed
  in `docs/tasks/060-export-command.md:14,29`; `UiDesignerPlugin.reloadConfiguration()`
  remains the seam and is exercised end-to-end at `UiDesignerPluginTest.kt:46-84`.
- **Layer placement unchanged and correct.** Pure resolution/fallback logic
  stays in plain-JUnit `UiDesignerConfigTest`; lifecycle/reload stays on
  MockBukkit in `UiDesignerPluginTest`.
