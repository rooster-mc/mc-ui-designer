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
