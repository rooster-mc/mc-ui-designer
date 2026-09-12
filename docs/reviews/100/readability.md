# Readability review — 100 (Adopt rooster-commands and drop command-layer accidents)

## Round 1

### Verdict

Ship with fixes. The rooster-commands rewrite is a clear readability win over
the old CommandAPI style — the command trees read as trees instead of builder
chains, the outcome→message `when` blocks are extracted, and the permission
scaffolding is gone. The remaining friction is small and local: two executor
styles mixed inside one `register()`, a misleading dead fallback in the greedy
executor, and one duplicated literal in the plugin constants.

### Findings

#### 1. Two executor styles and a duplicated help body in `UiDesignerCommand.register()`
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:69-77`
- Problem: The three subcommands use the rooster DSL (`literal("save").onExecute { ... }`),
  but the root executor drops back to a raw CommandAPI lambda
  (`CommandExecutor { sender, _ -> sender.sendMessage(Messages.help()) }`), and
  that help body is written twice in six lines — once for `literal("help")`,
  once for the root. A reader has to wonder whether the two styles differ
  semantically (they don't here) and whether the two help messages can drift.
- Suggested fix: hoist the help executor to a local
  `val helpExecutor = CommandExecutor { sender, _ -> sender.sendMessage(Messages.help()) }`
  (as the pre-ticket code did) and use it for both the `help` literal and the
  root, so the file shows one executor style per concern and one help body.
  The raw `CommandExecutor` at the root stays — the standalone `command()`
  factory returns a `CommandTree` with no root executor, so `.executes(...)` is
  the only way to set it — but it should be the *only* raw lambda in the file.

#### 2. Same mix in `ChestEditCommand.register()`, with the usage body inline
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:50-51`
- Problem: Same pattern as finding 1: DSL executors for the two argument nodes,
  then a raw `CommandExecutor { sender, _ -> sender.sendMessage(Messages.chestEditUsage()) }`
  chained onto the closing brace. The long inline lambda on the `.executes(...)`
  line is also the densest line in the file; it reads as an afterthought glued
  to the tree rather than a peer of the other executors.
- Suggested fix: extract it the same way, e.g. a local
  `val usageExecutor = CommandExecutor { sender, _ -> sender.sendMessage(Messages.chestEditUsage()) }`
  declared before `command(...)`, so the tree block and the root fallback are
  visually separate and the register body scans top-to-bottom.

#### 3. Dead fallback `argOrNull("name") ?: ""` misleads about when the executor runs
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:38-43`
- Problem: The executor is attached to the `greedyString("name")` node itself,
  so it can only fire when `name` parsed; the `?: ""` fallback describes a case
  that cannot happen and makes a reader hunt for the path where the key is
  absent (there is none — the root usage executor is a different node). It also
  hides that the parsed value is the actual subject: the code re-fetches it by
  string key instead of making that obvious.
- Suggested fix: drop the `?: ""` and read the value directly
  (`argOrNull<String>("name")` still needed for the cast, but no fabricated
  default), or add a named local `val rawName = argOrNull("name")` before the
  `apply` call so the executor reads as "resolve target, apply rawName, send
  outcome" without a phantom empty-string branch.

#### 4. `CONFIG_UNREADABLE_WARNING` re-hardcodes the config file name
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/UiDesignerPlugin.kt:82-83`
- Problem: The ticket's point was to name `"config.yml"` once
  (`CONFIG_FILE_NAME`), but the warning constant spells the same literal again.
  If the file name ever changes, the log message silently drifts from the file
  actually read.
- Suggested fix: build the warning from the constant —
  `const val CONFIG_UNREADABLE_WARNING = "$CONFIG_FILE_NAME could not be read; leaving it unchanged"`
  (const-string templating of another const compiles fine) — or accept the
  duplication consciously; one line either way.

#### 5. `sender` and `playerOrNull` used side-by-side for the same sender
- Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:39-44`,
  `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/UiDesignerCommand.kt:70-72`
- Problem: Inside the guarded executors, the player is bound via the
  `playerOrNull` extension but the message is sent via the receiver property
  `sender`. Both name the same object; a reader new to the DSL has to check
  `Context.kt` in rooster-commands to confirm `sender` isn't something else
  (e.g. the plugin's console sender).
- Suggested fix: send through the bound name — `player.sendMessage(...)` after
  the `?: return@onExecute` guard — so each executor uses one name for the
  sender throughout.

### Non-findings

- **The rewrite is more followable than the old CommandAPI style.** The old
  `ChestEditCommand.register()` was a ~55-line builder chain with the whole
  outcome dispatch inlined in the executor; the new version is a flat tree
  (`greedyString` + `literal`) with dispatch extracted to `outcomeMessage()`.
  `UiDesignerCommand` likewise went from nested `withSubcommand(CommandAPICommand(...))`
  blocks to three one-line literals. Judging from `Factory.kt` and
  `types/PrimitiveTypes.kt`, `command(label) { ... }` → `ChildrenScope` with
  `greedyString`/`literal` factory functions is a conventional receiver-DSL and
  the call sites here use it idiomatically.
- **Comments are hygiene-clean.** The three comments in the touched sources are
  all non-obvious-*why* comments: the FAWE class-loading containment note on
  `worldEditSelectionOf` (`UiDesignerPlugin.kt:37-39`), the "clear as a real
  literal node" note (`ChestEditCommand.kt:45-47`, explaining why the old
  sentinel hack is gone), and the trim/blank rationale on `apply()`. The long
  TODO/comment block the ticket promised to delete is gone, as are all
  `// TODO:` markers in touched files. No new comment violates the rule.
- **Dead code and imports are gone.** `SelectionSource.kt`,
  `FaweSelectionSource.kt`, both `PERMISSION` companion objects,
  `Messages.noPermission`, the `bukkit { permissions { ... } }` block, and the
  `net.minecrell.pluginyml...Permission` import are all removed; no unused
  imports remain in the touched files (the removed `Player`/`Messages`/`SelectionSource`
  imports in the tests match the removed code).
- **Constant naming in `UiDesignerPlugin` is otherwise good.** `CONFIG_FILE_NAME`,
  `LOG_ENABLED`/`LOG_DISABLED`, and `OUTPUT_PATH_INVALID_WARNING` are
  self-explanatory, scoped to a private companion object, and
  `OUTPUT_PATH_INVALID_WARNING` correctly reuses `UiDesignerConfig.OUTPUT_FILE_KEY`
  instead of re-hardcoding it (finding 4 is only about the one constant that
  doesn't follow this pattern).
- **`ChestCapture.capture(selectionOf, player)` reads well.** The parameter name
  `selectionOf` matches the lambda's role and the call sites
  (`{ null }`, `{ region }` in tests) are self-documenting; the test helper
  rename `capture(FakeSelectionSource)` → `captureSelection(region)` is an
  improvement.
- **Formatting conforms.** No line in the touched files exceeds the
  `.editorconfig` `max_line_length = 100`; the chained `.executes(...).register(plugin)`
  continuations and the `when` arms follow the existing file style; trailing
  commas are consistent with the disabled ktlint rules.
- **Concur with correctness Round 1 finding 1** (console bare `/chest-edit`
  printing usage): within readability the fix direction chosen there doesn't
  affect this report; whichever convention is picked, findings 1–2 above still
  apply to how the executors are written.

## Round 2

### Verdict

Ship. All five round-1 findings are fixed as suggested, the fixes read better
than the originals, and the new settings guard carries a legitimate
non-obvious-*why* comment. No new findings.

### Findings

None.

### Non-findings

- **Round-1 finding 1 fixed.** `UiDesignerCommand.kt:69-70` hoists
  `val helpExecutor` and the root now attaches it via `.executes(helpExecutor)`;
  the raw `CommandExecutor` lambda is gone from the chain. The `help` literal
  keeps its own one-line `onExecute { sender.sendMessage(Messages.help()) }`
  body, so the help text still appears twice in the function — but the rooster
  `onExecute` callback is a `Context.() -> Unit`, which a `CommandExecutor`
  cannot be reused as, so sharing the hoisted value across both would need a
  wrapper indirection worse than the six-word duplication. Accepting it.
- **Round-1 finding 2 fixed.** `ChestEditCommand.kt:37-42` hoists
  `usageExecutor` with the player guard inside, and the tree block now ends in
  `.executes(usageExecutor)` — the dense inline lambda on the chain is gone and
  the register body scans top-to-bottom as intended.
- **Round-1 finding 3 fixed.** The greedy executor
  (`ChestEditCommand.kt:45-48`) now binds `val rawName = argOrNull<String>("name")
  ?: return@onExecute` and passes the named local to `apply`; the phantom
  `?: ""` empty-string branch is gone and the executor reads as
  guard → resolve → apply → send.
- **Round-1 finding 4 fixed.** `CONFIG_UNREADABLE_WARNING` is now
  `"$CONFIG_FILE_NAME could not be read; leaving it unchanged"`
  (`UiDesignerPlugin.kt:82-83`); the literal is named once.
- **Round-1 finding 5 fixed.** All guarded executors send through the bound
  `player` (`ChestEditCommand.kt:48,54`, `UiDesignerCommand.kt:73`);
  `sender` remains only where there is no player guard (`reload`, `help`,
  `usageExecutor`'s pre-guard lines), which is now the consistent rule rather
  than a mix.
- **Two guard idioms coexist by necessity, not drift.** The hoisted raw
  executors use `sender as? Player ?: return@CommandExecutor` while the DSL
  executors use `playerOrNull ?: return@onExecute`. This is forced by the API
  boundary — `playerOrNull` is a `Context` extension and cannot be called inside
  a CommandAPI `CommandExecutor` — and correctness round 2 confirmed the two
  forms are semantically identical. Nothing to change.
- **New settings comment is rule-compliant.** The `rooster-core` guard comment
  (`settings.gradle.kts:26-28`) explains the non-obvious *why* (the requirement
  is transitive via rooster-commands' own composite include; failing here gives
  a clear message instead of a deep resolution error), matches the placement and
  message format of the two existing guards, and introduces no naming drift
  (`roosterCoreDir` mirrors `roosterCommandsDir`/`roosterRegionDir`).
- **Formatting conforms.** No line in the touched sources exceeds the
  `.editorconfig` `max_line_length = 100`; the hoisted executor locals and the
  `greedyString("name").onExecute { ... }` collapse follow the existing style.
  The `fc4952d` comment-spacing fix in `DoubleChestGrouper.kt` is cosmetic and
  the remaining TODO there is the ticket's declared `rooster-region` deferral,
  not new debt.
