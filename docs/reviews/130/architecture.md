# Architecture review — 130 (Consume rooster-commands root executor and suggestion dedupe)

## Round 1
### Verdict
Ship. The change consumes the library's root `onExecute` and literal-suggestion
dedupe through the existing DSL seam, removes the last direct CommandAPI
executor from this repo, keeps the command layer thin, and updates the one doc
that described the old wiring. No architecture findings.

### Findings
No findings.

### Non-findings
- **No CommandAPI leakage into the command layer.** `rg 'dev\.jorel' src/`
  shows CommandAPI imports only in `UiDesignerPlugin.kt:7-8` (the
  `CommandAPI.onEnable`/paper-config bootstrap, which predates this ticket) and
  in the command *tests* (`CommandAPITestUtilities`/`MockCommandAPIPlugin`). The
  two touched sources now import only `dev.rooster.commands.*`; the removed
  `import dev.jorel.commandapi.executors.CommandExecutor` was the last raw
  CommandAPI symbol in `commands/`. The root executor is supplied through
  `CommandScope.onExecute` (`rooster-commands/.../RoosterCommands.kt:23-25`) and
  compiled by the library's `Factory`/`Compiler`, so the plugin still treats the
  DSL as the boundary — exactly the seam `docs/architecture.md` describes.
- **`CommandExecutor` is truly gone.** No occurrence remains anywhere under
  `src/` (only historical `docs/tasks/` and older review files mention it, which
  are records, not maintained docs). `ChestEditCommand.kt` and
  `UiDesignerCommand.kt` now chain `.register(plugin)` directly onto the
  `command(...)` result.
- **The DSL-vs-pure-logic seam is preserved.** `register()` in both files stays
  a wiring-only block: it declares nodes, executors and the `usageMessage()` /
  `helpMessage()` callbacks. The tested logic remains outside it
  (`ChestEditCommand.apply`, `UiDesignerCommand.save`/`reload`) and is still
  reachable without a dispatcher. The root callbacks resolve `playerOrNull` /
  `sender` via the library `Context`, not CommandAPI types.
- **`docs/architecture.md` is accurate after the change.** The rewritten
  paragraph (lines 138-149) correctly says both roots use a root
  `onExecute {...}` on the `command(...)` scope, that no direct
  `CommandTree.executes` remains, and that the optional greedy `name` suggests
  `clear` while the compiler's `excludingLiterals` dedupe keeps it offered once.
  That matches `Compiler.kt:163-171` (greedy node) and `Compiler.kt:207`
  (`siblingLiterals`) in the sibling library. The old claim that `clear` comes
  *only* from a literal node was replaced, so there is no stale suggestion
  description.
- **`docs/design.md` and `docs/data-format.md` were not invalidated.**
  `design.md` never described `.executes(...)` or the tab-completion suggestion;
  its only `clear` references (lines 99-104) are about the reserved name,
  casing/trim behaviour and bare-command usage, all still true. `data-format.md`
  is unrelated to command wiring. `docs/manual-test.md` MT-007 already names
  "suggestions offer the subcommands and `clear`" and needs no edit.
- **The `.suggestStrings { listOf("clear") }` call is redundant with the sibling
  literal, and I concur with the tester and correctness non-findings that this is
  deliberate.** `Compiler.compileChildren` filters the greedy provider through
  `excludingLiterals(["clear"])` (`Compiler.kt:207,167`), so the visible `clear`
  is contributed by the literal node. The call does not earn its keep on runtime
  behaviour alone, but the ticket explicitly asks for it to consume the new
  library feature and the test at `ChestEditCommandTest.kt:197` pins the
  combined behaviour. The drift risk (a future rename of the literal leaving a
  stale string) is bounded because the value is the same literal the sibling
  already excludes; not worth blocking or re-litigating.
- **Nothing over- or under-generalised.** No new files, packages, interfaces, or
  configuration were added. The change is two `onExecute` moves and one
  `suggestStrings` call inside the existing command files; package layout and the
  `commands/` vs `export/` purity boundary are untouched.
- **Next-feature walk.** Adding a new subcommand literal (e.g. a second
  `/chest-edit` verb) or another greedy-suggested value touches only the
  relevant command file plus its test; it needs no library change and no
  architecture-doc change beyond the existing node description. Adding a
  suggestion for a value that is *not* also a literal would simply not be
  filtered — the seam already supports it.
- **Concur with the tester and correctness reports.** Both conclusions
  (exact-cardinality suggestion test; root executor fires only for the bare
  command; console no-op preserved) match the library paths and the diff I
  read; no dissent.

## Round 2
### Verdict
Ship. The post-fix tree has no source change since round 1, and the readability
prose reflow has removed the only defect I could see in `docs/architecture.md`.
The doc is now both accurate and well-formed; `docs/design.md` is still
consistent with the code; the command-layer seam and the absence of
`CommandExecutor` are unchanged. No new findings.

### Findings
No findings.

### Non-findings
- **`docs/architecture.md` is accurate and well-formed after the reflow.** The
  stranded single-word line is gone: `docs/architecture.md:142` now reads
  `CommandAPI suggests the registered subcommand literals automatically; the`,
  and the paragraph flows normally to line 149. The reflow also closes the
  pre-existing `so the` / `literal` split flagged as optional in readability's
  finding (now `so the literal does not` / `need to be...`, lines 145-146).
  Factually the paragraph still matches the library: the root `onExecute` claim
  (`Compiler.kt:51-58`), the "no direct `CommandTree.executes` remains" claim,
  and the greedy-suggestion/exclusion claim (`Compiler.kt:163-171,207`) all hold.
- **`docs/design.md` remains accurate and needed no edit in round 2.** It never
  described `.executes(...)` or the tab-completion suggestion, so this ticket did
  not invalidate it. Its `clear` statements (lines 99-104) are about the reserved
  name, casing/trim and bare-command usage, all still true. The "both
  `/chest-edit` executors are player-only" phrasing predates this ticket and its
  accompanying clause ("uniformly across bare and argument-bearing invocations")
  already covers the root handler, so it is not stale as a result of this change.
  `docs/data-format.md` is untouched by command wiring.
- **The command-layer seam is intact.** Source is byte-identical to the state I
  reviewed in round 1 (`git diff 08b747e -- src/` is empty), so `register()`
  remains a wiring-only block over the DSL while `apply`/`save`/`reload` stay
  tested pure logic, and the root callbacks still resolve `sender`/`playerOrNull`
  through the library `Context` rather than CommandAPI types.
- **`CommandExecutor` is still fully gone.** `rg 'dev\.jorel|CommandExecutor'
  src/main/kotlin/dev/cypdashuhn/uidesigner/commands/` returns nothing, and
  `rg '\.executes\(|CommandExecutor' src/` is empty; the only `executes` strings
  in the maintained docs are the accurate "no direct `CommandTree.executes`
  remains" sentence.
- **Concur with the round-1 and round-2 reports; no dissent.** Readability's
  wrap finding is resolved in the current `docs/architecture.md`; correctness and
  tester round-2 reports correctly conclude nothing changed in source; ux's
  player-facing conclusion is unaffected. I add nothing to any of them.
