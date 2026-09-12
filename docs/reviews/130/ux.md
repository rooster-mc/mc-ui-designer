# Ux review — 130 (Consume rooster-commands root executor and suggestion dedupe)

## Round 1
### Verdict
Ship. The player-facing loop, feedback truthfulness and discoverability are
unchanged by this diff: the root `onExecute` handlers reproduce the old bare
`/chest-edit` usage, console no-op and `/uidesigner`/`uid` help, and `clear` is
still offered exactly once in tab completion. No UX findings.

### Findings
No findings.

### Non-findings
- **Bare `/chest-edit` usage is preserved verbatim and reaches the player.**
  `ChestEditCommand.kt:40-43` sends `usageMessage()` for a player and returns
  silently for anyone else; the text at `:81-86` is unchanged:
  `"Usage: /chest-edit <name> - name the chest you are looking at, or
  /chest-edit clear to remove the name."` It is `infoColor` (yellow), so it does
  not read as a success or an error, and it does not touch the targeted block
  (`bare chest-edit prints usage and does not name the chest`,
  `ChestEditCommandTest.kt:160-172`).
- **Console bare `/chest-edit` stays a silent no-op.** `playerOrNull` is
  `sender as? Player` (verified by correctness), so the root handler returns
  before sending. This matches the design decision (`docs/design.md:87-90`:
  "a non-player sender (console) is silently ignored rather than answered,
  uniformly") and is pinned by `ChestEditCommandTest.kt:174-182`. Silent console
  is intentional here, not a dropped message.
- **`/uidesigner` and `/uid` help are unchanged.** `UiDesignerCommand.kt:69`
  sends `helpMessage()` to `sender` (works for player and console);
  `helpMessage()` at `:139-150` still lists `save`, `reload`, `/chest-edit
  <name>` and `help`, all in `infoColor`. The `uid` alias is still attached to
  the same tree (`:76`) and the bare/`help`/alias paths are covered at
  `UiDesignerCommandTest.kt:452-477`.
- **Tab-completion discoverability is intact and truthful.** With no argument
  the `/uidesigner` literals still complete (`tab completion lists the uidesigner
  subcommands`, `UiDesignerCommandTest.kt:479-491`), and `/chest-edit` offers
  `clear` (`ChestEditCommandTest.kt:184-190`). The partial-name criterion this
  ticket calls out is pinned by `tab completion suggests clear for a partial
  name` (`:192-198`): `"chest-edit cl"` resolves to exactly `["clear"]`, so the
  player sees the value once, not duplicated — the criterion's wording.
- **The `.suggestStrings { listOf("clear") }` contributes nothing the player can
  observe, and that is not a UX problem.** The compiler runs the greedy
  provider through `excludingLiterals(siblingLiterals)` (`Compiler.kt:163-171`,
  `:207`), so the only visible `clear` comes from the `clear` literal sibling.
  A player's completion is identical with or without the call; there is no
  duplicate, no missing entry and no misleading extra suggestion. This is a
  library-consumption/architecture concern (already covered by the earlier
  reports), not a player-facing one.
- **Feedback truthfulness holds across every outcome.** The outcome mapping at
  `ChestEditCommand.kt:58-65` keeps every failure distinct and non-success:
  `"This chest has no name."` (info) for a no-op clear, `"Not looking at a chest
  (or it is out of reach)."` (error) and `"That block is not a chest."` (error),
  versus green `"Named this chest \"$name\"."` / `"Cleared this chest's name."`.
  No message claims success for a no-op or a failure. The `/uidesigner save`
  failure set (`NoSelection`, `NoChests`, `WriteFailed`, `InvalidOutputFile`,
  `UiDesignerCommand.kt:121-136`) is likewise truthful and pre-existing. Failure
  hints (`WRITE_FAILURE_HINT`, `RELOAD_FAILURE_HINT`, `INVALID_OUTPUT_HINT`,
  `:146-148`) tell the player what to check rather than echoing a raw exception.
- **The action loop is unchanged: three steps (look at chest → `/chest-edit
  <name>` → green confirmation; or `/chest-edit clear` / tab-completed `clear`
  → confirmation).** The diff moves wiring only; no new step, prompt or surprise
  is introduced, and the `clear` suggestion still saves the player from guessing
  the reserved word's spelling/casing.
- **No permissions in the loop.** Consistent with `docs/design.md:85-86`; the
  diff adds no permission node or check, so there is no "you lack permission"
  dead end to explain.
- **Manual gate: no new `docs/manual-test.md` entry is needed for 130; MT-007 is
  sufficient.** The only acceptance clauses beyond the automatable build/lint/grep
  ones are command behaviour and the partial-name/once-only suggestion, and the
  tester established that the CommandAPI test toolkit drives the real Brigadier
  dispatcher, so those are automated here (`ChestEditCommandTest.kt:192-198`).
  MT-007 (`docs/manual-test.md:18`) already exercises the live end-to-end
  variants this diff could affect (`bare /chest-edit` usage, console silent
  no-op, any-casing `clear`, `/uidesigner` and `uid`, suggestions offering
  `clear`). Since the diff changes no message text and no routing outcome, no new
  non-automatable criterion is introduced. I concur with the tester and
  architecture reports on this point.
- **I add nothing to, and do not dissent from, the earlier reports.** The tester
  and correctness findings are about test cardinality and dispatch wiring; the
  architecture finding is about the library seam; the readability finding is a
  prose wrap in `docs/architecture.md`. None is a player-facing loop, feedback or
  discoverability issue, and I do not re-report them.
