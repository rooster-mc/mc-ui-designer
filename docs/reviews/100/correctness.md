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
