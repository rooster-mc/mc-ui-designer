# UX review — 070 (Command UX polish)

## Round 1
### Verdict
Ship with fixes. The headline deferred item — the `/uidesigner reload`
false-success wording — is genuinely fixed: `reloadConfiguration()` now reports
whether `config.yml` was readable, and an unreadable file produces
`config.yml could not be read; using defaults. Output file: X.` instead of a
confident `Reloaded config.yml`. All four acceptance criteria are met
(bare `/uidesigner` prints help, tab completion lists subcommands, denial is a
prefixed chat line with no trace, and every message shares the aqua prefix plus
a green/red/yellow body). The remaining findings are error-wording polish that
050 and 060 explicitly handed to this ticket, plus one residual false-success
on a semantically bad `output-file`.

### Issues

#### 1. `/chest-edit` failure message is wrong for the clear path and for non-chest blocks (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:64`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:49-50,62-70`
- Problem: `chestEditNotAChest()` is the single line
  `"Look at a chest to name it."` but `Outcome.NotAChest` covers three distinct
  situations: `targetResolver` returned null (looking at air or beyond the
  `REACH = 5` raycast), the block in range is not a chest (e.g. a barrel or
  stone), and the `clear` path aimed at a non-chest. A player running
  `/chest-edit clear` while looking at air is told to look at a chest "to name
  it" — the wrong verb for the action they asked for — and a player aiming at a
  barrel gets no clue why a chest-like block was rejected. 050 round 2 deferred
  this to 070's "review all error paths for actionable wording", so it is in
  scope now.
- Suggested fix: at minimum neutralise the verb: `"Look at a chest to name or
  clear it."` Better, split the outcome so "nothing / too far" and "that block
  is not a chest" read differently (optionally naming the block type), e.g.
  `"Not looking at a chest (or it is out of reach)."` vs
  `"That block is not a chest."` Splitting the sealed `Outcome` is a small
  wording change, not new functionality.

#### 2. `/chest-edit clear` on an already-unnamed chest reports success (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:62-70`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:62`
- Problem: `apply` returns `Outcome.Cleared` for any chest whose input is blank
  or `clear`, without checking `ChestNamer.nameOf(target)`, so clearing an
  unnamed chest prints `Cleared this chest's name.` for a no-op. It is a false
  confirmation, and it also masks the designer who typed `clear` intending to
  name the chest "Clear". 050 round 2 deferred this to 070's error-path pass.
- Suggested fix: read the current name first and reply e.g.
  `"This chest has no name."` when there is nothing to clear; only emit
  `Cleared this chest's name.` when a name was actually removed.

#### 3. A semantically invalid `output-file` is silently overwritten and reported as a successful reload (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:46-61`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/config/UiDesignerConfig.kt:19-24`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:47-48`
- Problem: the `readable` probe only checks that the file is valid YAML, not
  that the keys are usable. If `config.yml` parses but `output-file` is present
  with the wrong type (e.g. `output-file: 5`), `explicitOutputFile()` returns
  null, `writeDefaultOutputIfBlank()` rewrites the key to the default and
  `saveConfig()` persists that overwrite — while the player sees the green
  `Reloaded config.yml. Output file: <default>.` line. Their edit is discarded
  and the feedback claims success. This is the same class of false confirmation
  the ticket was opened to remove, just one step narrower than the malformed-YAML
  case that was fixed.
- Suggested fix: have `UiDesignerConfig` distinguish "key absent" from "key
  present but unusable" (or return a status from the reload action) so the
  reload outcome can warn, e.g. `"output-file in config.yml is not a valid path;
  using defaults. Output file: X."`, and skip the rewrite when the key was
  explicitly set to an unusable value. If this is judged out of 070's wording
  scope, record it as a follow-up rather than leaving the silent overwrite.

#### 4. Error reasons can surface as bare exception class names (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:42-45,56-57,87`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:124,128`
- Problem: `writeFailed`/`reloadFailed` render
  `e.message ?: e.javaClass.simpleName`. When a filesystem exception has no
  message, the player sees `Could not write the export to /path: FileSystemException.`
  — a class name, not a next step. 060 round 2 explicitly deferred this to 070's
  "review all error paths for actionable wording".
- Suggested fix: keep the raw reason when present, but fall back to a
  plain-language hint, e.g. `"Could not write the export to X: check that the
  output folder exists and is writable."` Similarly for reload failures.

#### 5. `/uidesigner help` hides `/chest-edit` and advertises commands the sender cannot run (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:16-20`
- Problem: `help` is the permission-free discovery surface (design.md:74), yet
  its three lines cover only `/uidesigner save|reload|help`. `/chest-edit` —
  use-case step 6 and the only way to name a chest — is invisible unless the
  player already knows it exists. Conversely, a non-op who runs `/uidesigner
  help` sees `save` and `reload` listed with no hint they are op-only, then gets
  `You do not have permission to use this command.` on trying one. This is the
  one place the ticket's "clear help" goal is not fully realised.
- Suggested fix: add a `/chest-edit <name>` line to `HELP_TEXT`, and annotate or
  filter the privileged lines (a static note like `(op)` is enough; per-sender
  filtering is optional).

#### 6. Denial message does not name the required permission node (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:73-74`
- Problem: `noPermission()` is `"You do not have permission to use this
  command."` for all three commands, so an operator configuring the plugin
  cannot tell whether they are missing `uidesigner.save`, `uidesigner.reload`,
  or `uidesigner.chest-edit` without opening `plugin.yml`. The denial is clear
  but not as actionable as the ticket's error pass allows.
- Suggested fix: parameterise it, e.g.
  `noPermission(node)` → `"You do not have permission to use this command
  (uidesigner.save)."`, and pass each command's constant.

### Non-issues
- **The deferred 060 reload false-success is fixed.** `reloadConfiguration()`
  returns `readable` (`UiDesignerPlugin.kt:46-61`) and `UiDesignerCommand.reload`
  maps it to `ReloadOutcome.Reloaded` / `UsingDefaults`
  (`UiDesignerCommand.kt:114-125`). A malformed `config.yml` now produces the
  yellow `config.yml could not be read; using defaults. Output file: X.`
  (`Messages.kt:50-54`) rather than a green success, so the player is no longer
  falsely told their edit applied. The malformed-file path is pinned by
  `UiDesignerPluginTest` (`reloadConfiguration` returns false, file untouched)
  and the message by `UiDesignerCommandTest` and `MessagesTest`.
- **Loop tightness.** From "I have a selection" to "JSON on disk" is still one
  command (`/uidesigner save`) with no confirmation prompt or second step,
  matching `docs/design.md` use-case steps 3-5. `/chest-edit <name>` is one
  command to the GUI-visible name, and `/uidesigner reload` is one command to
  the new path.
- **Success feedback is complete.** `saveSuccess` reports the count and the
  absolute path plus the double-chest note (`Messages.kt:24-30`);
  `chestEditNamed`/`chestEditCleared` confirm the action; `reloadSuccess`
  reports the resulting path. The player always knows what happened and where.
- **Failure wording is actionable on the common paths.** `noSelection` names the
  tool and the next action; `noChests` adds the "loaded chunks only" caveat;
  `writeFailed` includes the target path and reason. No path prints a stack
  trace.
- **Permissions are graceful and consistent.** The three nodes are declared in
  `build.gradle.kts:71-84` with matching constants in the command classes, and
  each executor checks `hasPermission` before acting
  (`UiDesignerCommand.kt:59-66,71-80`; `ChestEditCommand.kt:38-42`), sending the
  red prefixed `noPermission()` instead of a Brigadier parse failure. Console
  still reaches `reload`/`help`, and `save` stays player-only.
- **Prefix and colour are centralised and consistent.** Every player-facing
  component is built by `Messages.styled` (`Messages.kt:76-85`): aqua
  `[UiDesigner] ` prefix, green success, red error, yellow info. `MessagesTest`
  pins prefix, palette and trailing period for all messages, so the
  "consistent colour/prefix" acceptance criterion is enforced rather than
  incidental.
- **Tab completion and help discoverability work.** CommandAPI surfaces the
  `save|reload|help` literals (`UiDesignerCommandTest` asserts all three) and
  `/chest-edit` suggests `clear` for its optional name argument
  (`ChestEditCommand.kt:32-35`; `ChestEditCommandTest`). Bare `/uidesigner`,
  `/uidesigner help`, and the `/uid` alias all print the help.
- **The `/chest-edit` reserved word is now documented to the player.** The bare
  command prints `Usage: /chest-edit <name> - name the chest you are looking at,
  or /chest-edit clear to remove the name.` (`Messages.kt:66-71`), so the
  `clear` sentinel is discoverable without reading `docs/design.md`. The
  remaining "a literal name `clear` is unreachable" limitation is a documented
  spec choice from 050 and new-functionality territory, correctly out of scope.
- **Verbosity is right.** One chat line per command; no action-bar churn, no
  duplicate lines, no spam on repeated saves.
- **The yellow `reloadUsingDefaults` line is appropriate.** It is a warning, not
  a hard failure (the plugin does fall back to a working default), so yellow
  rather than red is a reasonable signal; the wording already says
  `could not be read`.
