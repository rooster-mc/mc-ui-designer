# Correctness review — 130 (Consume rooster-commands root executor and suggestion dedupe)

## Round 1
### Verdict
Ship. The root executors, console no-op, receiver resolution and suggestion
behaviour are all equivalent to the pre-ticket wiring; I found no logic, state,
data or integration defect.

### Findings
No findings.

### Non-findings
- **Root executor wiring fires for the bare command and only the bare command.**
  `command(...)` (`rooster-commands/command-api/.../Factory.kt:10-14`) passes
  `scope.executor` to `Compiler.compile`, which installs it via
  `tree.executes` (`Compiler.kt:51-58`); `CommandScope.onExecute`
  (`RoosterCommands.kt:23-25`) only assigns that field. Literal/greedy children
  get their own executors (`Compiler.kt:173-176`), and Brigadier dispatches to
  the deepest matching node, so `/chest-edit clear`, `/chest-edit <name>` and
  `/uidesigner save|reload|help` cannot also trigger the root handler. This is
  the same node placement as the removed `.executes(usageExecutor)` /
  `.executes(helpExecutor)`.
- **Console no-op preserved exactly.** The old executor did
  `sender as? Player ?: return@CommandExecutor`; the new root handler does
  `playerOrNull ?: return@onExecute`, and `playerOrNull` is literally
  `sender as? Player` (`Context.kt:9`). Console bare `/chest-edit` stays silent
  (`ChestEditCommandTest.kt:175`); bare `/uidesigner` still uses
  `sender.sendMessage`, so it works for console as before.
- **`onExecute` / `Context` resolution is sound.** In the root blocks the
  member `CommandScope.onExecute` wins over the `CanOnExecute` extension; on
  argument builders the extension `onExecute` is used. `return@onExecute` labels
  the lambda in both cases, and `sender` (`Context.sender`), `playerOrNull`
  (`Context.kt:9`) and `argOrNull<String>` (`Context.kt:18`) all resolve against
  the new `Context`. No `CommandExecutor` remains anywhere in `src/`.
- **Suggestions are not duplicated and partial input still matches.**
  `compileChildren` computes `siblingLiterals = ["clear"]`
  (`Compiler.kt:207`) and `compileNode` applies `excludingLiterals`
  case-insensitively (`Argument.kt:33-34`) to the greedy node's provider, so the
  provider can never emit a second `clear`. The one visible `clear` comes from
  the sibling literal node (same mechanism as the `uidesigner` subcommand
  suggestions). `chest-edit cl` and `chest-edit ` resolve to exactly
  `["clear"]`. Note the dedupe filters the greedy contribution to empty rather
  than merging; the user-visible acceptance criterion ("suggests `clear`, once")
  is still met, and the filtering is the library feature this ticket consumes.
- **Clear casing/whitespace is untouched.** `apply` (`ChestEditCommand.kt:67-78`)
  is byte-identical: `trim()`, `isEmpty()`, `equals("clear", ignoreCase = true)`,
  and the literal branch still passes the exact `"clear"` sentinel. The greedy
  branch still forwards the raw argument to `apply`, preserving
  `"  clear  "`/`"CLEAR"` routing.
- **`uid` alias intact.** `withAliases("uid")` is still applied to the same
  `CommandTree` returned by `command(...)`; moving the root executor inside
  `Compiler.compile` does not affect alias registration.
- **I concur with the tester's report.** Its single non-trivial claim — that the
  new `chest-edit cl` assertion is an exact-cardinality integration test that
  would fail on a duplicate `clear` — matches the diff's intent and the compiler
  path I traced; no dissent.
