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
