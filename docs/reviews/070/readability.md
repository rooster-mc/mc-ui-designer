# Readability review — 070 (Command UX polish)

## Round 1
### Verdict
Ship with fixes. The change is a net readability win: every player-facing
string now lives in `util/Messages.kt`, the command classes hold named
permission constants, and the `register()`/`apply()` shapes stay small and
direct. All findings are low/medium polish — one cross-file Boolean contract
and three naming/doc nits — and none needs a rewrite.

### Issues

#### 1. `reloadConfiguration(): Boolean` hides its contract behind a bare flag (severity: medium)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:46-61`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:114-125`
- Problem: the return value has no name at the call site beyond the local
  `readable`, and its meaning only emerges by tracing `reload()`: `true` maps to
  `ReloadOutcome.Reloaded`, `false` to `UsingDefaults`. Worse, `readable` is
  `true` when the file is *absent* (`!configFile.isFile`), so a first-run/created
  config reports the green `Reloaded config.yml` line even though defaults were
  written — the flag's name and the outcome names disagree on that path. A
  reader has to hold both files open to know what the Boolean means.
- Suggested fix: make the result self-describing rather than a raw `Boolean` —
  e.g. have `reloadConfiguration()` return a tiny enum
  (`Reloaded` / `UsingDefaults`) that the command can map directly, or rename
  the function/return to `configReadable` and document the "absent counts as
  readable" rule in the architecture bullet. Either keeps the wiring one line
  shorter to follow.

#### 2. `Messages.sentence()` names the wrong thing and splits the punctuation rule (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:42-45,56-57,87`
- Problem: the helper only trims and appends a period (it does not capitalise or
  sentence-case), so the name `sentence` oversells it. It also creates two
  conventions in one file: every static message embeds its own final `.`, while
  the two dynamic-reason messages append one via this helper. A reader editing a
  message must know which convention that method follows.
- Suggested fix: rename to `withTrailingPeriod` (or `terminated`) so the
  behaviour is obvious at both call sites; optionally note nothing else — the
  split is forced by dynamic reasons and does not need a comment.

#### 3. Save/reload failure variants are named inconsistently (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:38-41,53-55`;
  `src/main/kotlin/dev/cypdashuhn/uidesigner/util/Messages.kt:42-45`
- Problem: the two sealed interfaces describe their failure differently —
  `SaveOutcome.WriteFailed(outputFile, reason)` vs `ReloadOutcome.Failed(reason)`
  — even though both wrap `e.message ?: e.javaClass.simpleName`. On top of that,
  `WriteFailed` is also produced when `configProvider()` throws (a config *read*
  failure, `UiDesignerCommand.kt:100-105`), and `Messages.writeFailed` renders
  "Could not write the export…" for that case, so the name is slightly wrong as
  well as asymmetric.
- Suggested fix: align the names (`Failed` on both, or `WriteFailed` /
  `ReloadFailed`) and consider `ExportFailed`/`ConfigFailed` if the config-read
  path matters. Purely a naming pass; the behaviour is correct.

#### 4. Architecture permission sentence silently omits `/chest-edit` (severity: low)
- Location: `docs/architecture.md:117-126`; `docs/design.md:69-74`
- Problem: `design.md` documents all three nodes (`uidesigner.save`,
  `uidesigner.reload`, `uidesigner.chest-edit`), but the architecture bullet
  enumerates only `save`/`reload` and says "`help` and the bare root need no
  permission". A reader coming from architecture alone can conclude
  `/chest-edit` is permission-free, and the `ChestEditCommand.kt` tree entry
  (`architecture.md:27`) likewise never names its node.
- Suggested fix: add `uidesigner.chest-edit` (default op) to the permission
  sentence, or add a half-line to the `ChestEditCommand.kt` tree entry. No new
  prose beyond one clause.

### Non-issues
- **Message centralisation is complete.** `grep` over `src/main/kotlin` finds no
  `sendMessage(...)` literal and no `Component.text` message left in the
  commands; the only remaining `Component.text` is `ChestNamer`'s chest display
  name (`naming/ChestNamer.kt:32`), which is stored data, not a player message.
  The ticket's "no inline literals scattered across commands" note is met.
- **Permission wiring reads clearly.** Each command owns a named constant
  (`UiDesignerCommand.SAVE_PERMISSION` / `RELOAD_PERMISSION`,
  `ChestEditCommand.PERMISSION`), the matching nodes are registered in
  `build.gradle.kts:71-84`, and each executor checks its own node before acting.
  The in-executor check (rather than `.withPermission`) is the documented design
  choice, so the resulting `if (!hasPermission)` branches are expected, not
  accidental.
- **Control flow is direct.** `save` keeps its early `NoSelection`/`NoChests`
  returns; `reload` uses a single `runCatching`; `saveMessage`/`reloadMessage`
  are flat `when`s that delegate to `Messages`. No double negations, needless
  locals, or clever one-liners.
- **No dead code or stray imports.** `Component` is still used in
  `UiDesignerCommand`'s message signatures; `SafeSuggestions` is used by the
  `chest-edit` suggestion; the removed `CommandSyntaxException` imports in the
  tests are gone. No unreachable branches.
- **Comments earn their place.** The `REACH` rationale
  (`ChestEditCommand.kt:13`), the `clear`-sentinel explanation (`:58-61`), and
  the FAWE class-loading note (`UiDesignerPlugin.kt:38-39`) all explain
  non-obvious *why*; nothing narrates the obvious.
- **Formatting.** Every touched Kotlin/`kts` file is within the 100-column
  `.editorconfig` limit (checked), and the chained `.withSubcommand(...)`
  layout matches the pre-existing ktlint style rather than introducing a new
  one.
- **Docs are otherwise coherent.** `design.md:69-74` gains a permission
  decision and `design.md:84-91` gains the bare-`/chest-edit` usage line;
  `architecture.md:117-126` records the "check inside each executor, message from
  `Messages`" split. Only the `/chest-edit` node omission above stands out.
- **Tests stay followable.** `MessagesTest` enumerates every message and pins
  prefix, palette, and trailing period; the command tests keep backticked
  behaviour names and reuse `registeredCommand`/`unregisteredCommand`,
  `opPlayer`, and `plainMessage` helpers rather than re-wiring constructors.

## Round 2
### Verdict
Ship. All four round-1 findings are resolved, and the new `ReloadResult` /
split-`Outcome` shapes are a clear readability improvement. The fix commit
introduces only two low-severity nits — three player-facing fallback hints that
sit outside `Messages`, and a stranded word in a reflowed `architecture.md`
paragraph. Neither needs a rewrite.

### Issues

#### 1. Failure-hint wording lives in the command class, not `Messages` (severity: low)
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:22-24`
- Problem: `WRITE_FAILURE_HINT`, `RELOAD_FAILURE_HINT`, and `INVALID_OUTPUT_HINT`
  are static, player-facing English sentences ("check that the output folder
  exists and is writable", ...). They are the one place a reader finds user
  wording outside `Messages`, and they contradict `architecture.md:123-125`
  ("`Messages` owns every player-facing component (prefix, colour, wording)")
  and the ticket's "prefer centralising messages in `util/Messages.kt`" note.
  A reader editing copy now has two files to check.
- Suggested fix: move the three strings into `Messages` (e.g. as fallback
  defaults inside `writeFailed`/`reloadFailed`/`invalidOutputFile`, or named
  `Messages` constants the command references) so the message surface stays in
  one file. The command then keeps only the `e.message ?: <Messages.fallback>`
  selection.

#### 2. Stranded "owns" line in the reflowed architecture bullet (severity: low)
- Location: `docs/architecture.md:123-125`
- Problem: the permission paragraph was re-wrapped by this commit and now reads
  `... `Messages`\n  owns\n  every player-facing component ...`, leaving the
  single word "owns" alone on a line. It is a small but visible break in an
  otherwise evenly wrapped document, and it sits in the exact paragraph this
  ticket edited.
- Suggested fix: reflow those three lines so "owns" rejoins the sentence
  (e.g. `...; \`Messages\` owns every player-facing component (prefix, colour,`
  `wording).`).

### Non-issues
- **Round-1 issue 1 resolved (Boolean contract).** `reloadConfiguration()`
  returns the self-describing `ReloadResult` enum
  (`UiDesignerPlugin.kt:47-75`), and `UiDesignerCommand.reload()` maps it with
  an exhaustive `when` (`UiDesignerCommand.kt:127-138`). The local `readable`
  remains, but it is now a private branch input rather than the public
  contract, so a caller no longer has to trace `reload()` to learn what the
  return means. The absent-file case still falls through to `Reloaded`, which
  is a defensible "fresh install reloaded defaults" reading and no longer
  creates a name/outcome disagreement at the API.
- **Round-1 issue 2 resolved (helper name).** `Messages.sentence` is now
  `withTrailingPeriod` (`Messages.kt:109`), which says exactly what it does at
  all three call sites; the static-vs-dynamic punctuation split is unchanged but
  now self-evident.
- **Round-1 issue 3 resolved (failure naming).** `SaveOutcome.WriteFailed` now
  means only a write failure; the config-read path is its own
  `SaveOutcome.InvalidOutputFile` (`UiDesignerCommand.kt:48-50,113-118`), and
  `ReloadOutcome.Failed` is the generic reload failure. The residual
  `WriteFailed` vs `Failed` asymmetry is now semantic rather than accidental,
  and `InvalidOutputFile` vs the two `InvalidOutput` names read fine in their
  own layers. No further rename needed.
- **Round-1 issue 4 resolved (architecture permission sentence).**
  `architecture.md:118-120` now names `uidesigner.chest-edit`, the tree entry
  carries it (`:28`), and `noPermission(node)` is documented. The node is
  discoverable from architecture alone.
- **`config/ReloadResult.kt` is a clean 7-line enum.** Three named constants,
  no members, no helpers; it reads at a glance and is listed in the
  architecture tree (`architecture.md:10`).
- **The split `/chest-edit` outcomes are self-describing.** `NoTarget` vs
  `NotAChest` vs `NothingToClear` vs `Cleared` (`ChestEditCommand.kt:20-32`)
  each map to a distinct `Messages` line, and `apply`'s flow is a direct
  sequence of guards (`:70-81`). The extra comment sentence on trimming
  (`:68-69`) explains a non-obvious *why* and earns its place.
- **Exhaustive `when`s, no `else` escape hatches.** `saveMessage`,
  `reloadMessage` (`UiDesignerCommand.kt:146-161`), the `ReloadResult` mapping
  (`:131-135`), and the `Outcome` dispatch (`ChestEditCommand.kt:49-58`) all
  enumerate every variant, so a new variant will fail to compile rather than
  silently fall through.
- **No inline player-facing literals in the command flow.** Apart from the
  named hint constants in issue 1, every `sendMessage` argument is a `Messages`
  call (`rg` over `commands/` confirms), and the only literals left in the
  command classes are the three permission-node constants.
- **Formatting.** Every changed Kotlin file is within the 100-column
  `.editorconfig` limit (checked), and the `when`/branch indentation matches the
  surrounding ktlint style. The awkward doc wrap in issue 2 is prose, not code.
- **`writeDefaultOutputIfBlank()` keeps its own guard.** The duplicate
  `hasUnusableOutputFile()` check (`UiDesignerConfig.kt:13-21`) is redundant on
  the reload path but protects the method's standalone invariant; it is
  defensible, not dead code.
- **Docs track the new shapes.** `architecture.md` gains the `ReloadResult`
  tree entry and the reload-outcome sentence; `design.md:85-88` records the
  trimming and the "nothing to clear" outcome. Both changes are accurate.
