# Correctness review — 100 (Adopt rooster-commands and drop command-layer accidents)

## Round 1

### Verdict

Ship with one small decision to make. The rooster-commands rewrite preserves
the old dispatch semantics for every player-facing case I traced, the FAWE
class-loading laziness survives the interface removal, permissions are fully
gone, and `apply`/`ChestCapture` contracts are untouched. Both implementor-flagged
deviations are real and I concur with accepting them. One console case falls
outside those two flags and should be decided consciously.

### Findings

1. **Console bare `/chest-edit` now answers with the usage message; previously it
   was a player-only failure.**
   - Location: `src/main/kotlin/dev/cypdashuhn/uidesigner/commands/ChestEditCommand.kt:53`
     (`.executes(CommandExecutor { sender, _ -> sender.sendMessage(Messages.chestEditUsage()) })`).
   - Problem: The two flagged deviations cover console *no-ops* for the
     argument-bearing invocations (`/chest-edit <name>`, `/chest-edit clear`,
     `/uidesigner save`). Bare `/chest-edit` from console is a third case: the
     root executor has no `playerOrNull` guard, so a console sender now receives
     the full usage text, where the old `executesPlayer` registration failed
     hard. This is not covered by deviation 1 as worded, and it is internally
     inconsistent with the chosen no-op convention: console `/chest-edit foo`
     is silent, console `/chest-edit` prints usage.
   - Reproduction: run `chest-edit` (no arguments) as console.
   - Suggested fix: pick one deliberately. Either add the same
     `playerOrNull ?: return@onExecute` guard to the root executor for
     consistency with the no-op convention, or accept usage-to-console as the
     intended behaviour and record it alongside deviation 1 (it is arguably
     better UX; I would also accept that outcome). What I would not accept is
     leaving it as an accident.

### Concurrence with implementor-flagged deviations

- **Console no-op for player-only commands** — concur. Old behaviour was a hard
  executor-type failure from CommandAPI; the `playerOrNull ?: return@onExecute`
  guards in `ChestEditCommand.kt:41,50` and `UiDesignerCommand.kt:71` are a
  reasonable local-tool choice, and the `console save does not export` test
  pins it.
- **`clear` prefix-filter suggestion removal** — concur. Old
  `SafeSuggestions.suggest("clear")` offered `clear` for any input; the new
  literal node only suggests it while the typed prefix matches (`chest-edit `,
  `chest-edit c`), and the greedy branch offers nothing. Dispatch behaviour is
  unchanged (see non-findings); only the suggestion surface narrowed.

### Non-findings

- **FAWE laziness holds without the interface.** `UiDesignerPlugin.kt:14-15`
  imports `worldEditSelection`/`toRegion` (top-level extensions in
  rooster-region-worldedit's `Adapter.kt`); imports resolve nothing at class-load
  time. The body lives in `worldEditSelectionOf` (`UiDesignerPlugin.kt:37-39`),
  invoked only when the `selectionProvider` lambda runs, i.e. when a player
  executes `save`. Constant-pool resolution of `AdapterKt` and the FAWE-typed
  intermediates (`WERegion` receiver) is deferred to first execution; MockBukkit's
  `UiDesignerPluginTest` loads the plugin class and enables it without FAWE on the
  test classpath, which exercises exactly this path. No import in
  `UiDesignerPlugin.kt` names a FAWE type.
- **Compiler merge machinery is not engaged.** `Compiler.compileChildren`
  groups siblings by `ArgumentType::class` and only merges non-literal groups
  (`Compiler.kt:201-213`). `chest-edit`'s root children are `GreedyStringArgumentType`
  + `LiteralArgumentType` (different classes); `uidesigner`'s are three literals
  (exempt). So no `_route_` synthetic key, no merged executor, no merged
  suggestion filtering applies to either command.
- **Literal-vs-greedy precedence at the `chest-edit` root is correct.** Both
  children match the token `clear`; Brigadier keeps the literal (registered
  last) for the exact match, and any-casing/padded input (`Clear`, `  clear  `)
  falls to the greedy branch where `apply` trims and clears case-insensitively
  (`ChestEditCommand.kt:72`) — identical outcomes to the old reserved-sentinel
  approach, including `clear x` naming literally. Dispatch tests cover the
  exact and padded cases.
- **Optional-argument synthesis is irrelevant here.** The greedy node carries
  its own executor, so `compileNode`'s optional-child executor synthesis
  (`Compiler.kt:186-192`) never fires; bare `/chest-edit` reaches the root
  executor, matching the old `args["name"] == null` usage path. A
  whitespace-only remainder parses the same way under both registrations, so
  the old and new behaviour for `/chest-edit ` (trailing space) coincide.
- **Executor synchronization preserves old sender semantics.** `save` and both
  `chest-edit` branches were `executesPlayer` and are now `playerOrNull`-guarded
  (deviation 1); `reload`, `help`, and both roots were `CommandExecutor` and
  remain unguarded, so console `reload`/`help`/`/uidesigner` root still work as
  before. `argOrNull("name") ?: ""` in the greedy executor is unreachable-dead
  in practice (the executor only fires when the greedy node matched) and
  harmless: blank input routes to the clear path exactly as the old trimmed
  blank handling did.
- **`apply` and `ChestCapture.capture` contracts unchanged.** `apply`
  (`ChestEditCommand.kt:68-79`) is byte-for-byte the old logic (trim, blank/
  case-insensitive clear, `NothingToClear` on unnamed chest). `ChestCapture.capture`
  now takes `(Player) -> Region?` instead of `SelectionSource`; null selection
  still yields `null` → `NoSelection`, and the test fakes were updated to the
  lambda form with identical semantics.
- **Permissions fully removed.** No `hasPermission`, `PERMISSION` constants, or
  `Messages.noPermission` references remain under `src/`; the `bukkit.yml`
  permission registrations are gone from `build.gradle.kts`. The only remaining
  `Permission` hits are the unrelated `PosixFilePermission` file-mode code in
  `JsonExporter`. (Help text dropping the `(op)` markers is the intended
  consequence.)
- **Registration surface is intact.** `command()` (`Factory.kt`) builds a
  `CommandTree` without an executor; the plugin's `.executes(...)` sets the root
  executor and `.withAliases("uid")`/`.register(plugin)` exist on
  `ExecutableCommand`/`AbstractCommandTree` in commandapi 11.2.0. The standalone
  `command()` factory needs no rooster service init, and the plugin's own
  `CommandAPI.onEnable()`/`onDisable()` calls are unchanged. Unmatched input
  (`/uidesigner foo`) hits the same Brigadier no-match path as the old
  `CommandAPICommand` structure, so error behaviour is unchanged there too.

## Round 2

### Verdict

Ship. The round-1 finding is fixed exactly as suggested (console no-op
convention chosen for bare `/chest-edit`, pinned by a test), and the
round-2 refactors (executor hoisting, `argOrNull` early return, const
templating, settings check) are all behaviour-preserving. No new findings.

### Findings

None.

### Non-findings

- **Round-1 finding 1 resolved correctly.** The root executor
  (`ChestEditCommand.kt:37-41`) now guards `sender as? Player ?: return@CommandExecutor`.
  `Context.playerOrNull` is literally `sender as? Player` (`rooster-commands`
  `Context.kt:9`), so the direct cast in the raw `CommandExecutor` (whose
  `sender` is the same `CommandSender` object the `Context` wraps) behaves
  identically to the extension — no divergence between the two guard styles.
  Players still get the usage message; console is silent, and the new
  `console bare chest-edit is a silent no-op` test pins it via
  `nextComponentMessage() == null`.
- **`argOrNull<String>("name") ?: return@onExecute` cannot regress.** The
  greedy executor only fires via `executePathCore` after the greedy node
  matched, in which case CommandAPI has stored a (non-null) value under
  `"name"` — including the empty-string parse, which still reaches `apply("")`
  and the clear path exactly as the old `?: ""` fallback did. The early return
  is reachable only in a hypothetical state where the old code would have run
  the clear path on a missing argument — silently no-opping there is strictly
  safer (it could have cleared a chest on a dispatch quirk). No sending path
  changes behaviour.
- **`helpExecutor` hoisting is semantics-neutral.** The `help` literal keeps
  its own `onExecute { sender.sendMessage(Messages.help()) }` with the same
  body; the root now shares the hoisted `CommandExecutor` with the identical
  body and no sender guard, so console reaches `Messages.help()` through both
  the `/uidesigner help` literal and the bare `/uidesigner` root, matching the
  pre-ticket behaviour. Same shape for `usageExecutor`: hoisted, guarded, and
  attached only to the root.
- **`CONFIG_UNREADABLE_WARNING` templating is a compile-time constant.**
  `"$CONFIG_FILE_NAME could not be read; leaving it unchanged"` with
  `CONFIG_FILE_NAME = "config.yml"` (both `const val` in the same companion)
  folds to the identical string at compile time; no runtime or message change.
- **`rooster-core` settings check is correct and harmless.** Verified against
  the sibling repo: `rooster-commands/settings.gradle.kts` does
  `includeBuild("../rooster-core")` and both its modules depend on
  `dev.rooster.core:rooster-core`, so the transitive checkout requirement is
  real and the failure message (path + expectation) is accurate. The `check`
  runs once at root-build settings-configuration time, same as the existing
  `rooster-commands` check above it, and does not affect test execution beyond
  requiring the checkout to exist — which it does in this environment.
- **Docs changes (83d0ad2) touch no logic**; nothing in my scope to re-check
  beyond confirming no source behaviour is referenced incorrectly by them.
