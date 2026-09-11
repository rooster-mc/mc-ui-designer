# UX review — 060 (/uidesigner save export command)

## Round 1
### Verdict
Ship with fixes (all low severity). The loop is tight — one command from a FAWE
selection to JSON on disk — and every outcome (no selection, no chests, success,
IO failure) is a single chat line with no stack trace, matching the use case in
`docs/design.md`. The fixes are wording/feedback polish: the write-failure message
drops the path, `reload` claims success even when `config.yml` could not be read,
and the success unit "chest design(s)" is easy to misread as a block count.

### Issues

#### 1. Write-failure message omits the target path (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:107-108`
  (and `:83-85`)
- Problem: `"Could not write the export: ${outcome.reason}"` never names the file,
  so the player cannot tell *which* configured path failed — while the success
  path does show it (`:101`). For `IOException("disk full")` the reason is the
  only clue; when `e.message` is null the fallback is the class name
  (`e.javaClass.simpleName`, `:84`), e.g.
  `Could not write the export: FileSystemException`, which is not actionable.
- Suggested fix: include the path, e.g.
  `"Could not write the export to ${outcome.outputFile}: ${outcome.reason}"`, and
  consider a plain-language hint for the common case (e.g. "check the output
  folder is writable").

#### 2. `reload` always claims success, even when `config.yml` could not be read (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:88-91,111-112`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:46-60`
- Problem: `reloadMessage` unconditionally prints
  `"Reloaded config.yml. Output file: $outputFile."`. But `reloadConfiguration`
  swallows a parse failure (`canOverwriteFile == false`), logs a warning, leaves
  the file unchanged, and falls back to the bundled defaults — so the player sees
  a confident success line and the *default* output path even though their edit
  was not applied. For a tool whose config edit is the point, that is a false
  confirmation.
- Suggested fix: let the reload action report a status (or have the command check
  whether the file parsed) and message accordingly, e.g.
  `"config.yml could not be read; using defaults. Output file: X."`. If that is
  beyond 060, record it for 070's "review all error paths for actionable wording".

#### 3. "Exported N chest design(s)" is an odd unit and can read as a block count (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:98-102`
- Problem: the count is correct (`named.size`, so a double is one), but
  `"Exported 1 chest design to X."` / `"Exported 2 chest designs to X."` uses a
  non-standard unit and the help text says "export the selected chests to JSON"
  (`:117`). A player who placed a double plus a single and sees
  `Exported 2 chest designs` may think a chest is missing (3 blocks). The word
  "design" does hint at the grouping, but "chest" sits immediately before it as
  if it were the counted noun.
- Suggested fix: make the unit explicit and single-noun, e.g.
  `"Exported 2 designs (double chests count once) to X."`, and use the same noun
  in the help line. If the block count is available, `"Exported 2 designs
  (3 chest blocks)"` removes all doubt.

#### 4. "The selection contains no chests." is terse and can be wrong for unloaded chunks (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:106`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/capture/ChestScanner.kt:15`
- Problem: the scanner silently `continue`s on unloaded chunks, so a selection
  that extends past loaded terrain can genuinely contain chests yet still report
  this line. Even when accurate, the message gives no next step.
- Suggested fix: add a short hint, e.g.
  `"The selection contains no chests. Place chests inside the selected region
  (loaded chunks only)."`

### Non-issues
- **Loop tightness.** From "I have a selection" to "JSON on disk" is exactly one
  command (`/uidesigner save`, `UiDesignerCommand.kt:44-51`); no confirmation
  step, no second command, no chat prompt. Matches `docs/design.md` use-case
  steps 3-5.
- **No-selection message.** `"No WorldEdit selection. Select a region first."`
  (`:104-105`) names the tool, states the problem, and tells the player what to
  do. Actionable and distinct from the empty-selection case.
- **Success shows count and path.** `:98-102` includes both the number of designs
  and the written path, so the player knows the action happened and where the
  file went.
- **Failure modes are all handled without a trace.** No selection, no chests and
  an IO failure each map to a sealed `SaveOutcome` (`:26-39`) and one line of
  feedback; the exporter's atomic write means a failed save leaves the previous
  file intact (acceptance criterion), so the player is never left with a corrupt
  export.
- **Alias, bare command, and help.** `/uid` is registered (`:43`), bare
  `/uidesigner` and `/uidesigner help` both print the three-line help
  (`:60-67,114-120`), and `help` needs no permission (`:61`), so the command is
  discoverable. The test file pins all three.
- **Presentation consistency with `/chest-edit`.** Both commands send a single
  plain `Component.text` line with a trailing period and no prefix
  (`ChestEditCommand.kt:38-45`; `UiDesignerCommand.kt:96-120`). Nothing here
  regresses 070's planned centralised prefix/colour work.
- **Deferred items.** Tab completion, colour/prefix, and graceful
  permission-denial wording are explicitly ticket 070 (`docs/tasks/070-ux-polish.md`)
  and `060` introduces nothing misleading about them: the help lists `save` and
  `reload` without claiming they are open to everyone, and permission denial is
  CommandAPI's standard handling (the test at `UiDesignerCommandTest.kt:216-230`
  only asserts denial).
- **`/chest-edit` "look at nothing" path.** Out of 060's scope; it was already
  reviewed under 050 and remains 070's error-wording pass.

## Round 2
### Verdict
Ship. All four round-1 UX findings are resolved in the current tree, no new
player-facing regression was introduced, and the console-capable `reload`/`help`
paths send through `CommandSender` as intended. The remaining polish (prefix,
colour, tab completion, explicit denial wording, and the `reload`
false-success) is correctly deferred to 070.

### Issues
None. The round-1 items now read as follows.

#### 1. Write-failure names the target (round 1 #1 — resolved)
- `UiDesignerCommand.kt:119-122`:
  `val target = outcome.outputFile?.let { " to $it" } ?: ""` then
  `Component.text("Could not write the export$target: ${outcome.reason}")`.
  An exporter failure now prints
  `Could not write the export to /path/design.json: disk full` — path plus
  reason, matching the detail level of the success line. The config-path failure
  keeps the path-less form (`Could not write the export: missing default
  output`), which is right because no path is known; that reason wording belongs
  to 070's error pass. The optional "check the output folder is writable" hint
  was not added, but it is not required now that the path is present.

#### 2. Success wording calls out double-counting (round 1 #3 — resolved)
- `UiDesignerCommand.kt:105-111`:
  `"Exported ${outcome.chests} chest $noun to ${outcome.outputFile} (a double chest counts once)."`
  A double plus a single now reads
  `Exported 2 chest designs to /path/design.json (a double chest counts once).`
  The parenthetical removes the "3 chest blocks → 2" ambiguity, and the help
  line uses the same noun (`/uidesigner save - export the selected chest designs
  to JSON`, `:131`). "chest design" is still slightly non-standard, but it is
  internally consistent and no longer misreadable.

#### 3. No-chests message is actionable (round 1 #4 — resolved)
- `UiDesignerCommand.kt:114-118`:
  `"The selection contains no chests. Place chests inside the selected region (loaded chunks only)."`
  It gives a next step and covers the unloaded-chunk case the scanner silently
  skips (`ChestScanner.kt:15`), so the message is no longer wrong for a
  selection that extends past loaded terrain.

#### 4. `reload`/`help` are console-capable (round 2 check — fine)
- `UiDesignerCommand.kt:44-46,58-63`: both executors are
  `CommandExecutor { sender, _ -> sender.sendMessage(...) }` and both
  subcommands use `.executes(...)`, so console reaches them; `save` correctly
  stays `executesPlayer` (it needs a player selection). The tests
  `console can reload` and `console can print help`
  (`UiDesignerCommandTest.kt:313,349`) pin this. Console sees
  `Reloaded config.yml. Output file: /path.` and the same three-line help.

### Non-issues
- **Player loop unchanged and tight.** From "I have a selection" to "JSON on
  disk" is still one command with no confirmation prompt; the four outcomes
  (success, no selection, no chests, IO failure) each map to a single plain chat
  line with no trace, matching `docs/design.md` use-case steps 3-5.
- **No-selection message unchanged and fine.**
  `"No WorldEdit selection. Select a region first."` (`:112-113`) still names
  the tool, the problem, and the action, and stays distinct from the
  empty-selection line.
- **Presentation stays consistent with `/chest-edit`.** Both commands use a
  single plain `Component.text` line with no prefix or colour, sentence case,
  and a trailing period (`ChestEditCommand.kt:38-45`; `UiDesignerCommand.kt:103-134`).
  The write-failure line ends with the raw reason (no guaranteed period), a tiny
  inconsistency that 070's message centralisation will absorb; it does not
  mislead.
- **Fixes did not regress anything.** The success count is still grouped
  `named.size`, the no-selection/empty-config early returns are untouched, and
  the exporter's atomic write still leaves the previous file intact on failure
  (`JsonExporter.kt:21-33`; pinned by
  `JsonExporterTest.a failed export leaves the previous file intact`).
- **Deferred and not re-litigated.** The `reload` false-success (round 1 #2) is
  recorded for 070's error-wording pass; tab completion, colour/prefix, and
  explicit permission-denial wording remain 070. The manual FAWE end-to-end walk
  is still unverified and must be recorded before `done`.
