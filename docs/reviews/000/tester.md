# Tester review — 000 (Project setup and dev server)

## Round 1

### Verdict
Ship. The single MockBukkit smoke test is the right test for a setup ticket: it
proves the test harness is wired (JUnit 5 + Kotlin test source set + MockBukkit)
and that the plugin loads and enables, which is the one acceptance criterion that
is unit-testable. The remaining issues are low-severity polish, not blockers.

### Issues

#### 1. Test classpath pins a different Paper API build than production (severity: low)
- Location: `build.gradle.kts:30` (`compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")`) vs `build.gradle.kts:42` (`testImplementation("io.papermc.paper:paper-api:26.2.build.111-stable")`)
- Problem: main code compiles against build 123 while the test runtime uses
  build 111. Tests therefore run against a different API than the jar ships
  with. Today the plugin touches no API that differs between the two builds, so
  it is harmless; once `model`/`export`/`capture` use newer API, a green test
  run could mask a `NoSuchMethodError`/behaviour change that only appears on
  the real server, and the divergence is easy to forget.
- Suggested fix: align the test dependency to `26.2.build.123-stable`, or route
  both through a single version constant/version catalog so they cannot drift.
  If build 111 is deliberately required by MockBukkit, add a one-line `why`
  (this is the rare comment the repo allows) so the next person does not
  "fix" it.

#### 2. Smoke test never asserts the plugin identity it claims to cover (severity: low, optional)
- Location: `src/test/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPluginTest.kt:12-14`
- Problem: the acceptance criterion is that the server log shows `UiDesigner`
  enabled. `MockBukkit.load(UiDesignerPlugin::class.java)` will happily load the
  class even if the generated `plugin.yml` carries the wrong `name`, so a typo
  in the `bukkit { name = ... }` block would not be caught by the suite. This is
  a metadata build-config concern, so it is borderline rather than required.
- Suggested fix: either leave as is (metadata is validated by the build/dev
  server, and ticket 010+ will assert config/commands), or add one assertion
  such as `assertEquals("UiDesigner", plugin.name)`. Do not go further than
  that — asserting `apiVersion`, aliases, descriptions, etc. would couple the
  test to plugin-yml's output rather than to behaviour.

### Non-issues
- **No pure-logic tests, MockBukkit used instead.** Correct layer. No
  `model`/`export` packages exist yet (ticket 000 scope), so there is nothing
  Bukkit-free to unit test. When 020/040 land, those tests must stay plain
  JUnit with no MockBukkit; the current file does not need to anticipate that.
- **No test for `config.yml` resource filtering / `output-file` default.** This
  is intentionally deferred, not missing. Ticket 010's acceptance criteria
  explicitly own default resolution, relative/absolute path resolution and
  reload, and its tests will load the packaged resource, so a duplicate test
  here would be redundant. Ticket 000's acceptance criteria do not mention
  config at all.
- **No test for FAWE presence, port `25000`, or jar loading in a real server.**
  These are dev-server/integration concerns with no meaningful unit-test layer;
  the implementor's `just run` verification is the right place for them.
  Encoding them as tests would be expensive and brittle.
- **Not brittle to MockBukkit lifecycle/global state/ordering.** There is one
  test, and `MockBukkit.mock()` is wrapped in `try/finally` (`:10-17`), so
  `unmock()` runs even if the assertion fails — no leaked global server state.
  The `MockBukkitExtension` alternative is equivalent here; manual
  mock/unmock is fine and arguably clearer for a single test. Revisit only if
  the suite grows and multiple classes start managing the server.
- **The `assertTrue(plugin.isEnabled)` assertion is not tautological.**
  `MockBukkit.load` runs `onEnable`, so a throwing `onEnable` fails the load;
  the assertion additionally pins the enabled state that the acceptance
  criterion names. It is a shallow assertion, but shallow is the correct depth
  for a smoke test.
- **No test of `onDisable` or the log lines.** Asserting on log output would be
  brittle and tests nothing the acceptance criteria need beyond "loads without
  errors".
- **Naming/style.** Backticked sentence test name matches the convention in
  `docs/architecture.md:62`; test lives in the correct `src/test/kotlin`
  source set and package.
