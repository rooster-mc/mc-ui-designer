# ux review — 150 (Require unique chest names for export)

## Round 1

### Verdict

The fail-closed loop is clear and truthful: `save` aborts with a red
`[UiDesigner]` message that names the offending chest positions and the command
to fix them, `/chest-edit` blank input prints the same usage as the bare command
and leaves the name untouched, `clear` is now an ordinary name with a truthful
confirmation, and the help/usage text drops the removed `clear` mention. Two
small feedback gaps remain: a selection that is both unnamed and duplicated fails
in two save cycles instead of one, and the duplicate message never says the
comparison ignores case and surrounding whitespace, so a player who used `Shop`
and `SHOP` cannot see *why* they collide.

### Findings

#### 1. Unnamed and duplicate failures are reported one at a time, costing an extra save cycle

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:107-113`
- Problem: `validateForExport(named)` already returns both
  `validation.unnamed` and `validation.duplicates`, but `save` returns on the
  first non-empty one (`:108-113`). A selection that contains, say, one unnamed
  chest and two colliding names gets only
  `"Cannot export: 1 chest has no name. Name it with /chest-edit <name>: (2, 0, 0)."`
  The player names that chest, re-runs `save`, and only then discovers
  `"Cannot export: chest names must be unique. Duplicates: \"Shop\" at (0, 0, 0), (2, 0, 0)."`
  That is an avoidable second trip through the same failure path for information
  the validator already had, which is exactly the awkward step in the
  selection-to-JSON loop.
- Suggested fix: when both lists are non-empty, report both in the one red
  message (e.g. append the duplicate clause to the unnamed message, or emit a
  combined "unnamed: …; duplicates: …" body), reusing the existing sentence
  builders. Keep returning before `configProvider()`/`exporter(...)` so no file
  is written.

#### 2. Duplicate message does not say duplicate detection ignores case and surrounding whitespace

- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:233-242` (message) and `src/main/kotlin/dev/cypdashuhn/uidesigner/export/ExportValidation.kt:22-28` (comparison)
- Problem: names are grouped by `trim().lowercase()`, but the player-facing text
  is only `"Cannot export: chest names must be unique. Duplicates: \"Shop\" at (0, 0, 0), (2, 0, 0)."`
  and `DuplicateNameGroup.name` is a single spelling (`group.first().name`). A
  player who deliberately named one chest `Shop` and another `SHOP` (or one
  `" Shop "`) reads a message that shows `"Shop"` beside two positions and has no
  way to tell why the tool considers them the same name; the most likely reading
  is that the exporter mismatched or is wrong. The message states the rule but
  not the *why*, which is the part this ticket makes mandatory.
- Suggested fix: add a short parenthetical to the message, e.g.
  `"…names must be unique (compared ignoring case and surrounding spaces)…"`,
  and/or carry each group's colliding spellings so the message reads
  `"Shop" at (0, 0, 0), "SHOP" at (2, 0, 0)`. Either makes the collision
  self-evident; both keep the message one red component.

### Non-findings

- **The unnamed message is truthful and actionable.** `unnamedChestsMessage`
  (`UiDesignerCommand.kt:222-231`) uses `errorColor`, the shared aqua
  `[UiDesigner]` prefix via `Messages.styled`, correct singular/plural
  (`"1 chest has no name… Name it with…"` vs `"N chests have no name… Name
  them with…"`) and lists every canonical position as `(x, y, z)` via the same
  `coords()` helper as the clipped-chest error. It is returned before
  `configProvider()` and `exporter(...)` are called (`:107-125`), so it cannot
  claim success or leave a stale file, and `saveSuccessMessage` is only reachable
  from `Exported`. The remediation (`/chest-edit <name>`) matches the actual
  command and help text.
- **Blank `/chest-edit` input is handled without touching the chest.**
  `apply` trims and returns `Outcome.BlankName` before `ChestNamer.setName`
  (`ChestEditCommand.kt:56-63`), and `outcomeMessage` maps it to
  `usageMessage()` (`:48-54`), so the text is
  `"Usage: /chest-edit <name> - name the chest you are looking at."` in
  `infoColor` (yellow) — the same line and colour as bare `/chest-edit`
  (`:36-39`). It reads as guidance, not as success or a hard error, and the
  previous name survives (pinned by
  `ChestEditCommandTest.apply treats a blank name as usage and leaves the name
  unchanged`). Trimming means a whitespace-only name can never be stored through
  the command, so the validator's blank-name path is not player-reachable in a
  way that could contradict the usage message.
- **`clear` as an ordinary name is truthful.** `ChestEditCommand.kt:56-63` now
  sets `"clear"` like any other name, and the success line is the green
  `"Named this chest \"clear\"."` (`:72-73`). No player-facing `clear` sentinel,
  `clearedMessage`, or `nothingToClearMessage` remains anywhere under `src/main`
  (grep confirms only `ChestNamer.clear` internals), so the removed behaviour is
  not suggested or half-removed.
- **Help and usage text are consistent.** `HELP_TEXT`
  (`UiDesignerCommand.kt:165-170`) and `usageMessage()`
  (`ChestEditCommand.kt:66-70`) both say only
  `"/chest-edit <name> - name the chest you are looking at."`; the stale
  `"or /chest-edit clear to remove the name."` clause is gone from both, so a
  player reading help cannot be pointed at a command that no longer exists.
- **Discoverability is intact where it should be.** The `ui`/`uid` alias,
  bare `/uidesigner` and `/uidesigner help` are untouched and still list `save`,
  `reload`, `/chest-edit <name>`, `help`. `/chest-edit` now offers no
  suggestions for the freeform greedy `name` (the `clear` literal and its
  suggestion are gone), which is the expected completion behaviour for an
  arbitrary string; `uidesigner` subcommand completion is unaffected. The new
  hard rule (names required and unique) has no in-game mention before the first
  `save`, but the failure message is unavoidable, names the positions, and gives
  the fixing command, so the player is never blocked without an explanation;
  adding the rule to the terse help text would be optional polish, not a gap I
  would stand behind as work.
- **Target precedence on blank input is acceptable.** `apply` checks
  `NoTarget`/`NotAChest` before `BlankName` (`:57-60`), so a blank argument
  while looking at a non-chest reports `"That block is not a chest."` rather
  than usage. That is the more specific and more useful diagnosis (the player
  is aiming at the wrong block), and it is consistent with every other
  `/chest-edit` invocation.
- **Other failure modes unchanged and still truthful.** No selection
  (`"No WorldEdit selection. Select a region first."`), no chests
  (`"The selection contains no chests. …"`), clipped double chests, write
  failure (`"Could not write the export to <path>: …"`) and unusable output path
  messages are untouched by this diff; the new validation short-circuits before
  the output path is resolved, matching the clipped-chest gate that precedes it.
- **Manual gate covers the new criteria.** `docs/manual-test.md` MT-012 records
  the live-FAWE unnamed/duplicate/`clear`/blank-arg checks for 150, and MT-007
  and MT-009 were updated to drop the removed `clear` suggestion. The remaining
  non-automatable parts (real selection, live Brigadier tree) are therefore
  tracked rather than lost.
