# Correctness review — 070 (Command UX polish)

## Round 1

### Verdict
Ship with fixes. The permission move from Brigadier `.withPermission` to
in-executor `hasPermission` is complete and correct — every privileged path is
gated, the console still reaches `reload`/`help`, `save`/`chest-edit` stay
player-only, and the three nodes match `build.gradle.kts`. Tab completion and the
`clear` sentinel wiring work. The one real defect is in the reload readable
probe: it only proves the file is valid YAML, so a present-but-wrong-typed
`output-file` is silently overwritten and reported as a successful reload. The
rest are low-severity edge/robustness items.

### Issues

#### 1. A present-but-wrong-typed `output-file` is silently overwritten and reported as a successful reload (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:46-61`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:13-24`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:114-125,141-146`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:47-48`
- Problem: `readable` only checks that `config.yml` parses as YAML, not that the
  keys are usable. If `output-file` is present with a non-String value (`5`,
  `true`, `null`, a list/map), `explicitOutputFile()` returns `null` because
  `config.get(...) as? String` is null, so `writeDefaultOutputIfBlank()` treats
  the key as *absent*: it rewrites `output-file` to the packaged default and
  `saveConfig()` persists that overwrite. `readable` is `true`, so `reload()`
  returns `ReloadOutcome.Reloaded` and the sender gets the green
  `Reloaded config.yml. Output file: <default>.` The admin's value is discarded
  with a success message — the same false-confirmation class the ticket removed
  for malformed YAML, just one step narrower. (The ux report already notes this
  as issue 3; this is the correctness statement of the same defect.)
- Repro: write `output-file: 5` into `plugins/UiDesigner/config.yml`, run
  `/uidesigner reload` → the file is rewritten to the default path and the
  sender sees a green success; the original `5` is gone. A blank explicit value
  (`output-file: ""`) takes the same path.
- Suggested fix: distinguish "key absent" from "key present but unusable" in
  `UiDesignerConfig` (e.g. `explicitOutputFile(): String?` plus
  `hasUnusableOutputFile(): Boolean`), skip the `writeDefaultOutputIfBlank()`
  rewrite when the key is explicitly set to an unusable value, and map that case
  to a warning outcome (a new `ReloadOutcome` variant, or `UsingDefaults` with
  wording that says the key is invalid rather than absent).

#### 2. `/chest-edit clear` is not robust to surrounding whitespace, so the reserved sentinel can be bypassed (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:62-70`
- Problem: `GreedyStringArgument` returns the raw remaining text with no trim,
  and `apply` compares `rawName.equals("clear", ignoreCase = true)` exactly.
  `/chest-edit  clear` (two spaces before `clear`) yields `rawName = " clear"`
  and names the chest `" clear"`; `/chest-edit clear ` yields `"clear "` and
  names the chest `"clear "`. The command itself suggests `clear`, so a stray
  space silently produces a name instead of clearing, and the `docs/design.md`
  guarantee that a literal name "clear" is unreachable is not airtight for
  whitespace-padded spellings.
- Repro: on a named chest run `/chest-edit  clear` → the reply is
  `Named this chest " clear".` (leading space) and `ChestNamer.nameOf` returns
  `" clear"` instead of `null`.
- Suggested fix: normalise in `apply`, e.g.
  `val name = rawName.trim()` then test `name.isEmpty() ||
  name.equals("clear", ignoreCase = true)` for clear and use `name` for naming.

#### 3. `reload()` catches `Throwable`, so an `Error` on the reload path becomes a chat message (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:114-125`
- Problem: `runCatching { ... }` catches `Throwable`, not `Exception`. A
  `LinkageError`/`NoClassDefFoundError`/`StackOverflowError` raised while
  rebuilding the config is converted into `ReloadOutcome.Failed(...)` and a
  normal chat line, masking a broken environment. `save` deliberately catches
  `Exception` only (`failure(e: Exception)`), so the two paths are inconsistent.
- Suggested fix: mirror `save`: `try { ... } catch (e: Exception) { ... }`, or
  keep `runCatching` but rethrow `Error`.

#### 4. A config-path failure on `/uidesigner save` is reported as a write failure (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:100-105,127-128,138`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:42-45`
- Problem: when `configProvider().outputFile` throws (e.g. `output-file: "\0"`
  → `InvalidPathException`), `save()` returns `WriteFailed(null, reason)` and the
  player sees `Could not write the export: Nul character not allowed.` The
  failure was resolving/parsing the configured path, not writing, so the message
  points the admin at the wrong thing. (`reload()` now handles the same input as
  a reload failure, so `save` and `reload` describe the same misconfiguration
  differently.)
- Suggested fix: add a distinct outcome for "export path unusable" (or reword
  the null-target branch) so the message says the configured path is invalid
  rather than that the write failed.

### Non-issues
- **Every privileged path is gated, with no bypass.** `reload` checks
  `sender.hasPermission(RELOAD_PERMISSION)` (`UiDesignerCommand.kt:59-66`),
  `save` checks `player.hasPermission(SAVE_PERMISSION)` inside the player
  executor (`:71-80`), and `chest-edit` checks
  `player.hasPermission(PERMISSION)` before touching the target
  (`ChestEditCommand.kt:38-42`). The root and `help` are intentionally
  permission-free; `save` and `chest-edit` remain `.executesPlayer`, so
  non-players cannot reach them. `/execute as`/command-block senders are still
  the original sender for permission purposes, so there is no impersonation
  bypass.
- **Console behaviour is correct.** `reload` and `help` use the generic
  `CommandExecutor` and `ConsoleCommandSender.hasPermission` is `true`, so the
  console can reload/print help; only `save` is player-only, as documented.
  Pinned by `console can reload` / `console can print help`.
- **Declared nodes are correct and consistent.** `uidesigner.save`,
  `uidesigner.reload`, `uidesigner.chest-edit` are registered with
  `Permission.Default.OP` in `build.gradle.kts:71-84`; the generated
  `build/generated/plugin-yml/Bukkit/plugin.yml` shows `default: op` for all
  three; the command constants (`UiDesignerCommand.SAVE_PERMISSION` /
  `RELOAD_PERMISSION`, `ChestEditCommand.PERMISSION`) match exactly. Removing
  `.withPermission` no longer hides the nodes at parse time, which is the
  documented choice and what makes the custom denial reachable.
- **Tab completion is correct.** CommandAPI surfaces the registered
  `save|reload|help` literals (`UiDesignerCommandTest` asserts all three), and
  the `/chest-edit` optional greedy argument carries the `clear` suggestion
  (`ChestEditCommand.kt:32-35`). No stale `.withPermission` filter remains.
- **`/chest-edit` optional-argument wiring is sound.** `setOptional(true)` on
  the single trailing greedy argument produces the two expected registrations
  (zero-arg and one-arg); the zero-arg path is reachable via bare
  `/chest-edit`, `args["name"] == null` is a valid absent lookup
  (`CommandArguments.get` returns `null` for a missing node), and the usage
  branch returns before `targetResolver` is called, so a bare command never
  raycasts or mutates the chest. The `clear` sentinel still clears
  case-insensitively.
- **The reload readable/unreadable mapping is right for the cases it detects.**
  A malformed YAML file makes `readable` false, `reload()` returns
  `UsingDefaults`, and the yellow `config.yml could not be read; using defaults`
  line is accurate because `reloadConfig()` leaves the in-memory config on the
  packaged defaults while the file is untouched. A missing file counts as
  readable and is recreated by `writeDefaultOutputIfBlank()`/`saveConfig()`, so
  the green success is not wrong there.
- **No regression against `docs/data-format.md` or the capture/export
  pipeline.** The diff does not touch `model`, `capture`, `export`, or the
  `save` pipeline; `named.size` is still the grouped-design count (double counts
  once), names are still read at each `UiChest` canonical position, and
  `JsonExporter` remains the single ordering/format authority. Casing, ordering,
  omitted fields, and the blank-name rules are unchanged.
- **Main-thread discipline is unchanged.** `save`/`chest-edit` run on the
  player executor and `reload`/`help` on the command executor, all on the server
  main thread; the small synchronous file IO is the documented MVP trade-off.
- **`/chest-edit clear` on an already-unnamed chest still reports success**
  (`apply` returns `Cleared` without checking the current name) and
  `chestEditNotAChest()` says "to name it" on the clear path. These are the ux
  report's issues 1-2; they are wording/false-confirmation concerns, not
  technical breakage, so I am not duplicating them here.

## Round 2

### Verdict
Ship. All four round-1 correctness findings are resolved in `5e1199c`, the
split `/chest-edit` outcomes and the new `ReloadResult`/`SaveOutcome` variants
are wired correctly, and I found no new correctness regression. Permission
enforcement, the reload status mapping, and the capture/export pipeline are
unaffected.

### Issues
None. No blocking or medium/low correctness issue remains.

### Non-issues

- **Round-1 issue 1 (wrong-typed `output-file`) resolved for non-String values.**
  `UiDesignerConfig.hasUnusableOutputFile()` (`UiDesignerConfig.kt:13-14`) is
  `config.isSet(OUTPUT_FILE_KEY) && config.get(OUTPUT_FILE_KEY) !is String`.
  Bukkit's `isSet` uses `get(path, null)` and ignores defaults, so a key set to
  an int/bool/list/map is caught while an absent key is not; `writeDefaultOutputIfBlank()`
  early-returns `false` for it (`:17`) and `reloadConfiguration()` returns
  `ReloadResult.InvalidOutput` without calling `saveConfig()` (`UiDesignerPlugin.kt:61-67`),
  so the file is left byte-for-byte unchanged and the green success is replaced
  by the yellow `reloadInvalidOutput` line. Pinned by
  `UiDesignerPluginTest` (`reload leaves a non-string output file unchanged…`)
  and `UiDesignerConfigTest` (`non-string key is not overwritten` /
  `non-string key is reported as unusable`).
- **Blank/`null` `output-file` is intentionally treated as absent, not a residual
  false confirmation.** Round 1 named `output-file: ""` in the same breath as
  non-String values, but a blank or explicit-null value carries no path intent:
  `explicitOutputFile()` already treats it as absent, the default is written
  back, and the "Reloaded config.yml. Output file: <default>." line is accurate
  (the plugin does use the default). `UiDesignerConfigTest` pins this as the
  intended contract (`blank key is written back from the default`,
  `absent blank and string keys are not reported as unusable`). I am not
  re-raising it as a defect.
- **A String path with an invalid character is still a distinct red failure, not
  a false success.** `output-file: "\0"` passes `hasUnusableOutputFile()` (it is
  a String), so `reloadConfiguration()` returns `Reloaded`; the following
  `configProvider().outputFile` throws `InvalidPathException`, which the outer
  `catch (e: Exception)` maps to `ReloadOutcome.Failed` and
  `"Could not reload config.yml: Nul character not allowed."`. That is a red
  failure with the real reason, so there is no silent overwrite. It differs from
  the `InvalidOutput` wording, but the outcome is not wrong and this behaviour
  predates the fix.
- **Round-1 issue 2 (whitespace-padded sentinel) resolved.** `apply` trims once
  (`ChestEditCommand.kt:73`) and compares the trimmed `name`, so `"  clear  "`
  clears and `"  Shop  "` names `"Shop"`; `args["name"]` is still the raw
  `GreedyStringArgument` value, but the trim happens before any comparison or
  write. Pinned by `apply trims a whitespace-padded clear sentinel`,
  `apply trims a whitespace-padded name`, and the dispatch test
  `chest-edit  clear `.
- **Round-1 issue 3 (`Throwable` on reload) resolved.** `reload()` now uses
  `try { … } catch (e: Exception) { … }` (`UiDesignerCommand.kt:127-138`), so an
  `Error` on the reload path propagates instead of becoming a chat line, matching
  `save`'s `Exception`-only handling. The caught set is otherwise unchanged, so
  the normal failure mapping still works.
- **Round-1 issue 4 (config-path failure on save) resolved.** The
  `configProvider().outputFile` access is now caught separately and returns
  `SaveOutcome.InvalidOutputFile(e.message ?: "check the output-file setting")`
  (`UiDesignerCommand.kt:113-118`), rendered red by `Messages.invalidOutputFile`
  (`Messages.kt:51-55`) and handled exhaustively in `saveMessage` (`:152`). A
  throwing provider no longer masquerades as `WriteFailed(null, …)`.
- **New `/chest-edit` outcomes are correct and exhaustive.** `apply` returns
  `NoTarget` for a null raycast before `NotAChest` for a non-chest block
  (`ChestEditCommand.kt:71-72`), and `NothingToClear` only when the target is a
  chest whose `ChestNamer.nameOf` is null (`:74-75`). The dispatch `when`
  covers all five variants (`:50-58`), so the sealed hierarchy is exhaustive and
  no branch falls through. `nameOf` reads both halves of a double chest via
  `firstNotNullOfOrNull`, so a named half still clears correctly and an unnamed
  pair reports `NothingToClear`; a blank custom name is normalised to null by
  `readName` and therefore also counts as nothing to clear, which is consistent.
- **Permission enforcement is unchanged and still complete.** The only change is
  `Messages.noPermission(node)` receiving each command's own constant
  (`UiDesignerCommand.kt:75,88`; `ChestEditCommand.kt:44`); the guards remain
  `sender.hasPermission(RELOAD_PERMISSION)` for reload, `player.hasPermission(SAVE_PERMISSION)`
  for save, and `player.hasPermission(PERMISSION)` before `targetResolver` in
  `/chest-edit`, so the bare-command path still never raycasts. `save`/`chest-edit`
  stay `.executesPlayer`; `help`/root stay permission-free for the console.
- **Reload status mapping is complete.** The three `ReloadResult` values map
  one-to-one to `Reloaded`/`UsingDefaults`/`InvalidOutput`, and any thrown
  `Exception` maps to `Failed` (`UiDesignerCommand.kt:131-137`); the `when` in
  `reloadMessage` is exhaustive (`:155-161`). The `InvalidOutput` case reports
  the resolved default path, which is exactly the path `uiConfig.outputFile`
  will use, so the message and behaviour agree.
- **No regression against `docs/data-format.md` or the capture/export
  pipeline.** The diff touches only `UiDesignerPlugin`, the two command classes,
  `UiDesignerConfig`, the new `ReloadResult`, and `Messages`; `model`, `capture`,
  `export`, and `JsonExporter` are untouched. `save` still captures, groups,
  reads names at each `UiChest` canonical position, and passes the grouped list
  to the exporter, so casing, ordering, omitted fields, the blank-name rule, and
  the double-chest-counts-once count are unchanged.
- **Main-thread discipline is unchanged.** All new work (config probe, warning
  logging, `saveConfig`, message rendering) happens inside the same command
  executors as before, on the server main thread; no async/off-thread access was
  introduced.
