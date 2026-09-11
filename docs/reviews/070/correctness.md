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
