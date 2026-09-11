# Correctness review — 010 (Config loading and default output path)

## Round 1

### Verdict
Ship with fixes. The path-resolution core is correct and the write-back works
(the `saveConfig()`-only-serialises-the-root-map worry is a non-issue because
`applyDefaults()` sets the key explicitly). Two real behaviour gaps remain: a
malformed/unreadable `config.yml` is silently clobbered with the default, and a
blank `output-file` resolves to the data folder itself. AC2 as written
("running `/uidesigner reload`") is not satisfiable in this ticket; the seam is
in place and the literal command is deferred to 060.

### Issues

#### 1. Malformed/unreadable `config.yml` is overwritten with a default-only file (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:17-22`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:13-17`
- Problem: `YamlConfiguration.loadConfiguration(file)` swallows
  `InvalidConfigurationException` and returns an **empty** config
  (`org/bukkit/configuration/file/YamlConfiguration.java:99-115`). On reload the
  empty config makes `applyDefaults()` return `true`, and `reloadConfiguration()`
  then calls `saveConfig()`, replacing the user's file with `output-file: <default>`
  and destroying every other key plus the evidence of the parse error. The same
  applies to a transient `IOException` while reading (e.g. permissions): the
  unreadable file is overwritten. This is beyond the intended "missing file /
  missing key" write-back.
- Repro: enable the plugin (file written), then put invalid YAML in
  `plugins/UiDesigner/config.yml`, e.g. `output-file: [unclosed`, and restart or
  call `reloadConfiguration()`. The file becomes exactly `output-file: design.json`;
  the malformed content and any other keys are gone.
- Suggested fix: distinguish "loaded but key absent" from "load failed". Either
  parse the file with `YamlConfiguration().load(file)` in a try/catch inside
  `reloadConfiguration()` and skip `saveConfig()` (logging a warning) on failure,
  or have `applyDefaults()`/the plugin only write back when the file is absent or
  was read successfully.

#### 2. AC2 (literal `/uidesigner reload`) is a deferred gap, not met in this ticket (severity: medium)
- Location: `docs/tasks/010-config.md:19-20,26-27`, `docs/tasks/060-export-command.md:14,29`
- Problem: AC2 requires editing `output-file` and running `/uidesigner reload`
  to change the path used by the exporter. This ticket registers no command and
  contains no exporter, so the user-visible criterion cannot be exercised.
  `reloadConfiguration()` is the correct seam and is tested, but that is the
  underlying behaviour, not the AC. It is an accepted cross-ticket handoff
  (060 owns `/uidesigner reload`), yet 010's own scope text still promises the
  command, so a reader of 010 cannot tell it is deferred.
- Repro: build, start the server, run `/uidesigner reload` → command does not
  exist. Read `docs/tasks/010-config.md:19-20` (claims the command) next to the
  absence of any command class.
- Suggested fix: mark the command explicitly as deferred in 010's scope/notes
  (e.g. "the `/uidesigner reload` command lands in 060; 010 exposes
  `reloadConfiguration()`"), so AC2 is recorded as split rather than silently
  unmet. No source change needed.

#### 3. Blank `output-file` resolves to the data folder itself (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:10-11,23-26`
- Problem: `output-file: ""` is present, so `applyDefaults()` does not fire
  (`isSet` is true). `resolve("")` is `dataFolder.resolve(Path.of(""))`, which is
  `dataFolder` — a directory. A later exporter would attempt to write a file over
  a directory and fail with a confusing IO error, instead of falling back to the
  default. Whitespace-only values have the same shape (a directory named with
  spaces). A user blanking the value to "reset" it is a plausible edit.
- Repro: set `output-file: ""` in `config.yml`, reload, and inspect
  `plugin.uiConfig.outputFile` → it equals the data folder.
- Suggested fix: treat blank/whitespace as missing, e.g. in `applyDefaults()`
  use `config.getString(OUTPUT_FILE_KEY).isNullOrBlank()` instead of `isSet`, and
  in the `outputFile` getter fall back when `getString` is null or blank.

#### 4. `lateinit uiConfig` has no safe pre-enable read (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:8-9,21`
- Problem: `uiConfig` is only assigned at the end of `reloadConfiguration()`,
  which runs from `onEnable()`. Any read before enable (a future `onLoad`, a
  static/DI accessor, or a command handler invoked while disabled) throws
  `UninitializedPropertyAccessException`, and if `defaultOutputFile()` throws
  (missing packaged default) the property stays uninitialised even though the
  plugin may still be reported as loaded. No current caller triggers this, so it
  is latent.
- Repro: call `plugin.uiConfig` before `onEnable()` (or from a code path that
  runs when config defaults are absent) → uninitialised-property crash.
- Suggested fix: leave as-is if the only entry point stays `onEnable`, or expose
  a `configOrNull`/initialise a fallback config so callers can handle a
  not-yet-enabled plugin.

### Non-issues
- **`saveConfig()` persisting the default is genuinely correct.** Verified
  against paper-api `26.2.build.111-stable` sources: `FileConfiguration.save`
  serialises `YamlConfiguration.saveToString()` → `toNodeTree(section)` →
  `getValues(false)` (`YamlConfiguration.java:188-205`), and `getValues(false)`
  only merges defaults when `copyDefaults()` is true
  (`MemorySection.java:97-113`). The plugin's config uses the default
  `copyDefaults = false` (`ConfigurationOptions.java:11`), so a default never
  reaches disk by itself — but `applyDefaults()` calls `config.set(...)`
  (`UiDesignerConfig.kt:15`), which stores the value in the root map, so the
  write-back does persist. No `copyDefaults(true)` needed.
- **`isSet` really does ignore defaults.** `MemorySection.isSet` with
  `copyDefaults=false` returns `get(path, null) != null`
  (`MemorySection.java:120-130`), and `get(path, def)` only inspects the explicit
  map (`:150-180`). So a missing key yields `applyDefaults() == true` even though
  `config.getString(...)` would already return the default via `getDefault`
  (`:331-334`). The `?: defaultOutputFile()` fallback in the getter is therefore
  effectively only a fail-fast for a null `defaults`, which is fine.
- **Gradle default change to `design.json` is the right resolution.** Under the
  new "relative paths resolve against the data folder" rule, the old
  `plugins/UiDesigner/design.json` would resolve to
  `<plugins>/UiDesigner/plugins/UiDesigner/design.json` (double-prefixed). The
  new value resolves to `<plugins>/UiDesigner/design.json`, which is the path the
  old literal meant server-relative. Confirmed the filtered artifact is
  `output-file: design.json` (`build/resources/main/config.yml`), and the
  `ReplaceTokens` delimiters (`build.gradle.kts:60-69`) match `${defaultOutput}`.
- **`saveDefaultConfig()` first vs subsequent enable is correct.**
  `saveDefaultConfig` only writes when `configFile` is absent
  (`JavaPlugin.java`), so an edited file survives restarts; the write-back on
  reload covers a deleted file or removed key.
- **Relative / `..` / absolute resolution is correct.** `Path.of` + `isAbsolute`
  + `dataFolder.resolve` + `normalize` yields the expected paths, including
  `exports/../shop.json` → `<data>/shop.json`. Path traversal (`../secret.json`)
  is allowed but is admin-controlled configuration, and absolute paths were
  already permitted by the ticket, so it is by design rather than a hole.
- **Threading.** All config/block access happens from `onEnable` or the
  (future) main-thread command; no async file IO is introduced here.
- **Nullability of the key.** A blank YAML key parses to absent (covered by the
  fallback); a non-string scalar is stringified by `getString`; no NPE on the
  normal paths. `Path.of` can throw `InvalidPathException` on exotic values, but
  that surfaces at export time and is not worth special handling in this ticket.
- **Acceptance criteria AC1/AC3 hold.** AC1: the filtered resource is copied to
  the data folder on first enable and equals `output-file: design.json`. AC3:
  `UiDesignerConfigTest` covers default, relative, normalised relative, absolute,
  write-back and "present key is not overwritten"; `UiDesignerPluginTest` covers
  reload and missing-key write-back. Tests are green (4 + 6, 0 failures).
