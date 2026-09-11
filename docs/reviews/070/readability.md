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
