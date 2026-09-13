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

## Round 2

### Verdict

Ship. Both round-1 findings are resolved in `507ac95`: `save` now returns a
single `SaveOutcome.InvalidNames` carrying `unnamed` and `duplicates`
together, and the combined `invalidNamesMessage` reads as one coherent,
truthful red `[UiDesigner]` sentence — it still names every position and the
fixing command, now adds the case/whitespace comparison rule, and lists each
colliding spelling with its own position. I found no new player-facing loop,
feedback-truthfulness or discoverability problem in the fix.

### Findings

None. My two round-1 findings are both fixed and need no re-derivation:

- Finding 1 (extra save cycle when a selection is both unnamed and duplicated)
  is closed by `UiDesignerCommand.kt:104-107` returning `InvalidNames` with both
  lists, and `invalidNamesMessage` (`:215-221`) joining both clauses.
- Finding 2 (the message never explained the case/whitespace comparison and
  showed one spelling) is closed by the
  `"Chest names must be unique (compared ignoring case and surrounding spaces)."`
  sentence and the per-entry `"<name>" at (x, y, z)` list (`:232-239`).

### Non-findings

- **The combined message is grammatically coherent and complete.** The two
  clauses are built by `unnamedClause`/`duplicateClause` (each returns `null`
  when its list is empty) and joined with a single space inside
  `"Cannot export: $body"`, so the player sees only the relevant clauses and
  never a dangling `"Cannot export: "` (at least one list is non-empty whenever
  `InvalidNames` is returned). Rendered examples, all with the aqua prefix and
  red body: only unnamed →
  `"Cannot export: 1 chest has no name. Name it with /chest-edit <name>: (4, 0, 0)."`;
  only duplicates →
  `"Cannot export: Chest names must be unique (compared ignoring case and surrounding spaces). Duplicates: \"Shop\" at (0, 0, 0), \"SHOP\" at (2, 0, 0)."`;
  both →
  `"…1 chest has no name. Name it with /chest-edit <name>: (4, 0, 0). Chest names must be unique (compared ignoring case and surrounding spaces). Duplicates: …"`.
  Each clause ends in one period, so there is no doubled or missing punctuation.
- **The fix makes the failure more truthful without becoming noisy.** The
  previous duplicate text named one spelling for a group that normalised two
  (or more) spellings to the same key; the new `"…" at (x, y, z)` list shows the
  actual pair, so a player who typed `Shop` and `SHOP` sees both and the
  parenthetical explains why they collide. It is still one chat component (not
  one line per chest), so the volume is unchanged in kind, and the unnamed
  clause keeps its correct singular/plural
  (`"1 chest has…"` / `"N chests have…"`, `"it"`/`"them"`).
- **No success can be claimed from the failure path.** `save` returns
  `InvalidNames` at `UiDesignerCommand.kt:104-107`, before
  `configProvider().outputFile` (`:108-113`) and before `exporter(...)`
  (`:114-119`), so the output path is not resolved and no file is written or
  overwritten; `saveSuccessMessage` remains reachable only from `Exported`. The
  round-1 tester's new throwing-config tests pin exactly this ordering for both
  the unnamed and duplicate branches.
- **The `/chest-edit` surface is untouched by the fix and still correct.**
  Blank input still maps to the info-colour usage line and leaves the existing
  name in place (`ChestEditCommand.kt:56-70`), `clear` is still an ordinary name
  with the truthful green `"Named this chest \"clear\"."`, and `HELP_TEXT`
  (`UiDesignerCommand.kt:158-163`) and `usageMessage()` still say only
  `"/chest-edit <name> - name the chest you are looking at."`. No player-facing
  `clear` sentinel remains under `src/main`.
- **Discoverability is unchanged and adequate.** `ui`/`uid`, bare
  `/uidesigner` and `/uidesigner help` still list the same commands, and
  `/chest-edit` still offers no suggestions for the freeform name (correct for
  an arbitrary string). The rule is learned at the first failing `save`, whose
  message now carries both the positions and the reason; I still do not consider
  a pre-emptive mention in the terse help text worth flagging as work.
- **Round-1 peer findings are resolved or out of my scope, and I do not
  re-report them.** tester's validation-before-output-path gap is now covered by
  the two "never resolves the output path" tests; architecture's doc-attribution
  fix landed in `docs/data-format.md`/`docs/design.md`; readability's
  `chestEdit*` key labels and the `JsonExporterTest` name are renamed. The
  remaining peer observations (doc wording, test cardinality, `ChestNamer.clear`
  dead code) are correctness/architecture/readability matters, not player-facing
  loop, feedback or discoverability issues.
